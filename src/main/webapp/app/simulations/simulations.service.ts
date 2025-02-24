import { Injectable, inject } from '@angular/core';
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
import { ArtemisServer } from '../core/util/artemisServer';
import { SimulationSchedule } from '../entities/simulation/simulationSchedule';
import { CiStatus } from '../entities/simulation/ciStatus';

@Injectable({
  providedIn: 'root',
})
export class SimulationsService {
  private httpClient = inject(HttpClient);
  private applicationConfigService = inject(ApplicationConfigService);
  private websocketService = inject(WebsocketService);

  receiveSimulationResult(run: SimulationRun): Observable<SimulationStats[]> {
    return this.websocketService.receive(`/topic/simulation/runs/${run.id}/result`).pipe(map((res: any) => res as SimulationStats[]));
  }

  receiveSimulationLog(run: SimulationRun): Observable<LogMessage> {
    return this.websocketService.receive(`/topic/simulation/runs/${run.id}/log`).pipe(map((res: any) => res as LogMessage));
  }

  receiveSimulationStatus(run: SimulationRun): Observable<Status> {
    return this.websocketService.receive(`/topic/simulation/runs/${run.id}/status`).pipe(map((res: any) => res as Status));
  }

  receiveNewSimulationRun(simulation: Simulation): Observable<SimulationRun> {
    return this.websocketService.receive(`/topic/simulation/${simulation.id}/runs/new`).pipe(map((res: any) => res as SimulationRun));
  }

  receiveCiStatus(simulationRun: SimulationRun): Observable<CiStatus> {
    return this.websocketService.receive(`/topic/simulation/runs/${simulationRun.id}/ci-status`).pipe(map((res: any) => res as CiStatus));
  }

  createSimulation(simulation: Simulation): Observable<Simulation> {
    const endpoint = this.applicationConfigService.getEndpointFor(`/api/simulations`);
    return this.httpClient.post(endpoint, simulation).pipe(map((res: any) => res as Simulation));
  }

  getSimulations(): Observable<Simulation[]> {
    const endpoint = this.applicationConfigService.getEndpointFor(`/api/simulations`);
    return this.httpClient.get(endpoint).pipe(map((res: any) => res as Simulation[]));
  }

  getSimulation(simulationId: number): Observable<Simulation> {
    const endpoint = this.applicationConfigService.getEndpointFor(`/api/simulations/${simulationId}`);
    return this.httpClient.get(endpoint).pipe(map((res: any) => res as Simulation));
  }

  getSimulationRun(simulationRunId: number): Observable<SimulationRun> {
    const endpoint = this.applicationConfigService.getEndpointFor(`/api/simulations/runs/${simulationRunId}`);
    return this.httpClient.get(endpoint).pipe(map((res: any) => res as SimulationRun));
  }

  runSimulation(simulationId: number, account?: ArtemisAccountDTO): Observable<SimulationRun> {
    const endpoint = this.applicationConfigService.getEndpointFor(`/api/simulations/${simulationId}/run`);
    return this.httpClient.post(endpoint, account).pipe(map((res: any) => res as SimulationRun));
  }

  deleteSimulation(simulationId: number): Observable<undefined> {
    const endpoint = this.applicationConfigService.getEndpointFor(`/api/simulations/${simulationId}`);
    return this.httpClient.delete<undefined>(endpoint);
  }

  patchSimulationInstructorAccount(simulationId: number, account: ArtemisAccountDTO): Observable<Simulation> {
    const endpoint = this.applicationConfigService.getEndpointFor(`/api/simulations/${simulationId}/instructor-account`);
    return this.httpClient.patch(endpoint, account).pipe(map((res: any) => res as Simulation));
  }

  deleteSimulationInstructorAccount(simulationId: number): Observable<Simulation> {
    const endpoint = this.applicationConfigService.getEndpointFor(`/api/simulations/${simulationId}/instructor-account`);
    return this.httpClient.delete(endpoint).pipe(map((res: any) => res as Simulation));
  }

  deleteSimulationRun(runId: number): Observable<undefined> {
    const endpoint = this.applicationConfigService.getEndpointFor(`/api/simulations/runs/${runId}`);
    return this.httpClient.delete<undefined>(endpoint);
  }

  abortSimulationRun(runId: number): Observable<undefined> {
    const endpoint = this.applicationConfigService.getEndpointFor(`/api/simulations/runs/${runId}/abort`);
    return this.httpClient.post<undefined>(endpoint, {});
  }

  getServersWithCleanupEnabled(): Observable<ArtemisServer[]> {
    const endpoint = this.applicationConfigService.getEndpointFor(`/api/simulations/servers/cleanup-enabled`);
    return this.httpClient.get(endpoint).pipe(map((res: any) => res as ArtemisServer[]));
  }

  createSimulationSchedule(simulationId: number, simulationSchedule: SimulationSchedule): Observable<SimulationSchedule | undefined> {
    const endpoint = this.applicationConfigService.getEndpointFor(`/api/simulations/${simulationId}/schedule`);
    return this.httpClient.post(endpoint, simulationSchedule).pipe(map((res: any) => res as SimulationSchedule));
  }

  updateSimulationSchedule(simulationSchedule: SimulationSchedule): Observable<SimulationSchedule | undefined> {
    const endpoint = this.applicationConfigService.getEndpointFor(`/api/simulations/schedules/${simulationSchedule.id}`);
    return this.httpClient.put(endpoint, simulationSchedule).pipe(map((res: any) => res as SimulationSchedule));
  }

  deleteSimulationSchedule(simulationScheduleId: number): Observable<undefined> {
    const endpoint = this.applicationConfigService.getEndpointFor(`/api/simulations/schedules/${simulationScheduleId}`);
    return this.httpClient.delete<undefined>(endpoint);
  }

  getSimulationSchedules(simulationId: number): Observable<SimulationSchedule[]> {
    const endpoint = this.applicationConfigService.getEndpointFor(`/api/simulations/${simulationId}/schedules`);
    return this.httpClient.get(endpoint).pipe(map((res: any) => res as SimulationSchedule[]));
  }

  subscribeToSimulationSchedule(simulationScheduleId: number, email: string): Observable<undefined> {
    const endpoint = this.applicationConfigService.getEndpointFor(`/api/simulations/schedules/${simulationScheduleId}/subscribe`);
    return this.httpClient.post<undefined>(endpoint, email);
  }

  public unsubscribeFromSelectedSimulationRun(run: SimulationRun): void {
    this.websocketService.unsubscribe(`/topic/simulation/runs/${run.id}/result`);
    this.websocketService.unsubscribe(`/topic/simulation/runs/${run.id}/log`);
    this.websocketService.unsubscribe(`/topic/simulation/runs/${run.id}/ci-status`);
  }

  public unsubscribeFromSimulationRun(run: SimulationRun): void {
    this.unsubscribeFromSelectedSimulationRun(run);
    this.websocketService.unsubscribe(`/topic/simulation/runs/${run.id}/status`);
  }
}
