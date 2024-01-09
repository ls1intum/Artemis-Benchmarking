import { SimulationRun } from './simulationRun';

export class LocalCIStatus {
  constructor(
    public id: number,
    public isFinished: boolean,
    public queuedJobs: number,
    public totalJobs: number,
    public timeInMinutes: number,
    public avgJobsPerMinute: number,
    public simulationRun: SimulationRun,
  ) {}
}
