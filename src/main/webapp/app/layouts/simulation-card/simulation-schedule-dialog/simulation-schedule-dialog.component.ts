import { Component, Input, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { NgbActiveModal, NgbCollapseModule, NgbDatepickerModule, NgbTimeStruct, NgbTimepickerModule } from '@ng-bootstrap/ng-bootstrap';
import { Simulation } from '../../../entities/simulation/simulation';
import { SimulationsService } from '../../../simulations/simulations.service';
import { Cycle, DayOfWeek, SimulationSchedule } from '../../../entities/simulation/simulationSchedule';
import { FormsModule } from '@angular/forms';
import { faCalendarDays } from '@fortawesome/free-solid-svg-icons';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';

@Component({
  selector: 'jhi-simulation-schedule-dialog',
  standalone: true,
  imports: [CommonModule, NgbCollapseModule, FormsModule, NgbDatepickerModule, FontAwesomeModule, NgbTimepickerModule],
  templateUrl: './simulation-schedule-dialog.component.html',
  styleUrl: './simulation-schedule-dialog.component.scss',
})
export class SimulationScheduleDialogComponent implements OnInit {
  faCalendarDays = faCalendarDays;

  @Input()
  simulation?: Simulation;

  existingSchedules: SimulationSchedule[] = [];
  activeModal = inject(NgbActiveModal);
  isCollapsed = true;

  cycle = Cycle.DAILY;
  timeOfDay?: NgbTimeStruct = undefined;
  weekday?: DayOfWeek;
  startDate?: Date = undefined;
  endDate?: Date = undefined;

  protected readonly Cycle = Cycle;
  protected readonly DayOfWeek = DayOfWeek;

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
    const schedule = new SimulationSchedule(
      undefined,
      this.startDate!,
      new Date('1970-01-01T' + hourString + ':' + minuteString + ':00.000Z'),
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
      this.startDate !== undefined && this.timeOfDay !== undefined && (this.endDate === undefined || this.endDate >= this.startDate);
    if (this.cycle === Cycle.DAILY) {
      return basicRequirements;
    } else {
      return basicRequirements && this.weekday !== undefined;
    }
  }
}
