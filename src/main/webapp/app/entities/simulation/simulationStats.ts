import { SimulationRun } from './simulationRun';
import { RequestType } from './requestType';
import { StatsByTime } from './statsByTime';

export class SimulationStats {
  constructor(
    public id: number,
    public numberOfRequests: number,
    public avgResponseTime: number,
    public simulationRun: SimulationRun,
    public requestType: RequestType,
    public statsByMinute: StatsByTime[],
    public statsBySecond: StatsByTime[],
  ) {}
}

export function getOrder(simulationStats: SimulationStats): number {
  switch (simulationStats.requestType) {
    case RequestType.TOTAL:
      return 0;
    case RequestType.AUTHENTICATION:
      return 1;
    case RequestType.GET_STUDENT_EXAM:
      return 2;
    case RequestType.START_STUDENT_EXAM:
      return 3;
    case RequestType.SUBMIT_EXERCISE:
      return 4;
    case RequestType.SUBMIT_STUDENT_EXAM:
      return 5;
    case RequestType.CLONE:
      return 6;
    case RequestType.PUSH:
      return 7;
    case RequestType.PROGRAMMING_EXERCISE_RESULT:
      return 8;
    case RequestType.REPOSITORY_INFO:
      return 9;
    case RequestType.REPOSITORY_FILES:
      return 10;
    case RequestType.MISC:
      return 11;
  }
}
