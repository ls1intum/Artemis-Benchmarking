import { Component, Input } from '@angular/core';
import { SimulationStats } from '../../../models/simulationStats';

@Component({
  selector: 'jhi-result-part',
  templateUrl: './result-part.component.html',
  styleUrls: ['./result-part.component.scss'],
})
export class ResultPartComponent {
  @Input() simulationStats?: SimulationStats;

  formatDuration(durationInNanoSeconds: number): string {
    const durationInMicroSeconds = durationInNanoSeconds / 1000;
    if (durationInMicroSeconds > 1000) {
      const durationInMilliSeconds = durationInMicroSeconds / 1000;
      if (durationInMilliSeconds > 1000) {
        const durationInSeconds = durationInMilliSeconds / 1000;
        return durationInSeconds.toFixed(2) + ' s';
      }
      return durationInMilliSeconds.toFixed(2) + ' ms';
    }
    return durationInMicroSeconds.toFixed(2) + ' µs';
  }
}
