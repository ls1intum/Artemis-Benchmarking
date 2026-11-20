import { HttpClient, provideHttpClient, withInterceptorsFromDi } from '@angular/common/http';
import { HTTP_INTERCEPTORS } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';

import { StateStorageService } from 'app/core/auth/state-storage.service';
import { AuthInterceptor } from './auth.interceptor';

describe('AuthInterceptor', () => {
  let http: HttpClient;
  let httpMock: HttpTestingController;
  let storage: StateStorageService;

  beforeEach(() => {
    localStorage.clear();
    sessionStorage.clear();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptorsFromDi()),
        provideHttpClientTesting(),
        { provide: HTTP_INTERCEPTORS, useClass: AuthInterceptor, multi: true },
      ],
    });
    http = TestBed.inject(HttpClient);
    httpMock = TestBed.inject(HttpTestingController);
    storage = TestBed.inject(StateStorageService);
  });

  afterEach(() => {
    httpMock.verify();
    localStorage.clear();
    sessionStorage.clear();
  });

  it('sends the bearer token with requests to the own API', () => {
    storage.storeAuthenticationToken('secret-token', false);

    http.get('api/simulations').subscribe();

    const request = httpMock.expectOne('api/simulations');
    expect(request.request.headers.get('Authorization')).toBe('Bearer secret-token');
    request.flush([]);
  });

  it('does not leak the token to other hosts', () => {
    storage.storeAuthenticationToken('secret-token', false);

    http.get('https://example.org/data').subscribe();

    const request = httpMock.expectOne('https://example.org/data');
    expect(request.request.headers.has('Authorization')).toBe(false);
    request.flush({});
  });

  it('sends requests without a header while nobody is logged in', () => {
    http.get('api/simulations').subscribe();

    const request = httpMock.expectOne('api/simulations');
    expect(request.request.headers.has('Authorization')).toBe(false);
    request.flush([]);
  });
});
