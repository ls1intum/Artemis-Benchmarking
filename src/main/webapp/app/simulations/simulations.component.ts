import { Component, OnInit } from '@angular/core';
import { SimulationsService } from './simulations.service';
import { SimulationResult } from '../models/simulationResult';
import { ArtemisServer } from '../models/artemisServer';
import { LogMessage } from '../models/logMessage';
import { ArtemisAccountDTO } from '../models/artemisAccountDTO';
import { ProfileService } from '../layouts/profiles/profile.service';

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
  availableServers = [ArtemisServer.TS1, ArtemisServer.TS3, ArtemisServer.PRODUCTION, ArtemisServer.STAGING];

  protected readonly ArtemisServer = ArtemisServer;

  constructor(
    private simulationsService: SimulationsService,
    private profileService: ProfileService,
  ) {}

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
    this.profileService.getProfileInfo().subscribe(profileInfo => {
      if (profileInfo.inProduction && this.availableServers.includes(ArtemisServer.LOCAL)) {
        const index = this.availableServers.indexOf(ArtemisServer.LOCAL);
        this.availableServers.splice(index, 1);
      } else if (!profileInfo.inProduction && !this.availableServers.includes(ArtemisServer.LOCAL)) {
        this.availableServers.push(ArtemisServer.LOCAL);
      }
    });
    this.simulationsService.getSimulations().subscribe(simulations => {
      console.log(simulations);
    });
  }
  startSimulation(): void {
    this.simulationResult = undefined;

    if (!this.useExistingExam) {
      this.courseId = 0;
      this.examId = 0;
    }
    this.logMessages = [];
    const observer = {
      next: () => {
        this.simulationRunning = true;
        this.adminPassword = '';
        this.adminUsername = '';
      },
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
