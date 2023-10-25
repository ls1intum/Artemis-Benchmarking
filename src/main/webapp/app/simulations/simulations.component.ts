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
  errorMessage = '';

  numberOfUsers = 0;
  courseId = 0;
  examId = 0;
  constructor(private simulationsService: SimulationsService) {}

  ngOnInit() {
    this.simulationsService.websocketSubscriptionSimulationCompleted().subscribe((simulationResult: SimulationResult) => {
      this.simulationResult = simulationResult;
      this.simulationRunning = false;
      this.errorMessage = '';
    });
    this.simulationsService.websocketSubscriptionSimulationError().subscribe((error: string) => {
      this.errorMessage = error;
      this.simulationRunning = false;
      console.log(error);
    });
  }
  startSimulation() {
    const observer = {
      next: () => (this.simulationRunning = true),
      error: () => (this.errorMessage = 'An error occurred. Simulation could not be started.'),
    };
    this.simulationsService.startSimulation(this.numberOfUsers, this.courseId, this.examId).subscribe(observer);
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
