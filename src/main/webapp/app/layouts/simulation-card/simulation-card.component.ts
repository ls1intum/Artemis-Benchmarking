import { Component, EventEmitter, Input, OnInit, Output } from '@angular/core';
import { getTextRepresentation, instructorCredentialsProvided, Mode, Simulation } from '../../entities/simulation/simulation';
import { SimulationRun, Status } from '../../entities/simulation/simulationRun';
import { SimulationsService } from '../../simulations/simulations.service';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { ArtemisServer } from '../../core/util/artemisServer';
import { ArtemisAccountDTO } from '../../simulations/artemisAccountDTO';
import { faCalendarDays, faChevronRight, faClock, faEye, faEyeSlash, faTrashCan, faUserTie } from '@fortawesome/free-solid-svg-icons';
import { SimulationScheduleDialogComponent } from '../simulation-schedule-dialog/simulation-schedule-dialog.component';

@Component({
  selector: 'jhi-simulation-card',
  templateUrl: './simulation-card.component.html',
  styleUrls: ['./simulation-card.component.scss'],
})
export class SimulationCardComponent implements OnInit {
  faTrashCan = faTrashCan;
  faChevronRight = faChevronRight;
  faCalendarDays = faCalendarDays;
  faClock = faClock;
  faUserTie = faUserTie;
  faEye = faEye;
  faEyeSlash = faEyeSlash;

  @Input()
  simulation!: Simulation;
  @Input()
  selectedRun?: SimulationRun;
  displayedRuns: SimulationRun[] = [];
  numberOfDisplayedRuns = 3;
  numberOfActiveSchedules = 0;
  credentialsRequired = false;
  instructorAccountAvailable = false;

  adminPassword = '';
  adminUsername = '';
  showAdminPassword = false;

  @Output() clickedRunEvent = new EventEmitter<SimulationRun>();
  @Output() delete = new EventEmitter<void>();

  protected readonly Mode = Mode;
  protected readonly Status = Status;
  protected readonly getTextRepresentation = getTextRepresentation;
  protected readonly ArtemisServer = ArtemisServer;
  protected readonly instructorCredentialsProvided = instructorCredentialsProvided;

  constructor(
    private simulationService: SimulationsService,
    private modalService: NgbModal,
  ) {}

  ngOnInit(): void {
    this.sortRuns();
    this.updateDisplayRuns();
    this.simulationService.getSimulationSchedules(this.simulation.id!).subscribe(numberOfActiveSchedules => {
      this.numberOfActiveSchedules = numberOfActiveSchedules.length;
    });
    this.subscribeToNewSimulationRun();
    this.credentialsRequired =
      this.simulation.server === ArtemisServer.PRODUCTION &&
      this.simulation.mode !== Mode.EXISTING_COURSE_PREPARED_EXAM &&
      !instructorCredentialsProvided(this.simulation);
    this.instructorAccountAvailable = instructorCredentialsProvided(this.simulation);
  }

  startRun(content: any): void {
    if (this.simulation.server === ArtemisServer.PRODUCTION) {
      this.modalService.open(content, { ariaLabelledBy: 'account-modal-title' }).result.then(
        () => {
          let account = undefined;
          if (this.simulation.mode !== Mode.EXISTING_COURSE_PREPARED_EXAM) {
            account = new ArtemisAccountDTO(this.adminUsername, this.adminPassword);
          }
          this.simulationService.runSimulation(this.simulation.id!, account).subscribe(newRun => {
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
      this.simulationService.runSimulation(this.simulation.id!).subscribe(newRun => {
        this.addNewRun(newRun);
      });
    }
  }

  patchInstructorAccount(content: any): void {
    this.modalService.open(content, { ariaLabelledBy: 'instructor-modal-title' }).result.then(
      (res: string) => {
        if (res == 'submit') {
          this.patchSimulationInstructorAccount();
        } else if (res == 'delete') {
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
    this.simulation.runs.sort((a, b) => new Date(b.startDateTime).getTime() - new Date(a.startDateTime).getTime());
    this.displayedRuns = this.simulation.runs.slice(0, 3);
  }

  updateDisplayRuns(): void {
    this.displayedRuns = this.simulation.runs.slice(0, this.numberOfDisplayedRuns);
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
    this.modalService.open(content, { ariaLabelledBy: 'delete-modal-title' }).result.then(
      () => {
        this.delete.emit();
      },
      () => {},
    );
  }

  hasActiveRun(): boolean {
    return this.simulation.runs.some(run => run.status === Status.RUNNING);
  }

  openScheduleDialog(): void {
    const modalRef = this.modalService.open(SimulationScheduleDialogComponent, { size: 'xl' });
    modalRef.componentInstance.simulation = this.simulation;
    modalRef.hidden.subscribe(() => {
      this.simulationService.getSimulationSchedules(this.simulation.id!).subscribe(numberOfActiveSchedules => {
        this.numberOfActiveSchedules = numberOfActiveSchedules.length;
      });
    });
  }

  subscribeToNewSimulationRun(): void {
    this.simulationService.receiveNewSimulationRun(this.simulation).subscribe(newRun => {
      this.addNewRun(newRun);
    });
  }

  addNewRun(newRun: SimulationRun): void {
    if (this.simulation.runs.some(run => run.id === newRun.id)) {
      return;
    }
    this.simulation.runs.push(newRun);

    this.simulationService.receiveSimulationStatus(newRun).subscribe(status => {
      newRun.status = status;
    });

    this.sortRuns();
    this.updateDisplayRuns();
  }

  patchSimulationInstructorAccount(): void {
    const account = new ArtemisAccountDTO(this.adminUsername, this.adminPassword);
    this.simulationService.patchSimulationInstructorAccount(this.simulation.id!, account).subscribe(updatedSimulation => {
      this.simulation.instructorUsername = updatedSimulation.instructorUsername;
      this.simulation.instructorPassword = updatedSimulation.instructorPassword;
      this.instructorAccountAvailable = instructorCredentialsProvided(this.simulation);
    });
  }

  deleteSimulationInstructorAccount(): void {
    this.simulationService.deleteSimulationInstructorAccount(this.simulation.id!).subscribe(updatedSimulation => {
      this.simulation.instructorUsername = updatedSimulation.instructorUsername;
      this.simulation.instructorPassword = updatedSimulation.instructorPassword;
      this.instructorAccountAvailable = instructorCredentialsProvided(this.simulation);
    });
  }
}
