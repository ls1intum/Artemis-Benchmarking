import { Component, EventEmitter, Input, OnInit, Output } from '@angular/core';
import { getTextRepresentation, Mode, Simulation } from '../../models/simulation';
import { SimulationRun, Status } from '../../models/simulationRun';
import { SimulationsService } from '../../simulations/simulations.service';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { ArtemisServer } from '../../models/artemisServer';
import { ArtemisAccountDTO } from '../../models/artemisAccountDTO';

@Component({
  selector: 'jhi-simulation-card',
  templateUrl: './simulation-card.component.html',
  styleUrls: ['./simulation-card.component.scss'],
})
export class SimulationCardComponent implements OnInit {
  @Input()
  simulation!: Simulation;
  @Input()
  selectedRun?: SimulationRun;
  displayedRuns: SimulationRun[] = [];
  numberOfDisplayedRuns = 3;

  adminPassword = '';
  adminUsername = '';

  @Output() clickedRunEvent = new EventEmitter<SimulationRun>();

  constructor(
    private simulationService: SimulationsService,
    private modalService: NgbModal,
  ) {}

  protected readonly Simulation = Simulation;
  protected readonly Mode = Mode;
  protected readonly Status = Status;

  ngOnInit(): void {
    this.sortRuns();
    this.updateDisplayRuns();
  }

  startRun(content: any): void {
    if (this.simulation.server == ArtemisServer.PRODUCTION) {
      this.modalService.open(content, { ariaLabelledBy: 'account-modal-title' }).result.then(
        () => {
          let account = undefined;
          if (this.simulation.mode != Mode.EXISTING_COURSE_PREPARED_EXAM) {
            account = new ArtemisAccountDTO(this.adminUsername, this.adminPassword);
          }
          this.simulationService.runSimulation(this.simulation.id!, account).subscribe(newRun => {
            this.simulation.runs.push(newRun);
            this.sortRuns();
            this.updateDisplayRuns();
          });
        },
        () => {},
      );
    } else {
      this.simulationService.runSimulation(this.simulation.id!).subscribe(newRun => {
        this.simulation.runs.push(newRun);
        this.sortRuns();
        this.updateDisplayRuns();
      });
    }
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
    if (this.numberOfDisplayedRuns < 3) this.numberOfDisplayedRuns = 3;
    this.updateDisplayRuns();
  }

  clickedRun(run: SimulationRun): void {
    this.clickedRunEvent.emit(run);
  }

  protected readonly getTextRepresentation = getTextRepresentation;
}