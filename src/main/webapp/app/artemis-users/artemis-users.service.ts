import { inject, Injectable } from '@angular/core';
import { ApplicationConfigService } from '../core/config/application-config.service';
import { HttpClient } from '@angular/common/http';
import { ArtemisUser } from '../entities/artemis-user/artemisUser';
import { ArtemisUserPatternDTO } from './artemisUserPatternDTO';
import { ArtemisServer } from '../core/util/artemisServer';
import { map } from 'rxjs/operators';
import { Observable } from 'rxjs';
import { ArtemisUserForCreationDTO } from './artemisUserForCreationDTO';

@Injectable({
  providedIn: 'root',
})
export class ArtemisUsersService {
  private readonly httpClient = inject(HttpClient);
  private readonly applicationConfigService = inject(ApplicationConfigService);

  getUsers(server: ArtemisServer): Observable<ArtemisUser[]> {
    const endpoint = this.applicationConfigService.getEndpointFor(`/api/artemis-users/${server}`);
    return this.httpClient.get(endpoint).pipe(map((res: any) => res as ArtemisUser[]));
  }

  createUser(server: ArtemisServer, user: ArtemisUserForCreationDTO): Observable<ArtemisUser> {
    const endpoint = this.applicationConfigService.getEndpointFor(`/api/artemis-users/${server}`);
    return this.httpClient.post(endpoint, user).pipe(map((res: any) => res as ArtemisUser));
  }

  createUsersFromPattern(server: ArtemisServer, userPattern: ArtemisUserPatternDTO): Observable<ArtemisUser[]> {
    const endpoint = this.applicationConfigService.getEndpointFor(`/api/artemis-users/${server}/create-by-pattern`);
    return this.httpClient.post(endpoint, userPattern).pipe(map((res: any) => res as ArtemisUser[]));
  }

  deleteByServer(server: ArtemisServer): Observable<undefined> {
    const endpoint = this.applicationConfigService.getEndpointFor(`/api/artemis-users/${server}`);
    return this.httpClient.delete<undefined>(endpoint);
  }

  deleteById(id: number): Observable<undefined> {
    const endpoint = this.applicationConfigService.getEndpointFor(`/api/artemis-users/${id}/by-id`);
    return this.httpClient.delete<undefined>(endpoint);
  }

  updateUser(user: ArtemisUser): Observable<ArtemisUser> {
    const endpoint = this.applicationConfigService.getEndpointFor(`/api/artemis-users/${user.id}`);
    return this.httpClient.put(endpoint, user).pipe(map((res: any) => res as ArtemisUser));
  }

  createUsersFromCsv(server: ArtemisServer, file: File): Observable<ArtemisUser[]> {
    const formData = new FormData();
    formData.append('file', file);
    const endpoint = this.applicationConfigService.getEndpointFor(`/api/artemis-users/${server}/csv`);
    return this.httpClient.post(endpoint, formData).pipe(map((res: any) => res as ArtemisUser[]));
  }
}
