import { SimulationStats } from './simulationStats';

export class StatsByTime {
  constructor(
    public id: number,
    public dateTime: Date,
    public numberOfRequests: number,
    public avgResponseTime: number,
    public simulationStats: SimulationStats,
  ) {}
}
