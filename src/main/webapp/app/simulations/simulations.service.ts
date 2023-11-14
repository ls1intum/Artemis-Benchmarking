import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { ApplicationConfigService } from '../core/config/application-config.service';
import { Observable } from 'rxjs/internal/Observable';
import { ArtemisServer } from '../models/artemisServer';
import { Subscription, map } from 'rxjs';
import { WebsocketService } from '../core/websocket/websocket.service';
import { SimulationResult } from '../models/simulationResult';
import { ArtemisAccountDTO } from '../models/artemisAccountDTO';
import { Simulation } from '../models/simulation';
import { SimulationRun } from '../models/simulationRun';

@Injectable({
  providedIn: 'root',
})
export class SimulationsService {
  public infoMessages$: Observable<string> = new Observable<string>();
  public errorMessages$: Observable<string> = new Observable<string>();
  public failure$: Observable<void> = new Observable<void>();
  public simulationResult$: Observable<SimulationResult> = new Observable<SimulationResult>();
  public simulationCompleted$: Observable<void> = new Observable<void>();

  private currentWebsocketChannels?: string[];
  private currentWebsocketReceiveSubscriptions?: Subscription[];

  constructor(
    private httpClient: HttpClient,
    private applicationConfigService: ApplicationConfigService,
    private websocketService: WebsocketService,
  ) {
    this.subscribeToSimulationUpdates();
  }

  subscribeToSimulationUpdates(): void {
    this.websocketService.subscribe('/topic/simulation/info');
    this.infoMessages$ = this.websocketService.receive('/topic/simulation/info');

    this.websocketService.subscribe('/topic/simulation/error');
    this.errorMessages$ = this.websocketService.receive('/topic/simulation/error');

    this.websocketService.subscribe('/topic/simulation/failed');
    this.failure$ = this.websocketService.receive('/topic/simulation/failed');

    this.websocketService.subscribe('/topic/simulation/result');
    this.simulationResult$ = this.websocketService.receive('/topic/simulation/result');

    this.websocketService.subscribe('/topic/simulation/completed');
    this.simulationCompleted$ = this.websocketService.receive('/topic/simulation/completed');
  }

  startSimulation(
    numberOfUsers: number,
    courseId: number,
    examId: number,
    server: ArtemisServer,
    account?: ArtemisAccountDTO,
  ): Observable<object> {
    const endpoint = this.applicationConfigService.getEndpointFor(
      '/api/simulations?users=' + numberOfUsers + '&courseId=' + courseId + '&examId=' + examId + '&server=' + server,
    );
    if (!window.location.protocol.startsWith('https:')) {
      // Only send credentials over HTTPS
      account = undefined;
    }
    return this.httpClient.post(endpoint, account);
  }

  createSimulation(simulation: Simulation): Observable<Simulation> {
    const endpoint = this.applicationConfigService.getEndpointFor('/api/simulations');
    return this.httpClient.post(endpoint, simulation).pipe(map((res: any) => res as Simulation));
  }

  getSimulations(): Observable<Simulation[]> {
    const endpoint = this.applicationConfigService.getEndpointFor('/api/simulations');
    return this.httpClient.get(endpoint).pipe(map((res: any) => res as Simulation[]));
  }

  getSimulation(simulationId: number): Observable<Simulation> {
    const endpoint = this.applicationConfigService.getEndpointFor('/api/simulations/' + simulationId);
    return this.httpClient.get(endpoint).pipe(map((res: any) => res as Simulation));
  }

  runSimulation(simulationId: number, account?: ArtemisAccountDTO): Observable<SimulationRun> {
    const endpoint = this.applicationConfigService.getEndpointFor('/api/simulations/' + simulationId + '/run');
    return this.httpClient.post(endpoint, account).pipe(map((res: any) => res as SimulationRun));
  }

  private unsubscribeFromSimulationUpdates(): void {
    this.currentWebsocketReceiveSubscriptions?.forEach(subscription => subscription.unsubscribe());
    this.currentWebsocketReceiveSubscriptions = undefined;

    this.currentWebsocketChannels?.forEach(channel => this.websocketService.unsubscribe(channel));
    this.currentWebsocketChannels = undefined;
  }
}
