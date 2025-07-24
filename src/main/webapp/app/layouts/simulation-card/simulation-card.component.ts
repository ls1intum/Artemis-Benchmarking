import { Component, OnInit, inject, input, output, signal, computed } from '@angular/core';
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
  selectedSimulation = input<Simulation | undefined>(undefined);

  isSelected = computed(() => {
    return this.selectedSimulation()?.id === this.simulation().id;
  });

  runs = signal<SimulationRun[]>([]);
  numberOfDisplayedRuns = signal(3);
  numberOfActiveSchedules = signal(0);
  credentialsRequired = signal(false);
  instructorAccountAvailable = signal(false);

  displayedRuns = computed(() => this.runs().slice(0, this.numberOfDisplayedRuns()));

  hasActiveRun = computed(() => this.runs().some(run => run.status === Status.RUNNING));

  adminPassword = '';
  adminUsername = '';
  showAdminPassword = false;

  protected readonly clickedRunEvent = output<SimulationRun>();
  protected readonly clickedSimulationEvent = output<Simulation>();
  protected readonly delete = output();
  protected readonly Mode = Mode;
  protected readonly Status = Status;
  protected readonly getTextRepresentation = getTextRepresentation;
  protected readonly ArtemisServer = ArtemisServer;
  protected readonly instructorCredentialsProvided = instructorCredentialsProvided;

  private simulationService = inject(SimulationsService);
  private modalService = inject(NgbModal);

  ngOnInit(): void {
    this.runs.set([...this.simulation().runs]);
    this.sortRuns();

    this.simulationService.getSimulationSchedules(this.simulation().id!).subscribe(schedules => {
      this.numberOfActiveSchedules.set(schedules.length);
    });
    this.subscribeToNewSimulationRun();
    this.updateCredentialsRequired();
    this.instructorAccountAvailable.set(instructorCredentialsProvided(this.simulation()));
  }

  startRun(content: any): void {
    const simulation = this.simulation();
    if (simulation.server === ArtemisServer.PRODUCTION) {
      this.modalService.open(content, { ariaLabelledBy: 'account-modal-title' }).result.then(
        () => {
          let account = undefined;
          if (simulation.mode !== Mode.EXISTING_COURSE_PREPARED_EXAM) {
            account = new ArtemisAccountDTO(this.adminUsername, this.adminPassword);
          }
          this.simulationService.runSimulation(simulation.id!, account).subscribe(newRun => {
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
    this.runs.update(runs => [...runs].sort((a, b) => new Date(b.startDateTime).getTime() - new Date(a.startDateTime).getTime()));
  }

  increaseNumberOfDisplayedRuns(): void {
    this.numberOfDisplayedRuns.update(n => n + 3);
  }

  decreaseNumberOfDisplayedRuns(): void {
    this.numberOfDisplayedRuns.update(n => Math.max(3, n - 3));
  }

  clickedRun(run: SimulationRun): void {
    this.clickedRunEvent.emit(run);
  }

  deleteSimulation(content: any): void {
    this.modalService.open(content, { ariaLabelledBy: 'delete-modal-title' }).result.then(() => {
      this.delete.emit();
    });
  }

  openScheduleDialog(): void {
    const simulation = this.simulation();
    const modalRef = this.modalService.open(SimulationScheduleDialogComponent, { size: 'xl' });
    modalRef.componentInstance.simulation.set(simulation);
    modalRef.hidden.subscribe(() => {
      this.simulationService.getSimulationSchedules(simulation.id!).subscribe(schedules => {
        this.numberOfActiveSchedules.set(schedules.length);
      });
    });
  }

  subscribeToNewSimulationRun(): void {
    this.simulationService.receiveNewSimulationRun(this.simulation()).subscribe(newRun => {
      this.addNewRun(newRun);
    });
  }

  addNewRun(newRun: SimulationRun): void {
    if (this.runs().some(run => run.id === newRun.id)) {
      return;
    }

    this.runs.update(runs => [...runs, newRun]);
    const simulation = this.simulation();
    simulation.runs.push(newRun);

    this.simulationService.receiveSimulationStatus(newRun).subscribe(status => {
      this.runs.update(runs =>
        runs.map(run => {
          if (run.id === newRun.id) {
            run.status = status;
          }
          return run;
        }),
      );
      newRun.status = status;
    });

    this.sortRuns();
  }

  patchSimulationInstructorAccount(): void {
    const account = new ArtemisAccountDTO(this.adminUsername, this.adminPassword);
    this.simulationService.patchSimulationInstructorAccount(this.simulation().id!, account).subscribe(updatedSimulation => {
      const simulation = this.simulation();
      simulation.instructorUsername = updatedSimulation.instructorUsername;
      simulation.instructorPassword = updatedSimulation.instructorPassword;
      this.instructorAccountAvailable.set(instructorCredentialsProvided(simulation));
      this.updateCredentialsRequired();
    });
  }

  deleteSimulationInstructorAccount(): void {
    this.simulationService.deleteSimulationInstructorAccount(this.simulation().id!).subscribe(updatedSimulation => {
      const simulation = this.simulation();
      simulation.instructorUsername = updatedSimulation.instructorUsername;
      simulation.instructorPassword = updatedSimulation.instructorPassword;
      this.instructorAccountAvailable.set(instructorCredentialsProvided(simulation));
      this.updateCredentialsRequired();
    });
  }

  updateCredentialsRequired(): void {
    const simulation = this.simulation();
    this.credentialsRequired.set(
      simulation.server === ArtemisServer.PRODUCTION &&
        simulation.mode !== Mode.EXISTING_COURSE_PREPARED_EXAM &&
        !instructorCredentialsProvided(simulation),
    );
  }

  clickedSimulation(): void {
    this.clickedSimulationEvent.emit(this.simulation());
  }
}
