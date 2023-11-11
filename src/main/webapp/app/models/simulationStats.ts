import { SimulationRun } from './simulationRun';
import { RequestType } from './requestType';

export class SimulationStats {
  constructor(
    public id: number,
    public numberOfRequests: number,
    public avgResponseTime: number,
    public simulationRun: SimulationRun,
    public requestType: RequestType,
    public requestsByMinute: Map<Date, number>,
    public avgResponseTimeByMinute: Map<Date, number>,
  ) {}
}
