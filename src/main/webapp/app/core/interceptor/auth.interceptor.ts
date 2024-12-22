import { HttpInterceptor, HttpRequest, HttpHandler, HttpEvent } from '@angular/common/http';

import { StateStorageService } from 'app/core/auth/state-storage.service';
import { ApplicationConfigService } from '../config/application-config.service';
import { Observable } from 'rxjs';
import { Injectable } from '@angular/core';

@Injectable()
export class AuthInterceptor implements HttpInterceptor {
  constructor(
    private stateStorageService: StateStorageService,
    private applicationConfigService: ApplicationConfigService,
  ) {}

  intercept(request: HttpRequest<any>, next: HttpHandler): Observable<HttpEvent<any>> {
    const serverApiUrl = this.applicationConfigService.getEndpointFor('');

    if (!request.url || (request.url.startsWith('http') && !(serverApiUrl && request.url.startsWith(serverApiUrl)))) {
      return next.handle(request);
    }
    // NOTE: do not add the token to requests that do not expect it (e.g. authenticate or forget password)
    const allowedUrls = ['/authenticate', '/account/reset-password/init', '/account/reset-password/finish'];
    if (allowedUrls.some(url => request.url.endsWith(url))) {
      return next.handle(request);
    }

    const token: string | null = this.stateStorageService.getAuthenticationToken();
    if (token) {
      request = request.clone({
        setHeaders: {
          Authorization: `Bearer ${token}`,
        },
      });
    }
    return next.handle(request);
  }
}
