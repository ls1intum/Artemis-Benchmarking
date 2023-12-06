import { Component, inject, Input, OnInit } from '@angular/core';
import { CommonModule, formatDate } from '@angular/common';
import {
  NgbActiveModal,
  NgbCollapseModule,
  NgbDatepickerModule,
  NgbTimepickerModule,
  NgbTimeStruct,
  NgbTooltipModule,
} from '@ng-bootstrap/ng-bootstrap';
import { Simulation } from '../../../entities/simulation/simulation';
import { SimulationsService } from '../../../simulations/simulations.service';
import { Cycle, DayOfWeek, SimulationSchedule } from '../../../entities/simulation/simulationSchedule';
import { FormControl, FormsModule, ReactiveFormsModule } from '@angular/forms';
import { faCalendarDays, faCircleInfo, faLightbulb } from '@fortawesome/free-solid-svg-icons';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';

@Component({
  selector: 'jhi-simulation-schedule-dialog',
  standalone: true,
  imports: [
    CommonModule,
    NgbCollapseModule,
    FormsModule,
    NgbDatepickerModule,
    FontAwesomeModule,
    NgbTimepickerModule,
    ReactiveFormsModule,
    NgbTooltipModule,
  ],
  templateUrl: './simulation-schedule-dialog.component.html',
  styleUrl: './simulation-schedule-dialog.component.scss',
})
export class SimulationScheduleDialogComponent implements OnInit {
  faCalendarDays = faCalendarDays;
  faLightbulb = faLightbulb;
  faCircleInfo = faCircleInfo;

  @Input()
  simulation?: Simulation;

  timezone: string = Intl.DateTimeFormat().resolvedOptions().timeZone;

  existingSchedules: SimulationSchedule[] = [];
  activeModal = inject(NgbActiveModal);
  isCollapsed = true;
  editScheduleId?: number = undefined;

  cycle = Cycle.DAILY;
  timeOfDay?: NgbTimeStruct = undefined;
  weekday?: DayOfWeek;
  startDate?: Date = undefined;
  endDate?: Date = undefined;

  editCycle = Cycle.DAILY;
  editTimeOfDay?: NgbTimeStruct = undefined;
  editWeekday?: DayOfWeek;
  editStartDate = new FormControl('');
  editEndDate = new FormControl('');
  daysOfWeek = [
    DayOfWeek.MONDAY,
    DayOfWeek.TUESDAY,
    DayOfWeek.WEDNESDAY,
    DayOfWeek.THURSDAY,
    DayOfWeek.FRIDAY,
    DayOfWeek.SATURDAY,
    DayOfWeek.SUNDAY,
  ];

  protected readonly Cycle = Cycle;

  constructor(private simulationService: SimulationsService) {}

  ngOnInit(): void {
    this.simulationService.getSimulationSchedules(this.simulation?.id!).subscribe(schedules => {
      this.existingSchedules = schedules;
    });
  }

  createSchedule(): void {
    console.log(this.timeOfDay);
    const hourString = this.timeOfDay!.hour.toLocaleString('en-US', { minimumIntegerDigits: 2, useGrouping: false });
    const minuteString = this.timeOfDay!.minute.toLocaleString('en-US', { minimumIntegerDigits: 2, useGrouping: false });
    const dateForTimeOfDay = new Date('1970-01-01T' + hourString + ':' + minuteString + ':00.000');
    const schedule = new SimulationSchedule(
      undefined,
      this.startDate!,
      new Date(dateForTimeOfDay.toISOString()),
      this.cycle,
      this.endDate,
      this.weekday,
    );
    this.isCollapsed = true;
    this.simulationService.createSimulationSchedule(this.simulation!.id!, schedule).subscribe(newSchedule => {
      this.existingSchedules.push(newSchedule);
    });
  }

  inputValid(): boolean {
    const basicRequirements =
      this.startDate !== undefined &&
      this.startDate !== null &&
      this.timeOfDay !== undefined &&
      this.timeOfDay !== null &&
      (this.endDate === undefined || this.endDate === null || this.endDate >= this.startDate);
    if (this.cycle === Cycle.DAILY) {
      return basicRequirements;
    } else {
      return basicRequirements && this.weekday !== undefined && this.weekday !== null;
    }
  }

  openEditSchedule(id: number): void {
    this.editScheduleId = id;
    const schedule = this.existingSchedules.find(s => s.id === id);
    if (schedule) {
      console.log(schedule);
      this.editCycle = schedule.cycle;
      const time = new Date(schedule.timeOfDay);
      this.editTimeOfDay = {
        hour: time.getHours(),
        minute: time.getMinutes(),
        second: 0,
      };
      this.editWeekday = schedule.dayOfWeek;

      const startDate = new Date(schedule.startDateTime.valueOf());
      this.editStartDate.setValue(formatDate(startDate, 'yyyy-MM-dd', 'en-US'));
      if (schedule.endDateTime !== undefined && schedule.endDateTime !== null) {
        const endDate = new Date(schedule.endDateTime.valueOf());
        this.editEndDate.setValue(formatDate(endDate, 'yyyy-MM-dd', 'en-US'));
      }
    }
  }

  updateSchedule(): void {
    const schedule = this.existingSchedules.find(s => s.id === this.editScheduleId);
    if (schedule) {
      const hourString = this.editTimeOfDay!.hour.toLocaleString('en-US', { minimumIntegerDigits: 2, useGrouping: false });
      const minuteString = this.editTimeOfDay!.minute.toLocaleString('en-US', { minimumIntegerDigits: 2, useGrouping: false });
      const dateForTimeOfDay = new Date('1970-01-01T' + hourString + ':' + minuteString + ':00.000');
      const dateForStart = new Date(this.editStartDate.value! + 'T00:00:00.000');
      const dateForEnd = this.editEndDate.value === '' ? undefined : new Date(this.editEndDate.value! + 'T00:00:00.000');
      const updatedSchedule = new SimulationSchedule(
        schedule.id,
        new Date(dateForStart.toISOString()),
        new Date(dateForTimeOfDay.toISOString()),
        this.editCycle,
        this.editEndDate.value === '' ? undefined : new Date(dateForEnd!.toISOString()),
        this.editWeekday,
      );
      this.simulationService.updateSimulationSchedule(updatedSchedule).subscribe(newSchedule => {
        this.existingSchedules = this.existingSchedules.map(s => (s.id === newSchedule.id ? newSchedule : s));
        this.editScheduleId = undefined;
      });
    }
  }

  deleteSchedule(id: number): void {
    this.simulationService.deleteSimulationSchedule(id).subscribe(() => {
      this.existingSchedules = this.existingSchedules.filter(s => s.id !== id);
    });
  }

  editValid(): boolean {
    const basicRequirements =
      this.editStartDate.value !== '' &&
      this.editTimeOfDay !== undefined &&
      this.editTimeOfDay !== null &&
      (this.editEndDate.value === '' ||
        new Date(this.editEndDate.value! + 'T00:00:00.000') >= new Date(this.editStartDate.value! + 'T00:00:00.000'));
    if (this.editCycle === Cycle.DAILY) {
      return basicRequirements;
    } else {
      return basicRequirements && this.editWeekday !== undefined && this.editWeekday !== null;
    }
  }
}
