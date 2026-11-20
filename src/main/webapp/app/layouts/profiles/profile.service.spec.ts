import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';

import { InfoResponse, ProfileInfo } from './profile-info.model';
import { ProfileService } from './profile.service';

describe('ProfileService', () => {
  let service: ProfileService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    service = TestBed.inject(ProfileService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  function respondWith(response: InfoResponse): ProfileInfo[] {
    const received: ProfileInfo[] = [];
    service.getProfileInfo().subscribe(info => received.push(info));
    httpMock.expectOne(req => req.url.endsWith('management/info')).flush(response);
    return received;
  }

  it('recognises the production and API documentation profiles', () => {
    const [info] = respondWith({ activeProfiles: ['prod', 'api-docs'] });

    expect(info.inProduction).toBe(true);
    expect(info.openAPIEnabled).toBe(true);
    expect(info.activeProfiles).toEqual(['prod', 'api-docs']);
  });

  it('is neither production nor documenting the API without those profiles', () => {
    const [info] = respondWith({ activeProfiles: ['dev'] });

    expect(info.inProduction).toBe(false);
    expect(info.openAPIEnabled).toBe(false);
  });

  it('shows the ribbon for the first active profile that is configured for it', () => {
    const [info] = respondWith({ activeProfiles: ['prod', 'dev'], 'display-ribbon-on-profiles': 'dev,staging' });

    expect(info.ribbonEnv).toBe('dev');
  });

  it('shows no ribbon when none of the configured profiles is active', () => {
    const [info] = respondWith({ activeProfiles: ['prod'], 'display-ribbon-on-profiles': 'dev' });

    expect(info.ribbonEnv).toBeUndefined();
  });

  it('falls back to default profile info when the info endpoint is unavailable', () => {
    const received: ProfileInfo[] = [];
    service.getProfileInfo().subscribe(info => received.push(info));

    httpMock.expectOne(req => req.url.endsWith('management/info')).error(new ProgressEvent('error'));

    expect(received).toHaveLength(1);
    expect(received[0].inProduction).toBe(false);
  });

  it('requests the info endpoint only once for several subscribers', () => {
    service.getProfileInfo().subscribe();
    service.getProfileInfo().subscribe();

    httpMock.expectOne(req => req.url.endsWith('management/info')).flush({ activeProfiles: [] });
  });
});
