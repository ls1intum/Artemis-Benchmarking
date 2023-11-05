import { Component, OnInit } from '@angular/core';
import { SimulationsService } from './simulations.service';
import { SimulationResult } from './simulationResult';
import { ArtemisServer } from './artemisServer';
import { LogMessage } from './logMessage';

@Component({
  selector: 'jhi-simulations',
  templateUrl: './simulations.component.html',
  styleUrls: ['./simulations.component.scss'],
})
export class SimulationsComponent implements OnInit {
  simulationResult?: SimulationResult;
  simulationRunning = false;
  logMessages: Array<LogMessage> = [];

  numberOfUsers = 0;
  courseId = 0;
  examId = 0;
  selectedServer = ArtemisServer.TS1;

  protected readonly ArtemisServer = ArtemisServer;

  constructor(private simulationsService: SimulationsService) {}

  ngOnInit(): void {
    this.simulationsService.infoMessages$.subscribe(msg => {
      this.logMessages.push(new LogMessage(msg, false));
    });
    this.simulationsService.errorMessages$.subscribe(msg => {
      this.logMessages.push(new LogMessage(msg, true));
    });
    this.simulationsService.failure$.subscribe(() => {
      this.logMessages.push(new LogMessage('Simulation failed.', true));
      this.simulationRunning = false;
    });
    this.simulationsService.simulationResult$.subscribe(result => {
      this.simulationResult = result;
      this.simulationRunning = false;
    });
  }
  startSimulation(): void {
    this.simulationResult = undefined;
    this.logMessages = [];
    const observer = {
      next: () => (this.simulationRunning = true),
      error: () => this.logMessages.push(new LogMessage('Error starting simulation.', true)),
    };
    this.simulationsService.startSimulation(this.numberOfUsers, this.courseId, this.examId, this.selectedServer).subscribe(observer);
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
