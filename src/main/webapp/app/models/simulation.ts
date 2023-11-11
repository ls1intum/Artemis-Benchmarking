import { ArtemisServer } from './artemisServer';
import { SimulationRun } from './simulationRun';

export class Simulation {
  constructor(
    public id: number,
    public name: string,
    public courseId: number,
    public examId: number,
    public numberOfUsers: number,
    public server: ArtemisServer,
    public mode: Mode,
    public runs: SimulationRun[],
  ) {}
}

export enum Mode {
  CREATE_COURSE_AND_EXAM = 'CREATE_COURSE_AND_EXAM',
  EXISTING_COURSE_UNPREPARED_EXAM = 'EXISTING_COURSE_UNPREPARED_EXAM',
  EXISTING_COURSE_PREPARED_EXAM = 'EXISTING_COURSE_PREPARED_EXAM',
  EXISTING_COURSE_CREATE_EXAM = 'EXISTING_COURSE_CREATE_EXAM',
}
