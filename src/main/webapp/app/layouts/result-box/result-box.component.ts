import { Component, Input, OnInit } from '@angular/core';
import { SimulationStats } from '../../entities/simulation/simulationStats';
import { RequestType } from '../../entities/simulation/requestType';

@Component({
  selector: 'jhi-result-box',
  templateUrl: './result-box.component.html',
  styleUrls: ['./result-box.component.scss'],
})
export class ResultBoxComponent implements OnInit {
  @Input() simulationStats?: SimulationStats;

  ngOnInit(): void {
    this.simulationStats?.statsByMinute.sort((a, b) => new Date(a.dateTime).getTime() - new Date(b.dateTime).getTime());
  }
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
