import { Component, OnInit, inject, output } from '@angular/core';
import { getTextRepresentation, Mode, Simulation } from '../../entities/simulation/simulation';
import { ArtemisServer } from '../../core/util/artemisServer';
import { ProfileService } from '../profiles/profile.service';
import { SimulationsService } from '../../simulations/simulations.service';
import { faEye, faEyeSlash } from '@fortawesome/free-solid-svg-icons';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { ModeExplanationComponent } from '../mode-explanation/mode-explanation.component';

@Component({
  selector: 'create-simulation-box',
  templateUrl: './create-simulation-box.component.html',
  styleUrls: ['./create-simulation-box.component.scss'],
  imports: [FormsModule, RouterLink, FaIconComponent, ModeExplanationComponent],
})
export class CreateSimulationBoxComponent implements OnInit {
  faEye = faEye;
  faEyeSlash = faEyeSlash;

  readonly simulationToCreate = output<Simulation>();

  name = '';
  numberOfUsers = 0;
  courseId = 0;
  examId = 0;
  server: ArtemisServer = ArtemisServer.TS1;
  mode: Mode = Mode.CREATE_COURSE_AND_EXAM;
  customizeUserRange = false;
  userRange = '';
  numberOfCommitsAndPushesFrom = 8;
  numberOfCommitsAndPushesTo = 15;
  instructorUsername = '';
  instructorPassword = '';
  passwordPercentage = 100;
  tokenPercentage = 0;
  sshPercentage = 0;
  onlineIdePercentage = 0;

  availableServers = Object.values(ArtemisServer);
  availableModes = [
    Mode.CREATE_COURSE_AND_EXAM,
    Mode.EXISTING_COURSE_CREATE_EXAM,
    Mode.EXISTING_COURSE_PREPARED_EXAM,
    Mode.EXISTING_COURSE_UNPREPARED_EXAM,
  ];
  serversWithCleanupEnabled: ArtemisServer[] = [];
  showPassword = false;

  protected readonly Mode = Mode;
  protected readonly ArtemisServer = ArtemisServer;
  protected readonly getTextRepresentation = getTextRepresentation;

  private profileService = inject(ProfileService);
  private simulationService = inject(SimulationsService);

  ngOnInit(): void {
    this.profileService.getProfileInfo().subscribe(profileInfo => {
      if (profileInfo.inProduction && this.availableServers.includes(ArtemisServer.LOCAL)) {
        const index = this.availableServers.indexOf(ArtemisServer.LOCAL);
        this.availableServers.splice(index, 1);
      } else if (!profileInfo.inProduction && !this.availableServers.includes(ArtemisServer.LOCAL)) {
        this.availableServers.push(ArtemisServer.LOCAL);
      }
    });
    this.simulationService.getServersWithCleanupEnabled().subscribe(servers => {
      this.serversWithCleanupEnabled = servers;
    });
  }

  createSimulation(): void {
    if (this.inputValid()) {
      const simulation: Simulation = new Simulation(
        undefined,
        this.name,
        this.courseId,
        this.examId,
        this.numberOfUsers,
        this.server,
        this.mode,
        [],
        new Date(),
        this.customizeUserRange,
        this.numberOfCommitsAndPushesFrom,
        this.numberOfCommitsAndPushesTo,
        this.onlineIdePercentage,
        this.passwordPercentage,
        this.tokenPercentage,
        this.sshPercentage,
        this.userRange,
        this.instructorUsername.length > 0 ? this.instructorUsername : undefined,
        this.instructorPassword.length > 0 ? this.instructorPassword : undefined,
      );
      this.simulationToCreate.emit(simulation);
      this.instructorUsername = '';
      this.instructorPassword = '';
      this.showPassword = false;
    }
  }

  inputValid(): boolean {
    const basicRequirements: boolean =
      this.name.length > 0 &&
      ((!this.customizeUserRange && this.numberOfUsers > 0) || (this.customizeUserRange && this.userRange.length > 0)) &&
      this.numberOfCommitsAndPushesFrom > 0 &&
      this.numberOfCommitsAndPushesTo > this.numberOfCommitsAndPushesFrom &&
      this.sshPercentage + this.tokenPercentage + this.passwordPercentage + this.onlineIdePercentage === 100;

    if (this.mode === Mode.CREATE_COURSE_AND_EXAM) {
      return basicRequirements;
    }
    if (this.mode === Mode.EXISTING_COURSE_CREATE_EXAM) {
      return basicRequirements && this.courseId > 0;
    }
    return basicRequirements && this.courseId > 0 && this.examId > 0;
  }
}
