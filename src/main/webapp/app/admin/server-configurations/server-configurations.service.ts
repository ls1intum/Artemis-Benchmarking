import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

import { ApplicationConfigService } from 'app/core/config/application-config.service';
import { ArtemisServerConfiguration } from './server-configuration.model';

@Injectable({ providedIn: 'root' })
export class ServerConfigurationsService {
  private readonly http = inject(HttpClient);
  private readonly applicationConfigService = inject(ApplicationConfigService);

  getServerConfigurations(): Observable<ArtemisServerConfiguration[]> {
    return this.http.get<ArtemisServerConfiguration[]>(this.applicationConfigService.getEndpointFor('api/admin/server-configurations'));
  }
}
