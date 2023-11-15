import { Component, OnInit } from '@angular/core';
import { Simulation } from '../../models/simulation';
import { SimulationsService } from '../simulations.service';
import { SimulationRun, Status } from '../../models/simulationRun';
import { getOrder } from '../../models/simulationStats';

@Component({
  selector: 'jhi-simulations-overview',
  templateUrl: './simulations-overview.component.html',
  styleUrls: ['./simulations-overview.component.scss'],
})
export class SimulationsOverviewComponent implements OnInit {
  simulations: Simulation[] = [];
  selectedRun?: SimulationRun;
  isCollapsed = true;

  protected readonly Status = Status;

  constructor(private simulationsService: SimulationsService) {}

  ngOnInit(): void {
    this.simulationsService.getSimulations().subscribe(simulations => {
      this.simulations = simulations.sort((a, b) => new Date(b.creationDate).getTime() - new Date(a.creationDate).getTime());

      this.simulations.forEach(simulation => {
        simulation.runs.forEach(run => {
          this.subscribeToRunStatus(run);
        });
      });
    });
  }

  createSimulation(simulation: Simulation): void {
    this.simulationsService.createSimulation(simulation).subscribe(simulation => {
      this.simulations.push(simulation);
      this.simulations.sort((a, b) => new Date(b.creationDate).getTime() - new Date(a.creationDate).getTime());
    });
    this.isCollapsed = true;
  }

  selectRun(run: SimulationRun): void {
    if (this.selectedRun) {
      this.simulationsService.unsubscribeFromSelectedSimulationRun(this.selectedRun);
    }

    // Update the run
    this.simulationsService.getSimulationRun(run.id).subscribe(updatedRun => {
      run.status = updatedRun.status;
      run.stats = updatedRun.stats.sort((a, b) => getOrder(a) - getOrder(b));
      run.logMessages = updatedRun.logMessages.sort((a, b) => new Date(a.timestamp).getTime() - new Date(b.timestamp).getTime());
      this.selectedRun = run;
      this.subscribeToSelectedRun(run);
    });
  }

  deleteSelectedRun(): void {
    if (this.selectedRun) {
      this.simulationsService.deleteSimulationRun(this.selectedRun.id).subscribe(() => {
        if (this.selectedRun) {
          this.simulationsService.unsubscribeFromSimulationRun(this.selectedRun);
        }

        this.selectedRun = undefined;
        this.simulationsService.getSimulations().subscribe(simulations => {
          this.simulations = simulations.sort((a, b) => new Date(b.creationDate).getTime() - new Date(a.creationDate).getTime());
        });
      });
    }
  }

  deleteSimulation(simulation: Simulation): void {
    this.simulationsService.deleteSimulation(simulation.id!).subscribe(() => {
      simulation.runs.forEach(run => {
        this.simulationsService.unsubscribeFromSimulationRun(run);
      });

      if (this.selectedRun && simulation.runs.includes(this.selectedRun)) {
        this.selectedRun = undefined;
      }
      this.simulations = this.simulations.filter(s => s.id !== simulation.id);
    });
  }

  subscribeToRunStatus(run: SimulationRun): void {
    this.simulationsService.receiveSimulationStatus(run).subscribe(status => {
      run.status = status;
    });
  }

  subscribeToSelectedRun(run: SimulationRun): void {
    this.simulationsService.receiveSimulationLog(run).subscribe(logMessage => {
      run.logMessages.push(logMessage);
    });
    this.simulationsService.receiveSimulationResult(run).subscribe(stats => {
      run.stats = stats;
    });
  }
}
