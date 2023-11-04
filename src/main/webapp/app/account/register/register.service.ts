import { HttpClient } from '@angular/common/http';

import { ApplicationConfigService } from 'app/core/config/application-config.service';
import { Registration } from './register.model';
import { Observable } from 'rxjs';
import { Injectable } from '@angular/core';

@Injectable({ providedIn: 'root' })
export class RegisterService {
  constructor(
    private http: HttpClient,
    private applicationConfigService: ApplicationConfigService,
  ) {}

  save(registration: Registration): Observable<{}> {
    return this.http.post(this.applicationConfigService.getEndpointFor('api/register'), registration);
  }
}
