import { HttpClient } from '@angular/common/http';
import { ApplicationConfigService } from 'app/core/config/application-config.service';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';

@Injectable({ providedIn: 'root' })
export class PasswordResetFinishService {
  constructor(
    private http: HttpClient,
    private applicationConfigService: ApplicationConfigService,
  ) {}

  save(key: string, newPassword: string): Observable<{}> {
    return this.http.post(this.applicationConfigService.getEndpointFor('api/account/reset-password/finish'), { key, newPassword });
  }
}
