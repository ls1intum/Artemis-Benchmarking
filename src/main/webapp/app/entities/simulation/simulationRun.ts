import { SimulationStats } from './simulationStats';
import { Simulation } from './simulation';
import { LogMessage } from './logMessage';
import { CiStatus } from './ciStatus';

export class SimulationRun {
  constructor(
    public id: number,
    public startDateTime: Date,
    public stats: SimulationStats[],
    public status: Status,
    public simulation: Simulation,
    public logMessages: LogMessage[],
    public endDateTime?: Date,
    public ciStatus?: CiStatus,
  ) {}

  public static of(simulationRun: SimulationRun): SimulationRun {
    return new SimulationRun(
      simulationRun.id,
      simulationRun.startDateTime,
      simulationRun.stats,
      simulationRun.status,
      simulationRun.simulation,
      simulationRun.logMessages,
      simulationRun.endDateTime,
      simulationRun.ciStatus,
    );
  }
}

export enum Status {
  QUEUED = 'QUEUED',
  RUNNING = 'RUNNING',
  FINISHED = 'FINISHED',
  FAILED = 'FAILED',
  CANCELLED = 'CANCELLED',
}
