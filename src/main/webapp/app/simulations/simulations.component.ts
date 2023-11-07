import { Component, OnInit } from '@angular/core';
import { SimulationsService } from './simulations.service';
import { SimulationResult } from './simulationResult';
import { ArtemisServer } from './artemisServer';
import { LogMessage } from './logMessage';
import { ArtemisAccountDTO } from './artemisAccountDTO';

@Component({
  selector: 'jhi-simulations',
  templateUrl: './simulations.component.html',
  styleUrls: ['./simulations.component.scss'],
})
export class SimulationsComponent implements OnInit {
  simulationResult?: SimulationResult;
  simulationRunning = false;
  logMessages: Array<LogMessage> = [];

  useExistingExam = false;
  numberOfUsers = 0;
  courseId = 0;
  examId = 0;
  selectedServer = ArtemisServer.TS1;
  adminPassword = '';
  adminUsername = '';

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
    });
    this.simulationsService.simulationCompleted$.subscribe(() => {
      this.simulationRunning = false;
    });
  }
  startSimulation(): void {
    this.simulationResult = undefined;
    this.adminPassword = '';
    this.adminUsername = '';

    if (!this.useExistingExam && !(this.selectedServer === ArtemisServer.PRODUCTION)) {
      this.courseId = 0;
      this.examId = 0;
    }
    this.logMessages = [];
    const observer = {
      next: () => (this.simulationRunning = true),
      error: () => this.logMessages.push(new LogMessage('Error starting simulation.', true)),
    };

    let account;
    if (this.selectedServer === ArtemisServer.PRODUCTION) {
      account = new ArtemisAccountDTO(this.adminUsername, this.adminPassword);
    }
    this.simulationsService
      .startSimulation(this.numberOfUsers, this.courseId, this.examId, this.selectedServer, account)
      .subscribe(observer);
  }

  inputValid(): boolean {
    if (this.selectedServer === ArtemisServer.PRODUCTION) {
      return (
        this.numberOfUsers > 0 &&
        (!this.useExistingExam || (this.courseId > 0 && this.examId > 0)) &&
        this.adminPassword.length > 0 &&
        this.adminUsername.length > 0
      );
    }
    return this.numberOfUsers > 0 && (!this.useExistingExam || (this.courseId > 0 && this.examId > 0));
  }
}
