import { Component, Input, OnChanges, OnInit } from '@angular/core';
import { SimulationRun } from '../../entities/simulation/simulationRun';
import { ApplicationConfigService } from '../../core/config/application-config.service';
import { WebsocketService } from '../../core/websocket/websocket.service';
import { MetricValue } from '../../entities/metric-value';
import { HttpClient } from '@angular/common/http';
import { map } from 'rxjs/operators';
import { Observable } from 'rxjs';
import { DatePipe } from '@angular/common';
import { NgxChartsModule } from '@swimlane/ngx-charts';
import { FormsModule } from '@angular/forms';
import { BrowserAnimationsModule } from '@angular/platform-browser/animations';

@Component({
  selector: 'jhi-prometheus-box',
  standalone: true,
  imports: [DatePipe, NgxChartsModule, FormsModule, BrowserAnimationsModule],
  providers: [DatePipe],
  templateUrl: './prometheus-box.component.html',
  styleUrl: './prometheus-box.component.scss',
})
export class PrometheusBoxComponent implements OnInit, OnChanges {
  private static readonly INTERVAL_SECONDS = 15;

  @Input()
  run!: SimulationRun;
  metricValues: MetricValue[] = [];
  data: any[] = [];

  colorScheme = {
    domain: ['#5AA454', '#E44D25', '#CFC0BB', '#7aa3e5', '#a8385d', '#aae3f5'],
  };

  axisFormat = (val: any): string => {
    if (!this.metricValues || this.metricValues.length === 0) {
      return '';
    }
    if (this.metricValues.findIndex(metric => metric.dateTime === val) % 10 === 0) {
      return this.datePipe.transform(val, 'HH:mm') ?? '';
    }
    return '';
  };

  constructor(
    private applicationConfigService: ApplicationConfigService,
    private websocketService: WebsocketService,
    private httpClient: HttpClient,
    private datePipe: DatePipe,
  ) {}

  ngOnInit(): void {
    this.updateMetrics();
    setInterval(() => this.updateMetrics(), 1000 * PrometheusBoxComponent.INTERVAL_SECONDS);
  }

  ngOnChanges(): void {
    this.updateMetrics();
  }

  updateMetrics(): void {
    this.fetchMetrics().subscribe(metrics => {
      this.metricValues = metrics;
      this.data = [
        {
          name: 'CPU Usage',
          series: this.metricValues.map(metric => {
            return {
              name: metric.dateTime,
              value: metric.value,
            };
          }),
        },
      ];
      this.data = [...this.data];
    });
  }

  fetchMetrics(): Observable<MetricValue[]> {
    const endpoint = this.applicationConfigService.getEndpointFor('/api/prometheus/' + this.run.id);
    return this.httpClient.get(endpoint).pipe(map((res: any) => res as MetricValue[]));
  }
}
