import { Component, EventEmitter, OnInit, Output } from '@angular/core';
import { Mode, Simulation } from '../../models/simulation';
import { ArtemisServer } from '../../models/artemisServer';
import { ProfileService } from '../profiles/profile.service';

@Component({
  selector: 'jhi-create-simulation-box',
  templateUrl: './create-simulation-box.component.html',
  styleUrls: ['./create-simulation-box.component.scss'],
})
export class CreateSimulationBoxComponent implements OnInit {
  @Output() simulationToCreate = new EventEmitter<Simulation>();

  name: string = '';
  numberOfUsers: number = 0;
  courseId: number = 0;
  examId: number = 0;
  server: ArtemisServer = ArtemisServer.TS1;
  mode: Mode = Mode.CREATE_COURSE_AND_EXAM;

  availableServers = [ArtemisServer.TS1, ArtemisServer.TS3, ArtemisServer.PRODUCTION, ArtemisServer.STAGING];
  availableModes = [
    Mode.CREATE_COURSE_AND_EXAM,
    Mode.EXISTING_COURSE_CREATE_EXAM,
    Mode.EXISTING_COURSE_PREPARED_EXAM,
    Mode.EXISTING_COURSE_UNPREPARED_EXAM,
  ];

  protected readonly Mode = Mode;
  protected readonly ArtemisServer = ArtemisServer;

  constructor(private profileService: ProfileService) {}
  ngOnInit(): void {
    this.profileService.getProfileInfo().subscribe(profileInfo => {
      if (profileInfo.inProduction && this.availableServers.includes(ArtemisServer.LOCAL)) {
        const index = this.availableServers.indexOf(ArtemisServer.LOCAL);
        this.availableServers.splice(index, 1);
      } else if (!profileInfo.inProduction && !this.availableServers.includes(ArtemisServer.LOCAL)) {
        this.availableServers.push(ArtemisServer.LOCAL);
      }
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
      );
      this.simulationToCreate.emit(simulation);
    }
  }

  inputValid(): boolean {
    if (this.mode === Mode.CREATE_COURSE_AND_EXAM) {
      return this.name.length > 0 && this.numberOfUsers > 0;
    }
    if (this.mode === Mode.EXISTING_COURSE_CREATE_EXAM) {
      return this.name.length > 0 && this.numberOfUsers > 0 && this.courseId > 0;
    }
    return this.name.length > 0 && this.numberOfUsers > 0 && this.courseId > 0 && this.examId > 0;
  }
}
