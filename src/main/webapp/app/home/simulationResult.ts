import { SimulationStats } from './simulationStats';

export class SimulationResult {
  constructor(
    public authenticationStats: SimulationStats,
    public getExamStats: SimulationStats,
    public startExamStats: SimulationStats,
    public submitExerciseStats: SimulationStats,
    public submitExamStats: SimulationStats,
    public cloneStats: SimulationStats,
    public pushStats: SimulationStats,
    public miscStats: SimulationStats,
  ) {}
}
