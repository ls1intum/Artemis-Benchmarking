import { SimulationRun } from './simulationRun';

export class CiStatus {
  constructor(
    public id: number,
    public finished: boolean,
    public queuedJobs: number,
    public totalJobs: number,
    public timeInMinutes: number,
    public avgJobsPerMinute: number,
    public simulationRun: SimulationRun,
  ) {}
}
