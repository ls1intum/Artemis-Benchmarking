import { Component, EventEmitter, OnInit, Output } from '@angular/core';
import { getTextRepresentation, getTextRepresentationIdeType, IdeType, Mode, Simulation } from '../../entities/simulation/simulation';
import { ArtemisServer } from '../../core/util/artemisServer';
import { ProfileService } from '../profiles/profile.service';
import { SimulationsService } from '../../simulations/simulations.service';
import { faEye, faEyeSlash } from '@fortawesome/free-solid-svg-icons';

@Component({
  selector: 'jhi-create-simulation-box',
  templateUrl: './create-simulation-box.component.html',
  styleUrls: ['./create-simulation-box.component.scss'],
})
export class CreateSimulationBoxComponent implements OnInit {
  faEye = faEye;
  faEyeSlash = faEyeSlash;

  @Output() simulationToCreate = new EventEmitter<Simulation>();

  name: string = '';
  numberOfUsers: number = 0;
  courseId: number = 0;
  examId: number = 0;
  server: ArtemisServer = ArtemisServer.TS1;
  mode: Mode = Mode.CREATE_COURSE_AND_EXAM;
  customizeUserRange: boolean = false;
  userRange: string = '';
  ideType: IdeType = IdeType.OFFLINE;
  numberOfCommitsAndPushesFrom: number = 8;
  numberOfCommitsAndPushesTo: number = 15;
  instructorUsername: string = '';
  instructorPassword: string = '';

  availableServers = Object.values(ArtemisServer);
  availableModes = [
    Mode.CREATE_COURSE_AND_EXAM,
    Mode.EXISTING_COURSE_CREATE_EXAM,
    Mode.EXISTING_COURSE_PREPARED_EXAM,
    Mode.EXISTING_COURSE_UNPREPARED_EXAM,
  ];
  availableIdeTypes = [IdeType.OFFLINE, IdeType.ONLINE];
  serversWithCleanupEnabled: ArtemisServer[] = [];
  showPassword: boolean = false;

  protected readonly Mode = Mode;
  protected readonly ArtemisServer = ArtemisServer;
  protected readonly getTextRepresentation = getTextRepresentation;
  protected readonly getTextRepresentationIdeType = getTextRepresentationIdeType;

  constructor(
    private profileService: ProfileService,
    private simulationService: SimulationsService,
  ) {}

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
        this.ideType,
        this.numberOfCommitsAndPushesFrom,
        this.numberOfCommitsAndPushesTo,
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
      this.numberOfCommitsAndPushesTo > this.numberOfCommitsAndPushesFrom;

    if (this.mode === Mode.CREATE_COURSE_AND_EXAM) {
      return basicRequirements;
    }
    if (this.mode === Mode.EXISTING_COURSE_CREATE_EXAM) {
      return basicRequirements && this.courseId > 0;
    }
    return basicRequirements && this.courseId > 0 && this.examId > 0;
  }
}
