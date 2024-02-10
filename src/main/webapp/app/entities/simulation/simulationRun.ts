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
}

export enum Status {
  QUEUED = 'QUEUED',
  RUNNING = 'RUNNING',
  FINISHED = 'FINISHED',
  FAILED = 'FAILED',
  CANCELLED = 'CANCELLED',
}
