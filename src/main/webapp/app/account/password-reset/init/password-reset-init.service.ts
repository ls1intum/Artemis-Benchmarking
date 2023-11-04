import { HttpClient } from '@angular/common/http';
import { ApplicationConfigService } from 'app/core/config/application-config.service';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';

@Injectable({ providedIn: 'root' })
export class PasswordResetInitService {
  constructor(
    private http: HttpClient,
    private applicationConfigService: ApplicationConfigService,
  ) {}

  save(mail: string): Observable<{}> {
    return this.http.post(this.applicationConfigService.getEndpointFor('api/account/reset-password/init'), mail);
  }
}
