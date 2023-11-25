import { ArtemisServer } from '../../core/util/artemisServer';
import { SimulationRun } from './simulationRun';

export class Simulation {
  constructor(
    public id: number | undefined,
    public name: string,
    public courseId: number,
    public examId: number,
    public numberOfUsers: number,
    public server: ArtemisServer,
    public mode: Mode,
    public runs: SimulationRun[],
    public creationDate: Date,
    public customizeUserRange: boolean,
    public userRange?: string,
  ) {}
}

export enum Mode {
  CREATE_COURSE_AND_EXAM = 'CREATE_COURSE_AND_EXAM',
  EXISTING_COURSE_UNPREPARED_EXAM = 'EXISTING_COURSE_UNPREPARED_EXAM',
  EXISTING_COURSE_PREPARED_EXAM = 'EXISTING_COURSE_PREPARED_EXAM',
  EXISTING_COURSE_CREATE_EXAM = 'EXISTING_COURSE_CREATE_EXAM',
}

export function getTextRepresentation(mode: Mode): string {
  switch (mode) {
    case Mode.CREATE_COURSE_AND_EXAM:
      return 'Create course and exam';
    case Mode.EXISTING_COURSE_UNPREPARED_EXAM:
      return 'Existing course, unprepared exam';
    case Mode.EXISTING_COURSE_PREPARED_EXAM:
      return 'Existing course, prepared exam';
    case Mode.EXISTING_COURSE_CREATE_EXAM:
      return 'Existing course, create exam';
  }
}
