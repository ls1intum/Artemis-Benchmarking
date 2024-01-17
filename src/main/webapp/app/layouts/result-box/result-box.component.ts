import { Component, Input, OnInit } from '@angular/core';
import { SimulationStats } from '../../entities/simulation/simulationStats';
import { RequestType } from '../../entities/simulation/requestType';
import { DatePipe } from '@angular/common';
import { StatsByTime } from 'app/entities/simulation/statsByTime';
import { faChartLine, faTable } from '@fortawesome/free-solid-svg-icons';

@Component({
  selector: 'jhi-result-box',
  templateUrl: './result-box.component.html',
  providers: [DatePipe],
  styleUrls: ['./result-box.component.scss'],
})
export class ResultBoxComponent implements OnInit {
  faChartLine = faChartLine;
  faTable = faTable;

  @Input() simulationStats?: SimulationStats;
  data: any[] = [];
  chartDimensions: [number, number] = [600, 400];
  statsByTenSeconds: StatsByTime[] = [];
  showChart = false;

  constructor(private datePipe: DatePipe) {}

  ngOnInit(): void {
    this.simulationStats?.statsByMinute.sort((a, b) => new Date(a.dateTime).getTime() - new Date(b.dateTime).getTime());
    this.statsByTenSeconds = this.simulationStats?.statsByMinute ?? [];
    this.initChart();
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

  initChart(): void {
    if (this.simulationStats && this.simulationStats.requestType) {
      this.data = [
        {
          name: this.formatRequestType(this.simulationStats.requestType),
          series: this.simulationStats.statsByTenSec.map(stats => ({
            name: stats.dateTime,
            value: stats.avgResponseTime / 1_000_000,
          })),
        },
      ];
    }
  }

  axisFormat = (val: any): string => {
    if (this.statsByTenSeconds.length === 0) {
      return '';
    }
    if (this.statsByTenSeconds.length < 6) {
      return this.datePipe.transform(val, 'HH:mm:ss') ?? '';
    }
    const index = this.statsByTenSeconds.findIndex(metric => metric.dateTime === val);
    if (!!index && (index % 5 === 0 || index === 0)) {
      return this.datePipe.transform(val, 'HH:mm') ?? '';
    }
    return '';
  };
}
