import { Injectable } from '@angular/core';
import { RxStomp } from '@stomp/rx-stomp/esm6/rx-stomp';
import { SimulationResult } from './simulationResult';
import { AccountService } from '../core/auth/account.service';
import { map } from 'rxjs/internal/operators/map';
import SockJS from 'sockjs-client';
import { HttpClient } from '@angular/common/http';
import { ApplicationConfigService } from '../core/config/application-config.service';
import { AuthServerProvider } from '../core/auth/auth-jwt.service';
import { Location } from '@angular/common';
import { Observable } from 'rxjs/internal/Observable';

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
    this.accountService.getAuthenticationState().subscribe(account => {
      this.updateCredentials();
      this.rxStomp.activate();
    });
  }

  websocketSubscription(): Observable<SimulationResult> {
    return this.rxStomp.watch('/topic/simulation/completed').pipe(map(imessage => JSON.parse(imessage.body)));
  }

  startSimulation() {
    return this.httpClient.post(this.applicationConfigService.getEndpointFor('/api/simulations?users=10'), undefined);
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