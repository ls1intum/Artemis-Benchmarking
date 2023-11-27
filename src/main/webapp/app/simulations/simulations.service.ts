import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { ApplicationConfigService } from '../core/config/application-config.service';
import { Observable } from 'rxjs/internal/Observable';
import { map } from 'rxjs';
import { WebsocketService } from '../core/websocket/websocket.service';
import { ArtemisAccountDTO } from './artemisAccountDTO';
import { Simulation } from '../entities/simulation/simulation';
import { SimulationRun, Status } from '../entities/simulation/simulationRun';
import { SimulationStats } from '../entities/simulation/simulationStats';
import { LogMessage } from '../entities/simulation/logMessage';

@Injectable({
  providedIn: 'root',
})
export class SimulationsService {
  constructor(
    private httpClient: HttpClient,
    private applicationConfigService: ApplicationConfigService,
    private websocketService: WebsocketService,
  ) {}

  receiveSimulationResult(run: SimulationRun): Observable<SimulationStats[]> {
    this.websocketService.subscribe('/topic/simulation/runs/' + run.id + '/result');
    return this.websocketService.receive('/topic/simulation/runs/' + run.id + '/result').pipe(map((res: any) => res as SimulationStats[]));
  }

  receiveSimulationLog(run: SimulationRun): Observable<LogMessage> {
    this.websocketService.subscribe('/topic/simulation/runs/' + run.id + '/log');
    return this.websocketService.receive('/topic/simulation/runs/' + run.id + '/log').pipe(map((res: any) => res as LogMessage));
  }

  receiveSimulationStatus(run: SimulationRun): Observable<Status> {
    this.websocketService.subscribe('/topic/simulation/runs/' + run.id + '/status');
    return this.websocketService.receive('/topic/simulation/runs/' + run.id + '/status').pipe(map((res: any) => res as Status));
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

  getSimulationRun(simulationRunId: number): Observable<SimulationRun> {
    const endpoint = this.applicationConfigService.getEndpointFor('/api/simulations/runs/' + simulationRunId);
    return this.httpClient.get(endpoint).pipe(map((res: any) => res as SimulationRun));
  }

  runSimulation(simulationId: number, account?: ArtemisAccountDTO): Observable<SimulationRun> {
    const endpoint = this.applicationConfigService.getEndpointFor('/api/simulations/' + simulationId + '/run');
    return this.httpClient.post(endpoint, account).pipe(map((res: any) => res as SimulationRun));
  }

  deleteSimulation(simulationId: number): Observable<void> {
    const endpoint = this.applicationConfigService.getEndpointFor('/api/simulations/' + simulationId);
    return this.httpClient.delete(endpoint).pipe(map(() => {}));
  }

  deleteSimulationRun(runId: number): Observable<void> {
    const endpoint = this.applicationConfigService.getEndpointFor('/api/simulations/runs/' + runId);
    return this.httpClient.delete(endpoint).pipe(map(() => {}));
  }

  abortSimulationRun(runId: number): Observable<void> {
    const endpoint = this.applicationConfigService.getEndpointFor('/api/simulations/runs/' + runId + '/abort');
    return this.httpClient.post(endpoint, {}).pipe(map(() => {}));
  }

  public unsubscribeFromSelectedSimulationRun(run: SimulationRun): void {
    this.websocketService.unsubscribe('/topic/simulation/runs/' + run.id + '/result');
    this.websocketService.unsubscribe('/topic/simulation/runs/' + run.id + '/log');
  }

  public unsubscribeFromSimulationRun(run: SimulationRun): void {
    this.unsubscribeFromSelectedSimulationRun(run);
    this.websocketService.unsubscribe('/topic/simulation/runs/' + run.id + '/status');
  }
}
