import { TestBed, inject } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { ActivatedRoute } from '@angular/router';
import { of, throwError } from 'rxjs';

import { ActivateService } from './activate.service';
import ActivateComponent from './activate.component';

describe('ActivateComponent', () => {
  let comp: ActivateComponent;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [ActivateComponent],
      providers: [
        provideHttpClient(),
        {
          provide: ActivatedRoute,
          useValue: { queryParams: of({ key: 'ABC123' }) },
        },
      ],
    })
      .overrideTemplate(ActivateComponent, '')
      .compileComponents();
  });

  beforeEach(() => {
    const fixture = TestBed.createComponent(ActivateComponent);
    comp = fixture.componentInstance;
  });

  it('calls activate.get with the key from params', inject([ActivateService], (service: ActivateService) => {
    jest.spyOn(service, 'get').mockReturnValue(of());

    comp.ngOnInit();

    expect(service.get).toHaveBeenCalledWith('ABC123');
  }));

  it('should set set success to true upon successful activation', inject([ActivateService], (service: ActivateService) => {
    jest.spyOn(service, 'get').mockReturnValue(of({}));

    comp.ngOnInit();

    expect(comp.error()).toBe(false);
    expect(comp.success()).toBe(true);
  }));

  it('should set set error to true upon activation failure', inject([ActivateService], (service: ActivateService) => {
    jest.spyOn(service, 'get').mockReturnValue(throwError(() => {}));

    comp.ngOnInit();

    expect(comp.error()).toBe(true);
    expect(comp.success()).toBe(false);
  }));
});
