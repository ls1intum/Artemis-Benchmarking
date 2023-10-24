import { Component, OnInit, OnDestroy } from '@angular/core';
import { Router, RouterModule } from '@angular/router';
import { Subject } from 'rxjs';
import { takeUntil } from 'rxjs/operators';

import SharedModule from 'app/shared/shared.module';
import { AccountService } from 'app/core/auth/account.service';
import { Account } from 'app/core/auth/account.model';
import { HttpClient } from '@angular/common/http';
import { ApplicationConfigService } from '../core/config/application-config.service';
import { RxStomp } from '@stomp/rx-stomp/esm6/rx-stomp';
import SockJS from 'sockjs-client';
import { AuthServerProvider } from '../core/auth/auth-jwt.service';
import { Location } from '@angular/common';
import { map } from 'rxjs/internal/operators/map';
import { SimulationResult } from './simulationResult';

@Component({
  standalone: true,
  selector: 'jhi-home',
  templateUrl: './home.component.html',
  styleUrls: ['./home.component.scss'],
  imports: [SharedModule, RouterModule],
})
export default class HomeComponent implements OnInit, OnDestroy {
  account: Account | null = null;

  private readonly destroy$ = new Subject<void>();
  private rxStomp: RxStomp = new RxStomp();

  constructor(
    private accountService: AccountService,
    private router: Router,
    private httpClient: HttpClient,
    private applicationConfigService: ApplicationConfigService,
    private authServerProvider: AuthServerProvider,
    private location: Location,
  ) {}

  ngOnInit(): void {
    this.accountService
      .getAuthenticationState()
      .pipe(takeUntil(this.destroy$))
      .subscribe(account => {
        this.account = account;
        this.updateCredentials();
        this.rxStomp.activate();
      });

    this.rxStomp
      .watch('/topic/simulation/completed')
      .pipe(map(imessage => JSON.parse(imessage.body)))
      .subscribe((result: SimulationResult) => {
        console.log(result);
      });
  }

  login(): void {
    this.router.navigate(['/login']);
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  startSimulation() {
    this.httpClient.post(this.applicationConfigService.getEndpointFor('/api/simulations?users=10'), undefined).subscribe(() => {});
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
