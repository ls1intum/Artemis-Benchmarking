import { Component, Input, OnInit } from '@angular/core';
import { Mode, Simulation } from '../../models/simulation';
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
  displayedRuns: SimulationRun[] = [];
  numberOfDisplayedRuns = 3;

  adminPassword = '';
  adminUsername = '';

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
    if (this.simulation.server == ArtemisServer.PRODUCTION && this.simulation.mode != Mode.EXISTING_COURSE_PREPARED_EXAM) {
      this.modalService.open(content, { ariaLabelledBy: 'account-modal-title' }).result.then(
        () => {
          this.simulationService
            .runSimulation(this.simulation.id!, new ArtemisAccountDTO(this.adminUsername, this.adminPassword))
            .subscribe(newRun => {
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
}
