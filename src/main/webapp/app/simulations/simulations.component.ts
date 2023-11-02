import { Component, OnInit } from '@angular/core';
import { SimulationsService } from './simulations.service';
import { SimulationResult } from './simulationResult';

@Component({
  selector: 'jhi-simulations',
  templateUrl: './simulations.component.html',
  styleUrls: ['./simulations.component.scss'],
})
export class SimulationsComponent implements OnInit {
  simulationResult?: SimulationResult;
  simulationRunning = false;
  constructor(private simulationsService: SimulationsService) {}

  ngOnInit(): void {
    this.simulationsService.websocketSubscription().subscribe((simulationResult: SimulationResult) => {
      this.simulationResult = simulationResult;
      this.simulationRunning = false;
    });
  }
  startSimulation(): void {
    this.simulationsService.startSimulation().subscribe(() => {
      this.simulationRunning = true;
    });
  }

  formatDuration(durationInNanoSeconds: number): string {
    const durationInMicroSeconds = durationInNanoSeconds / 1000;
    if (durationInMicroSeconds > 1000) {
      const durationInMilliSeconds = durationInMicroSeconds / 1000;
      if (durationInMilliSeconds > 1000) {
        const durationInSeconds = durationInMilliSeconds / 1000;
        return durationInSeconds.toFixed(2) + 's';
      }
      return durationInMilliSeconds.toFixed(2) + 'ms';
    }
    return durationInMicroSeconds.toFixed(2) + 'µs';
  }
}
