import { SimulationStats } from './simulationStats';
import { Simulation } from './simulation';
import { LogMessage } from './logMessage';

export class SimulationRun {
  constructor(
    public id: number,
    public startDateTime: Date,
    public stats: SimulationStats[],
    public status: Status,
    public simulation: Simulation,
    public logMessages: LogMessage[],
  ) {}
}

export enum Status {
  QUEUED = 'QUEUED',
  RUNNING = 'RUNNING',
  FINISHED = 'FINISHED',
  FAILED = 'FAILED',
}
