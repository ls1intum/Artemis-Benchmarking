import { Component, Input, OnChanges, OnInit } from '@angular/core';
import { SimulationRun } from '../../entities/simulation/simulationRun';
import { ApplicationConfigService } from '../../core/config/application-config.service';
import { MetricValue } from '../../entities/metric-value';
import { HttpClient } from '@angular/common/http';
import { map } from 'rxjs/operators';
import { Observable } from 'rxjs';
import { DatePipe } from '@angular/common';
import { NgxChartsModule, ScaleType } from '@swimlane/ngx-charts';
import { FormsModule } from '@angular/forms';
import { Metric } from '../../entities/metric';

@Component({
  selector: 'jhi-prometheus-box',
  imports: [NgxChartsModule, FormsModule],
  templateUrl: './prometheus-box.component.html',
  styleUrl: './prometheus-box.component.scss',
})
export class PrometheusBoxComponent implements OnInit, OnChanges {
  private static readonly INTERVAL_SECONDS = 15;

  @Input() run!: SimulationRun;

  metricValuesArtemis: MetricValue[] = [];
  dataArtemis: any[] = [];
  metricValuesVcs: MetricValue[] = [];
  dataVcs: any[] = [];
  metricValuesCi: MetricValue[] = [];
  dataCi: any[] = [];
  timeInterval: NodeJS.Timeout | undefined;

  colorScheme = {
    name: 'color',
    selectable: true,
    group: ScaleType.Linear,
    domain: ['#FF4833', '#A10A28', '#33ADFF', '#37FF33', '#FF33FC'],
  };

  constructor(
    private applicationConfigService: ApplicationConfigService,
    private httpClient: HttpClient,
    private datePipe: DatePipe,
  ) {}

  axisFormatArtemis = (val: any): string => {
    if (this.metricValuesArtemis.length === 0) {
      return '';
    }
    const index = this.metricValuesArtemis.findIndex(metric => metric.dateTime === val);
    if (index % 10 === 0) {
      return this.datePipe.transform(val, 'HH:mm') ?? '';
    }
    return '';
  };

  axisFormatVcs = (val: any): string => {
    if (this.metricValuesVcs.length === 0) {
      return '';
    }
    const index = this.metricValuesVcs.findIndex(metric => metric.dateTime === val);
    if (index % 10 === 0) {
      return this.datePipe.transform(val, 'HH:mm') ?? '';
    }
    return '';
  };

  axisFormatCi = (val: any): string => {
    if (this.metricValuesCi.length === 0) {
      return '';
    }
    const index = this.metricValuesCi.findIndex(metric => metric.dateTime === val);
    if (index % 10 === 0) {
      return this.datePipe.transform(val, 'HH:mm') ?? '';
    }
    return '';
  };

  ngOnInit(): void {
    this.updateMetrics();
    // Update metrics every 15 seconds if the run is still running / only recently finished
    if (!this.run.endDateTime || new Date(this.run.endDateTime).getTime() + 1000 * 60 * 30 >= Date.now()) {
      this.timeInterval = setInterval(() => this.updateMetrics(), 1000 * PrometheusBoxComponent.INTERVAL_SECONDS);
    }
  }

  ngOnChanges(): void {
    this.updateMetrics();
    // Stop updating metrics if the run is finished for more than 30 minutes
    if (!!this.run.endDateTime && new Date(this.run.endDateTime).getTime() + 1000 * 60 * 30 < Date.now()) {
      clearInterval(this.timeInterval);
    }
  }

  updateMetrics(): void {
    this.fetchMetricsArtemis().subscribe(metrics => {
      this.metricValuesArtemis = metrics.at(0)?.values ?? [];
      this.dataArtemis = [];
      metrics.forEach(metric => {
        this.dataArtemis.push({
          name: metric.name,
          series: metric.values.map(value => ({
            name: value.dateTime,
            value: value.value,
          })),
        });
      });
      this.dataArtemis = [...this.dataArtemis];
    });
    this.fetchMetricsVcs().subscribe(metrics => {
      this.metricValuesVcs = metrics.at(0)?.values ?? [];
      this.dataVcs = [];
      metrics.forEach(metric => {
        this.dataVcs.push({
          name: metric.name,
          series: metric.values.map(value => ({
            name: value.dateTime,
            value: value.value,
          })),
        });
      });
      this.dataVcs = [...this.dataVcs];
    });
    this.fetchMetricsCi().subscribe(metrics => {
      this.metricValuesCi = metrics.at(0)?.values ?? [];
      this.dataCi = [];
      metrics.forEach(metric => {
        this.dataCi.push({
          name: metric.name,
          series: metric.values.map(value => ({
            name: value.dateTime,
            value: value.value,
          })),
        });
      });
      this.dataCi = [...this.dataCi];
    });
  }

  fetchMetricsArtemis(): Observable<Metric[]> {
    const endpoint = this.applicationConfigService.getEndpointFor(`/api/prometheus/${this.run.id}/artemis`);
    return this.httpClient.get(endpoint).pipe(map((res: any) => res as Metric[]));
  }

  fetchMetricsVcs(): Observable<Metric[]> {
    const endpoint = this.applicationConfigService.getEndpointFor(`/api/prometheus/${this.run.id}/vcs`);
    return this.httpClient.get(endpoint).pipe(map((res: any) => res as Metric[]));
  }

  fetchMetricsCi(): Observable<Metric[]> {
    const endpoint = this.applicationConfigService.getEndpointFor(`/api/prometheus/${this.run.id}/ci`);
    return this.httpClient.get(endpoint).pipe(map((res: any) => res as Metric[]));
  }
}
