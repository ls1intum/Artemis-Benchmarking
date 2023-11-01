import { Injectable } from '@angular/core';
import { RxStomp } from '@stomp/rx-stomp';
import { SimulationResult } from './simulationResult';
import { AccountService } from '../core/auth/account.service';
import { map } from 'rxjs/internal/operators/map';
import SockJS from 'sockjs-client';
import { HttpClient } from '@angular/common/http';
import { ApplicationConfigService } from '../core/config/application-config.service';
import { AuthServerProvider } from '../core/auth/auth-jwt.service';
import { Location } from '@angular/common';
import { Observable } from 'rxjs/internal/Observable';
import { ArtemisServer } from './artemisServer';

@Injectable({
  providedIn: 'root',
})
export class SimulationsService {
  private rxStomp: RxStomp = new RxStomp();

  constructor(
    private accountService: AccountService,
    private httpClient: HttpClient,
    private applicationConfigService: ApplicationConfigService,
    private authServerProvider: AuthServerProvider,
    private location: Location,
  ) {
    this.accountService.getAuthenticationState().subscribe(() => {
      this.updateCredentials();
      this.rxStomp.activate();
    });
  }

  websocketSubscriptionSimulationCompleted(): Observable<SimulationResult> {
    return this.rxStomp.watch('/topic/simulation/completed').pipe(map(imessage => JSON.parse(imessage.body) as SimulationResult));
  }

  websocketSubscriptionSimulationError(): Observable<string> {
    return this.rxStomp.watch('/topic/simulation/error').pipe(map(imessage => imessage.body));
  }

  startSimulation(numberOfUsers: number, courseId: number, examId: number, server: ArtemisServer): Observable<object> {
    return this.httpClient.post(
      this.applicationConfigService.getEndpointFor(
        '/api/simulations?users=' + numberOfUsers + '&courseId=' + courseId + '&examId=' + examId + '&server=' + server,
      ),
      undefined,
    );
  }

  private buildUrl(): string {
    // building absolute path so that websocket doesn't fail when deploying with a context path
    let url = '/websocket/simulation';
    url = this.location.prepareExternalUrl(url);
    const authToken = this.authServerProvider.getToken();
    if (authToken) {
      return `${url}?access_token=${authToken}`;
    }
    return url;
  }

  private updateCredentials(): void {
    this.rxStomp.configure({
      webSocketFactory: () => SockJS(this.buildUrl()),
    });
  }
}
