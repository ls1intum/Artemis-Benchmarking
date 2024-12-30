import { Component, Input, OnInit } from '@angular/core';
import { SimulationStats } from '../../entities/simulation/simulationStats';
import { RequestType } from '../../entities/simulation/requestType';
import { DatePipe } from '@angular/common';
import { StatsByTime } from 'app/entities/simulation/statsByTime';
import { faChartLine, faTable } from '@fortawesome/free-solid-svg-icons';
import { NgxChartsModule, ScaleType } from '@swimlane/ngx-charts';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';

@Component({
  selector: 'jhi-result-box',
  templateUrl: './result-box.component.html',
  providers: [DatePipe],
  styleUrls: ['./result-box.component.scss'],
  imports: [FaIconComponent, NgxChartsModule],
})
export class ResultBoxComponent implements OnInit {
  faChartLine = faChartLine;
  faTable = faTable;

  @Input() simulationStats?: SimulationStats;
  dataResponseTime: any[] = [];
  dataNumberOfRequests: any[] = [];
  statsBySecond: StatsByTime[] = [];
  showChart = false;
  referenceLine: any[] = [];

  colorSchemeResponseTime = {
    name: 'colorTime',
    selectable: true,
    group: ScaleType.Linear,
    domain: ['#EB0505'],
  };

  colorSchemeNumberOfRequests = {
    name: 'colorNumber',
    selectable: true,
    group: ScaleType.Linear,
    domain: ['#33ADFF'],
  };

  constructor(private datePipe: DatePipe) {}

  ngOnInit(): void {
    this.simulationStats?.statsByMinute.sort((a, b) => new Date(a.dateTime).getTime() - new Date(b.dateTime).getTime());
    this.statsBySecond =
      this.simulationStats?.statsBySecond.sort((a, b) => new Date(a.dateTime).getTime() - new Date(b.dateTime).getTime()) ?? [];
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
    if (this.simulationStats) {
      this.referenceLine = [
        {
          name: 'Avg. response time',
          value: this.simulationStats.avgResponseTime / 1_000_000,
        },
      ];
      this.dataResponseTime = [
        {
          name: this.formatRequestType(this.simulationStats.requestType),
          series: this.statsBySecond.map(stats => ({
            name: stats.dateTime,
            value: stats.avgResponseTime / 1_000_000,
          })),
        },
      ];
      this.dataNumberOfRequests = [
        {
          name: this.formatRequestType(this.simulationStats.requestType),
          series: this.statsBySecond.map(stats => ({
            name: stats.dateTime,
            value: stats.numberOfRequests,
          })),
        },
      ];
    }
  }

  axisFormat = (val: any): string => this.datePipe.transform(val, 'HH:mm:ss') ?? '';
}
