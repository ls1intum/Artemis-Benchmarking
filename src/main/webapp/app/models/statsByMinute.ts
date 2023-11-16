import { SimulationStats } from './simulationStats';

export class StatsByMinute {
  constructor(
    public id: number,
    public dateTime: Date,
    public numberOfRequests: number,
    public avgResponseTime: number,
    public simulationStats: SimulationStats,
  ) {}
}
