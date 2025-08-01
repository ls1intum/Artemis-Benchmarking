import { Component, OnInit, inject, signal } from '@angular/core';
import { Simulation } from '../../entities/simulation/simulation';
import { SimulationsService } from '../simulations.service';
import { SimulationRun, Status } from '../../entities/simulation/simulationRun';
import { getOrder } from '../../entities/simulation/simulationStats';
import { NgbAccordionModule, NgbModal, NgbModule } from '@ng-bootstrap/ng-bootstrap';
import { faSpinner } from '@fortawesome/free-solid-svg-icons';
import { ActivatedRoute, Router } from '@angular/router';
import { CreateSimulationBoxComponent } from '../../layouts/create-simulation-box/create-simulation-box.component';
import { SimulationCardComponent } from '../../layouts/simulation-card/simulation-card.component';
import { StatusIconComponent } from '../../layouts/status-icon/status-icon.component';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { LogBoxComponent } from '../../layouts/log-box/log-box.component';
import { CiStatusCardComponent } from '../../layouts/ci-status-card/ci-status-card.component';
import { ResultBoxComponent } from '../../layouts/result-box/result-box.component';
import { DatePipe } from '@angular/common';
import { SimulationDetailsComponent } from '../../layouts/simulation-details/simulation-details.component';

export function sortSimulations(simulations: Simulation[]): Simulation[] {
  return simulations.sort((a, b) => new Date(b.creationDate).getTime() - new Date(a.creationDate).getTime());
}

@Component({
  selector: 'jhi-simulations-overview',
  templateUrl: './simulations-overview.component.html',
  styleUrls: ['./simulations-overview.component.scss'],
  imports: [
    NgbModule,
    NgbAccordionModule,
    CreateSimulationBoxComponent,
    SimulationCardComponent,
    StatusIconComponent,
    FaIconComponent,
    LogBoxComponent,
    CiStatusCardComponent,
    ResultBoxComponent,
    DatePipe,
    SimulationDetailsComponent,
  ],
})
export default class SimulationsOverviewComponent implements OnInit {
  faSpinner = faSpinner;

  simulations = signal<Simulation[]>([]);
  selectedRun = signal<SimulationRun | undefined>(undefined);
  selectedSimulation = signal<Simulation | undefined>(undefined);
  isCollapsed = true;
  cancellationInProgress = false;

  protected readonly Status = Status;

  private simulationsService = inject(SimulationsService);
  private modalService = inject(NgbModal);
  private route = inject(ActivatedRoute);
  private router = inject(Router);

  ngOnInit(): void {
    const selectedRunString = this.route.snapshot.queryParamMap.get('runId');
    const selectedSimulationString = this.route.snapshot.queryParamMap.get('simulationId');

    let selectedRunId = -1;
    if (selectedRunString) {
      selectedRunId = Number(selectedRunString);
    }

    let selectedSimulationId = -1;
    if (selectedSimulationString) {
      selectedSimulationId = Number(selectedSimulationString);
    }

    this.simulationsService.getSimulations().subscribe(simulations => {
      this.simulations.set(sortSimulations(simulations));

      if (selectedSimulationId > 0) {
        const simulation = simulations.find(s => s.id === selectedSimulationId);
        if (simulation) {
          this.onSelectSimulation(simulation);
        }
      }

      this.simulations().forEach(simulation => {
        simulation.runs.forEach(run => {
          this.subscribeToRunStatus(run);
          if (run.id === selectedRunId) {
            this.selectRun(run);
          }
        });
      });
    });
  }

  createSimulation(simulation: Simulation): void {
    this.simulationsService.createSimulation(simulation).subscribe(newSimulation => {
      this.simulations.update(simulations => [...simulations, newSimulation]);
      this.simulations.set(sortSimulations(this.simulations()));
    });
    this.isCollapsed = true;
  }

  selectRun(run: SimulationRun): void {
    this.selectedSimulation.set(undefined);
    const selectedRun = this.selectedRun();
    if (selectedRun) {
      this.simulationsService.unsubscribeFromSelectedSimulationRun(selectedRun);
    }

    // Update the run
    this.simulationsService.getSimulationRun(run.id).subscribe(updatedRun => {
      run.status = updatedRun.status;
      run.stats = updatedRun.stats.sort((a, b) => getOrder(a) - getOrder(b));
      run.logMessages = updatedRun.logMessages.sort((a, b) => new Date(a.timestamp).getTime() - new Date(b.timestamp).getTime());
      this.selectedRun.set(run);
      this.subscribeToSelectedRun(run);
      this.router.navigate([], { queryParams: { runId: run.id } });
    });
  }

  deleteSelectedRun(content: any): void {
    this.modalService.open(content, { ariaLabelledBy: 'delete-modal-title' }).result.then(() => {
      const selectedRun = this.selectedRun();
      if (!selectedRun) {
        return;
      }
      this.simulationsService.deleteSimulationRun(selectedRun.id).subscribe(() => {
        this.simulationsService.unsubscribeFromSimulationRun(selectedRun);

        this.selectedRun.set(undefined);
        this.simulationsService.getSimulations().subscribe(simulations => {
          this.simulations.set(sortSimulations(simulations));
        });
      });
    });
  }

  cancelSelectedRun(): void {
    this.cancellationInProgress = true;
    this.simulationsService.abortSimulationRun(this.selectedRun()!.id).subscribe(() => {
      this.cancellationInProgress = false;
    });
  }

  deleteSimulation(simulation: Simulation): void {
    this.simulationsService.deleteSimulation(simulation.id!).subscribe(() => {
      simulation.runs.forEach(run => {
        this.simulationsService.unsubscribeFromSimulationRun(run);
      });

      if (this.selectedRun() && simulation.runs.includes(this.selectedRun()!)) {
        this.selectedRun.set(undefined);
      }
      this.simulations.update(simulations => simulations.filter(s => s.id !== simulation.id));
    });
  }

  subscribeToRunStatus(run: SimulationRun): void {
    this.simulationsService.receiveSimulationStatus(run).subscribe(status => {
      run.status = status;
      this.updateSelectedRun(run);
    });
  }

  subscribeToSelectedRun(run: SimulationRun): void {
    this.simulationsService.receiveSimulationLog(run).subscribe(logMessage => {
      run.logMessages.push(logMessage);
      this.updateSelectedRun(run);
    });
    this.simulationsService.receiveSimulationResult(run).subscribe(stats => {
      run.stats = stats.sort((a, b) => getOrder(a) - getOrder(b));
      this.updateSelectedRun(run);
    });
    this.simulationsService.receiveCiStatus(run).subscribe(ciStatus => {
      run.ciStatus = ciStatus;
      this.updateSelectedRun(run);
    });
  }

  updateSelectedRun(run: SimulationRun): void {
    if (this.selectedRun() && this.selectedRun()!.id === run.id) {
      this.selectedRun.set(SimulationRun.of(run));
    }
  }

  onSelectSimulation(simulation: Simulation): void {
    this.selectedSimulation.set(simulation);
    this.selectedRun.set(undefined);
    this.router.navigate([], { queryParams: { simulationId: simulation.id } });
  }
}
