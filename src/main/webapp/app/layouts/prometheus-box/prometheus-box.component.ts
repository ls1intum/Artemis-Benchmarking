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
  metricValuesArtemis: MetricValue[] = [];
  dataArtemis: any[] = [];
  metricValuesVcs: MetricValue[] = [];
  dataVcs: any[] = [];
  metricValuesCi: MetricValue[] = [];
  dataCi: any[] = [];

  axisFormatArtemis = (val: any): string => {
    if (!this.metricValuesArtemis || this.metricValuesArtemis.length === 0) {
      return '';
    }
    const index = this.metricValuesArtemis.findIndex(metric => metric.dateTime === val);
    if (index % 10 === 0) {
      return this.datePipe.transform(val, 'HH:mm') ?? '';
    }
    return '';
  };

  axisFormatVcs = (val: any): string => {
    if (!this.metricValuesVcs || this.metricValuesVcs.length === 0) {
      return '';
    }
    const index = this.metricValuesVcs.findIndex(metric => metric.dateTime === val);
    if (index % 10 === 0) {
      return this.datePipe.transform(val, 'HH:mm') ?? '';
    }
    return '';
  };

  axisFormatCi = (val: any): string => {
    if (!this.metricValuesCi || this.metricValuesCi.length === 0) {
      return '';
    }
    const index = this.metricValuesCi.findIndex(metric => metric.dateTime === val);
    if (index % 10 === 0) {
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
    this.fetchMetricsArtemis().subscribe(metrics => {
      this.metricValuesArtemis = metrics;
      this.dataArtemis = [
        {
          name: 'CPU Usage Artemis',
          series: this.metricValuesArtemis.map(metric => {
            return {
              name: metric.dateTime,
              value: metric.value,
            };
          }),
        },
      ];
      this.dataArtemis = [...this.dataArtemis];
    });
    this.fetchMetricsVcs().subscribe(metrics => {
      this.metricValuesVcs = metrics;
      this.dataVcs = [
        {
          name: 'CPU Usage VCS',
          series: this.metricValuesVcs.map(metric => {
            return {
              name: metric.dateTime,
              value: metric.value,
            };
          }),
        },
      ];
      this.dataVcs = [...this.dataVcs];
    });
    this.fetchMetricsCi().subscribe(metrics => {
      this.metricValuesCi = metrics;
      this.dataCi = [
        {
          name: 'CPU Usage CI',
          series: this.metricValuesCi.map(metric => {
            return {
              name: metric.dateTime,
              value: metric.value,
            };
          }),
        },
      ];
      this.dataCi = [...this.dataCi];
    });
  }

  fetchMetricsArtemis(): Observable<MetricValue[]> {
    const endpoint = this.applicationConfigService.getEndpointFor('/api/prometheus/' + this.run.id + '/artemis');
    return this.httpClient.get(endpoint).pipe(map((res: any) => res as MetricValue[]));
  }

  fetchMetricsVcs(): Observable<MetricValue[]> {
    const endpoint = this.applicationConfigService.getEndpointFor('/api/prometheus/' + this.run.id + '/vcs');
    return this.httpClient.get(endpoint).pipe(map((res: any) => res as MetricValue[]));
  }

  fetchMetricsCi(): Observable<MetricValue[]> {
    const endpoint = this.applicationConfigService.getEndpointFor('/api/prometheus/' + this.run.id + '/ci');
    return this.httpClient.get(endpoint).pipe(map((res: any) => res as MetricValue[]));
  }
}
