import { Component, Input } from '@angular/core';
import { SimulationStats } from '../../models/simulationStats';
import { RequestType } from '../../models/requestType';

@Component({
  selector: 'jhi-result-box',
  templateUrl: './result-box.component.html',
  styleUrls: ['./result-box.component.scss'],
})
export class ResultBoxComponent {
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

  formatRequestType(requestType: RequestType): string {
    return requestType.replace(/_/g, ' ');
  }
}
