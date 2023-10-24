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
  constructor(private simulationsService: SimulationsService) {}

  ngOnInit() {
    this.simulationsService.websocketSubscription().subscribe((simulationResult: SimulationResult) => {
      this.simulationResult = simulationResult;
      console.log(simulationResult);
    });
  }
  startSimulation() {
    this.simulationsService.startSimulation();
  }

  formatDuration(durationInNanoSeconds: number) {
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
