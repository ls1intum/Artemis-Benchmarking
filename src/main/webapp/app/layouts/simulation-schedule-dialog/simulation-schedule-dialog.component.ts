import { Component, inject, OnInit, signal } from '@angular/core';
import { formatDate } from '@angular/common';
import {
  NgbActiveModal,
  NgbCollapseModule,
  NgbDatepickerModule,
  NgbModal,
  NgbTimepickerModule,
  NgbTimeStruct,
  NgbTooltipModule,
} from '@ng-bootstrap/ng-bootstrap';
import { Mode, Simulation } from '../../entities/simulation/simulation';
import { SimulationsService } from '../../simulations/simulations.service';
import { Cycle, DayOfWeek, SimulationSchedule } from '../../entities/simulation/simulationSchedule';
import { FormControl, FormsModule, ReactiveFormsModule } from '@angular/forms';
import { faBell, faCalendarDays, faCircleInfo, faLightbulb, faPen, faTrash } from '@fortawesome/free-solid-svg-icons';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import SharedModule from '../../shared/shared.module';

@Component({
  selector: 'simulation-schedule-dialog',
  imports: [
    SharedModule,
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
  emailRegex =
    '(?:[a-z0-9!#$%&\'*+/=?^_`{|}~-]+(?:\\.[a-z0-9!#$%&\'*+/=?^_`{|}~-]+)*|"(?:[\x01-\x08\x0b\x0c\x0e-\x1f\x21\x23-\x5b\x5d-\x7f]|\\\\[\x01-\x09\x0b\x0c\x0e-\x7f])*")@(?:(?:[a-z0-9](?:[a-z0-9-]*[a-z0-9])?\\.)+[a-z0-9](?:[a-z0-9-]*[a-z0-9])?|\\[(?:(?:(2(5[0-5]|[0-4][0-9])|1[0-9][0-9]|[1-9]?[0-9]))\\.){3}(?:(2(5[0-5]|[0-4][0-9])|1[0-9][0-9]|[1-9]?[0-9])|[a-z0-9-]*[a-z0-9]:(?:[\x01-\x08\x0b\x0c\x0e-\x1f\x21-\x5a\x53-\x7f]|\\\\[\x01-\x09\x0b\x0c\x0e-\x7f])+)\\])';

  faCalendarDays = faCalendarDays;
  faLightbulb = faLightbulb;
  faCircleInfo = faCircleInfo;
  faBell = faBell;
  faTrash = faTrash;
  faPen = faPen;

  simulation = signal<Simulation | undefined>(undefined);

  timezone: string = Intl.DateTimeFormat().resolvedOptions().timeZone;

  existingSchedules = signal<SimulationSchedule[]>([]);
  activeModal = inject(NgbActiveModal);
  isCollapsed = true;
  editScheduleId?: number = undefined;

  cycle = Cycle.DAILY;
  timeOfDay?: NgbTimeStruct = undefined;
  weekday?: DayOfWeek;
  startDate?: Date = undefined;
  endDate?: Date = undefined;

  email = '';

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

  error = false;
  success = false;

  protected readonly Cycle = Cycle;
  protected readonly Mode = Mode;

  private simulationService = inject(SimulationsService);
  private modalService = inject(NgbModal);

  ngOnInit(): void {
    const simulation = this.simulation();
    if (simulation?.id) {
      this.simulationService.getSimulationSchedules(simulation.id).subscribe(schedules => {
        this.existingSchedules.set(schedules);
      });
    }
  }

  createSchedule(): void {
    const hourString = this.timeOfDay!.hour.toLocaleString('en-US', { minimumIntegerDigits: 2, useGrouping: false });
    const minuteString = this.timeOfDay!.minute.toLocaleString('en-US', { minimumIntegerDigits: 2, useGrouping: false });
    const dateForTimeOfDay = new Date('2023-12-01T' + hourString + ':' + minuteString + ':00.000');
    const schedule = new SimulationSchedule(
      undefined,
      this.startDate!,
      new Date(dateForTimeOfDay.toISOString()),
      this.cycle,
      this.endDate,
      this.weekday,
    );
    this.isCollapsed = true;
    const simulation = this.simulation();
    if (simulation?.id) {
      this.simulationService.createSimulationSchedule(simulation.id, schedule).subscribe(newSchedule => {
        if (newSchedule) {
          this.existingSchedules.update(schedules => [...schedules, newSchedule]);
        }
      });
    }
  }

  inputValid(): boolean {
    const basicRequirements = !!this.startDate && !!this.timeOfDay && (!this.endDate || this.endDate >= this.startDate);
    if (this.cycle === Cycle.DAILY) {
      return basicRequirements;
    } else {
      return basicRequirements && !!this.weekday;
    }
  }

  openEditSchedule(scheduleId: number): void {
    this.editScheduleId = scheduleId;
    const schedule = this.existingSchedules().find(aSchedule => aSchedule.id === scheduleId);
    if (schedule) {
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
      if (schedule.endDateTime) {
        const endDate = new Date(schedule.endDateTime.valueOf());
        this.editEndDate.setValue(formatDate(endDate, 'yyyy-MM-dd', 'en-US'));
      }
    }
  }

  updateSchedule(): void {
    const schedule = this.existingSchedules().find(aSchedule => aSchedule.id === this.editScheduleId);
    if (schedule) {
      const hourString = this.editTimeOfDay!.hour.toLocaleString('en-US', { minimumIntegerDigits: 2, useGrouping: false });
      const minuteString = this.editTimeOfDay!.minute.toLocaleString('en-US', { minimumIntegerDigits: 2, useGrouping: false });
      const dateForTimeOfDay = new Date('2023-12-01T' + hourString + ':' + minuteString + ':00.000');
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
        if (newSchedule === undefined) {
          this.existingSchedules.update(schedules => schedules.filter(s => s.id !== schedule.id));
        } else {
          this.existingSchedules.update(schedules => schedules.map(s => (s.id === newSchedule.id ? newSchedule : s)));
        }
        this.editScheduleId = undefined;
      });
    }
  }

  deleteSchedule(scheduleId: number): void {
    this.simulationService.deleteSimulationSchedule(scheduleId).subscribe(() => {
      this.existingSchedules.update(schedules => schedules.filter(s => s.id !== scheduleId));
    });
  }

  editValid(): boolean {
    const basicRequirements =
      this.editStartDate.value !== '' &&
      !!this.editTimeOfDay &&
      (this.editEndDate.value === '' ||
        new Date(this.editEndDate.value! + 'T00:00:00.000') >= new Date(this.editStartDate.value! + 'T00:00:00.000'));
    if (this.editCycle === Cycle.DAILY) {
      return basicRequirements;
    } else {
      return basicRequirements && !!this.editWeekday;
    }
  }

  subscribeToSchedule(id: number, content: any): void {
    this.modalService.open(content, { ariaLabelledBy: 'subscribe-modal-title' }).result.then(
      () => {
        if (this.email.length > 0 && this.email.includes('@')) {
          this.simulationService.subscribeToSimulationSchedule(id, this.email).subscribe({
            next: () => {
              this.email = '';
              this.success = true;
              setTimeout(() => {
                this.success = false;
              }, 3000);
            },
            error: () => {
              this.email = '';
              this.error = true;
              setTimeout(() => {
                this.error = false;
              }, 3000);
            },
          });
        }
      },
      () => {
        this.email = '';
      },
    );
  }
}
