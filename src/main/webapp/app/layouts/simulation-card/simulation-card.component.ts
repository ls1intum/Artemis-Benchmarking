import { Component, OnInit, inject, input, output } from '@angular/core';
import { getTextRepresentation, instructorCredentialsProvided, Mode, Simulation } from '../../entities/simulation/simulation';
import { SimulationRun, Status } from '../../entities/simulation/simulationRun';
import { SimulationsService } from '../../simulations/simulations.service';
import { NgbModal, NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { ArtemisServer } from '../../core/util/artemisServer';
import { ArtemisAccountDTO } from '../../simulations/artemisAccountDTO';
import { faCalendarDays, faChevronRight, faClock, faEye, faEyeSlash, faTrashCan, faUserTie } from '@fortawesome/free-solid-svg-icons';
import { SimulationScheduleDialogComponent } from '../simulation-schedule-dialog/simulation-schedule-dialog.component';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { DatePipe, NgClass } from '@angular/common';
import { ServerBadgeComponent } from '../server-badge/server-badge.component';
import { StatusIconComponent } from '../status-icon/status-icon.component';
import { FormsModule } from '@angular/forms';

@Component({
  selector: 'jhi-simulation-card',
  templateUrl: './simulation-card.component.html',
  styleUrls: ['./simulation-card.component.scss'],
  imports: [FaIconComponent, NgbTooltip, NgClass, ServerBadgeComponent, StatusIconComponent, DatePipe, FormsModule],
  standalone: true,
})
export class SimulationCardComponent implements OnInit {
  faTrashCan = faTrashCan;
  faChevronRight = faChevronRight;
  faCalendarDays = faCalendarDays;
  faClock = faClock;
  faUserTie = faUserTie;
  faEye = faEye;
  faEyeSlash = faEyeSlash;

  simulation = input.required<Simulation>();
  selectedRun = input<SimulationRun>();
  displayedRuns: SimulationRun[] = [];
  numberOfDisplayedRuns = 3;
  numberOfActiveSchedules = 0;
  credentialsRequired = false;
  instructorAccountAvailable = false;

  adminPassword = '';
  adminUsername = '';
  showAdminPassword = false;

  readonly clickedRunEvent = output<SimulationRun>();
  readonly delete = output();

  protected readonly Mode = Mode;
  protected readonly Status = Status;
  protected readonly getTextRepresentation = getTextRepresentation;
  protected readonly ArtemisServer = ArtemisServer;
  protected readonly instructorCredentialsProvided = instructorCredentialsProvided;

  private simulationService = inject(SimulationsService);
  private modalService = inject(NgbModal);

  ngOnInit(): void {
    this.sortRuns();
    this.updateDisplayRuns();
    this.simulationService.getSimulationSchedules(this.simulation().id!).subscribe(numberOfActiveSchedules => {
      this.numberOfActiveSchedules = numberOfActiveSchedules.length;
    });
    this.subscribeToNewSimulationRun();
    this.updateCredentialsRequired();
    this.instructorAccountAvailable = instructorCredentialsProvided(this.simulation());
  }

  startRun(content: any): void {
    const simulation = this.simulation();
    if (simulation.server === ArtemisServer.PRODUCTION) {
      this.modalService.open(content, { ariaLabelledBy: 'account-modal-title' }).result.then(
        () => {
          let account = undefined;
          const simulationValue = this.simulation();
          if (simulationValue.mode !== Mode.EXISTING_COURSE_PREPARED_EXAM) {
            account = new ArtemisAccountDTO(this.adminUsername, this.adminPassword);
          }
          this.simulationService.runSimulation(simulationValue.id!, account).subscribe(newRun => {
            this.addNewRun(newRun);
          });
          this.adminPassword = '';
          this.adminUsername = '';
          this.showAdminPassword = false;
        },
        () => {
          this.adminPassword = '';
          this.adminUsername = '';
          this.showAdminPassword = false;
        },
      );
    } else {
      this.simulationService.runSimulation(simulation.id!).subscribe(newRun => {
        this.addNewRun(newRun);
      });
    }
  }

  patchInstructorAccount(content: any): void {
    this.modalService.open(content, { ariaLabelledBy: 'instructor-modal-title' }).result.then(
      (res: string) => {
        if (res === 'submit') {
          this.patchSimulationInstructorAccount();
        } else if (res === 'delete') {
          this.deleteSimulationInstructorAccount();
        }
        this.adminPassword = '';
        this.adminUsername = '';
        this.showAdminPassword = false;
      },
      () => {
        this.adminPassword = '';
        this.adminUsername = '';
        this.showAdminPassword = false;
      },
    );
  }

  sortRuns(): void {
    this.simulation().runs.sort((a, b) => new Date(b.startDateTime).getTime() - new Date(a.startDateTime).getTime());
    this.displayedRuns = this.simulation().runs.slice(0, 3);
  }

  updateDisplayRuns(): void {
    this.displayedRuns = this.simulation().runs.slice(0, this.numberOfDisplayedRuns);
  }

  increaseNumberOfDisplayedRuns(): void {
    this.numberOfDisplayedRuns += 3;
    this.updateDisplayRuns();
  }

  decreaseNumberOfDisplayedRuns(): void {
    this.numberOfDisplayedRuns -= 3;
    if (this.numberOfDisplayedRuns < 3) {
      this.numberOfDisplayedRuns = 3;
    }
    this.updateDisplayRuns();
  }

  clickedRun(run: SimulationRun): void {
    this.clickedRunEvent.emit(run);
  }

  deleteSimulation(content: any): void {
    this.modalService.open(content, { ariaLabelledBy: 'delete-modal-title' }).result.then(() => {
      this.delete.emit();
    });
  }

  hasActiveRun(): boolean {
    return this.simulation().runs.some(run => run.status === Status.RUNNING);
  }

  openScheduleDialog(): void {
    const simulation = this.simulation();
    const modalRef = this.modalService.open(SimulationScheduleDialogComponent, { size: 'xl' });
    modalRef.componentInstance.simulation.set(simulation);
    modalRef.hidden.subscribe(() => {
      this.simulationService.getSimulationSchedules(simulation.id!).subscribe(schedules => {
        this.numberOfActiveSchedules = schedules.length;
      });
    });
  }

  subscribeToNewSimulationRun(): void {
    this.simulationService.receiveNewSimulationRun(this.simulation()).subscribe(newRun => {
      this.addNewRun(newRun);
    });
  }

  addNewRun(newRun: SimulationRun): void {
    const simulation = this.simulation();
    if (simulation.runs.some(run => run.id === newRun.id)) {
      return;
    }
    simulation.runs.push(newRun);

    this.simulationService.receiveSimulationStatus(newRun).subscribe(status => {
      newRun.status = status;
    });

    this.sortRuns();
    this.updateDisplayRuns();
  }

  patchSimulationInstructorAccount(): void {
    const account = new ArtemisAccountDTO(this.adminUsername, this.adminPassword);
    this.simulationService.patchSimulationInstructorAccount(this.simulation().id!, account).subscribe(updatedSimulation => {
      const simulation = this.simulation();
      simulation.instructorUsername = updatedSimulation.instructorUsername;
      simulation.instructorPassword = updatedSimulation.instructorPassword;
      this.instructorAccountAvailable = instructorCredentialsProvided(simulation);
      this.updateCredentialsRequired();
    });
  }

  deleteSimulationInstructorAccount(): void {
    this.simulationService.deleteSimulationInstructorAccount(this.simulation().id!).subscribe(updatedSimulation => {
      const simulation = this.simulation();
      simulation.instructorUsername = updatedSimulation.instructorUsername;
      simulation.instructorPassword = updatedSimulation.instructorPassword;
      this.instructorAccountAvailable = instructorCredentialsProvided(simulation);
      this.updateCredentialsRequired();
    });
  }

  updateCredentialsRequired(): void {
    const simulation = this.simulation();
    this.credentialsRequired =
      simulation.server === ArtemisServer.PRODUCTION &&
      simulation.mode !== Mode.EXISTING_COURSE_PREPARED_EXAM &&
      !instructorCredentialsProvided(simulation);
  }
}
