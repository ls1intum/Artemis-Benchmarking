export class SimulationStats {
  constructor(
    public numberOfRequests: number,
    public avgResponseTime: number,
    public requestsByMinute: Map<Date, number>,
    public avgResponseTimeByMinute: Map<Date, number>,
  ) {}
}
