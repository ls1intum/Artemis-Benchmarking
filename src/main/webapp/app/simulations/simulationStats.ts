export class SimulationStats {
  constructor(
    public numberOfRequests: number,
    public avgResponseTime: number,
    public failureRate: number,
  ) {}
}
