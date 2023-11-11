import { ArtemisServer } from './artemisServer';
import { SimulationRun } from './simulationRun';

export class Simulation {
  constructor(
    public id: number,
    public name: string,
    public courseId: number,
    public examId: number,
    public numberOfUsers: number,
    public server: ArtemisServer,
    public runs: SimulationRun[],
  ) {}
}
