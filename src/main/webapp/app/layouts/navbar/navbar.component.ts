import SharedModule from 'app/shared/shared.module';
import HasAnyAuthorityDirective from 'app/shared/auth/has-any-authority.directive';
import { VERSION } from 'app/app.constants';
import { Account } from 'app/core/auth/account.model';
import { AccountService } from 'app/core/auth/account.service';
import { LoginService } from 'app/login/login.service';
import { ProfileService } from 'app/layouts/profiles/profile.service';
import { EntityNavbarItems } from 'app/entities/entity-navbar-items';
import NavbarItem from './navbar-item.model';
import { Router, RouterModule } from '@angular/router';
import { Component, OnInit } from '@angular/core';
import { ArtemisServer } from '../../core/util/artemisServer';
import { faCirclePlay, faHammer } from '@fortawesome/free-solid-svg-icons';

@Component({
  standalone: true,
  selector: 'jhi-navbar',
  templateUrl: './navbar.component.html',
  styleUrls: ['./navbar.component.scss'],
  imports: [RouterModule, SharedModule, HasAnyAuthorityDirective],
})
export default class NavbarComponent implements OnInit {
  faHammer = faHammer;
  faCirclePlay = faCirclePlay;

  inProduction?: boolean;
  isNavbarCollapsed = true;
  openAPIEnabled?: boolean;
  version = '';
  account: Account | null = null;
  entitiesNavbarItems: NavbarItem[] = [];
  availableServers = [ArtemisServer.TS1, ArtemisServer.TS3, ArtemisServer.PRODUCTION, ArtemisServer.STAGING, ArtemisServer.STAGING2];

  constructor(
    private loginService: LoginService,
    private accountService: AccountService,
    private profileService: ProfileService,
    private router: Router,
  ) {
    /* eslint-disable @typescript-eslint/no-unnecessary-condition */
    if (VERSION) {
      this.version = VERSION.toLowerCase().startsWith('v') ? VERSION : `v${VERSION}`;
    }
  }

  ngOnInit(): void {
    this.entitiesNavbarItems = EntityNavbarItems;
    this.profileService.getProfileInfo().subscribe(profileInfo => {
      this.inProduction = profileInfo.inProduction;
      this.openAPIEnabled = profileInfo.openAPIEnabled;
    });

    this.accountService.getAuthenticationState().subscribe(account => {
      this.account = account;
    });

    this.profileService.getProfileInfo().subscribe(profileInfo => {
      if (profileInfo.inProduction && this.availableServers.includes(ArtemisServer.LOCAL)) {
        const index = this.availableServers.indexOf(ArtemisServer.LOCAL);
        this.availableServers.splice(index, 1);
      } else if (!profileInfo.inProduction && !this.availableServers.includes(ArtemisServer.LOCAL)) {
        this.availableServers.push(ArtemisServer.LOCAL);
      }
    });
  }

  collapseNavbar(): void {
    this.isNavbarCollapsed = true;
  }

  login(): void {
    this.router.navigate(['/login']);
  }

  logout(): void {
    this.collapseNavbar();
    this.loginService.logout();
    this.router.navigate(['']);
  }

  toggleNavbar(): void {
    this.isNavbarCollapsed = !this.isNavbarCollapsed;
  }
}
