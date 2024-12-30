import { Injectable } from '@angular/core';

@Injectable({
  providedIn: 'root',
})
export class ApplicationConfigService {
  getEndpointFor(api: string): string {
    return api;
  }
}
