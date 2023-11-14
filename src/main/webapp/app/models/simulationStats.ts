import { SimulationRun } from './simulationRun';
import { RequestType } from './requestType';
import { StatsByMinute } from './statsByMinute';

export class SimulationStats {
  constructor(
    public id: number,
    public numberOfRequests: number,
    public avgResponseTime: number,
    public simulationRun: SimulationRun,
    public requestType: RequestType,
    public statsByMinute: StatsByMinute[],
  ) {}
}
