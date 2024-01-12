import { MetricValue } from './metric-value';

export class Metric {
  constructor(
    public name: string,
    public values: MetricValue[],
  ) {}
}
