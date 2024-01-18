import { HttpClientModule } from '@angular/common/http';
import { ApplicationConfigService } from 'app/core/config/application-config.service';
import { httpInterceptorProviders } from './core/interceptor';
import { AppRoutingModule } from './app-routing.module';
import { NgbDateDayjsAdapter } from './config/datepicker-adapter';
import { fontAwesomeIcons } from './config/font-awesome-icons';
import MainComponent from './layouts/main/main.component';
import MainModule from './layouts/main/main.module';
import { AppPageTitleStrategy } from './app-page-title-strategy';
import { NgModule, LOCALE_ID } from '@angular/core';
import { registerLocaleData } from '@angular/common';
import locale from '@angular/common/locales/en';
import { BrowserModule, Title } from '@angular/platform-browser';
import { TitleStrategy } from '@angular/router';
import { ServiceWorkerModule } from '@angular/service-worker';
import { FaIconLibrary, FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import dayjs from 'dayjs/esm';
import { NgbAccordionModule, NgbDateAdapter, NgbDatepickerConfig, NgbModule } from '@ng-bootstrap/ng-bootstrap';
import { FormsModule } from '@angular/forms';

import './config/dayjs';
import { SimulationsOverviewComponent } from './simulations/simulations-overview/simulations-overview.component';
import { SimulationCardComponent } from './layouts/simulation-card/simulation-card.component';
import { StatusIconComponent } from './layouts/status-icon/status-icon.component';
import { LogBoxComponent } from './layouts/log-box/log-box.component';
import { ResultBoxComponent } from './layouts/result-box/result-box.component';
import { CreateSimulationBoxComponent } from './layouts/create-simulation-box/create-simulation-box.component';
import { ServerBadgeComponent } from './layouts/server-badge/server-badge.component';
import { ModeExplanationComponent } from './layouts/mode-explanation/mode-explanation.component';
import { PrometheusBoxComponent } from './layouts/prometheus-box/prometheus-box.component';
import { LocalCiStatusCardComponent } from './layouts/local-ci-status-card/local-ci-status-card.component';
import { BrowserAnimationsModule } from '@angular/platform-browser/animations';
import { NgxChartsModule } from '@swimlane/ngx-charts';
// jhipster-needle-angular-add-module-import JHipster will add new module here

@NgModule({
  imports: [
    BrowserModule,
    // jhipster-needle-angular-add-module JHipster will add new module here
    AppRoutingModule,
    // Set this to true to enable service worker (PWA)
    ServiceWorkerModule.register('ngsw-worker.js', { enabled: false }),
    HttpClientModule,
    MainModule,
    FormsModule,
    NgbModule,
    FontAwesomeModule,
    NgbAccordionModule,
    PrometheusBoxComponent,
    LocalCiStatusCardComponent,
    NgxChartsModule,
    BrowserAnimationsModule,
  ],
  providers: [
    Title,
    { provide: LOCALE_ID, useValue: 'en' },
    { provide: NgbDateAdapter, useClass: NgbDateDayjsAdapter },
    httpInterceptorProviders,
    { provide: TitleStrategy, useClass: AppPageTitleStrategy },
  ],
  bootstrap: [MainComponent],
  declarations: [
    SimulationsOverviewComponent,
    SimulationCardComponent,
    StatusIconComponent,
    LogBoxComponent,
    ResultBoxComponent,
    CreateSimulationBoxComponent,
    ServerBadgeComponent,
    ModeExplanationComponent,
  ],
})
export class AppModule {
  constructor(applicationConfigService: ApplicationConfigService, iconLibrary: FaIconLibrary, dpConfig: NgbDatepickerConfig) {
    registerLocaleData(locale);
    iconLibrary.addIcons(...fontAwesomeIcons);
    dpConfig.minDate = { year: dayjs().subtract(100, 'year').year(), month: 1, day: 1 };
  }
}
