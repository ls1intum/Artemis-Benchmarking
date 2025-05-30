import { ChangeDetectorRef } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { of } from 'rxjs';

import MetricsComponent from './metrics.component';
import { MetricsService } from './metrics.service';
import { Metrics, Thread, ThreadDump } from './metrics.model';

describe('MetricsComponent', () => {
  let comp: MetricsComponent;
  let fixture: ComponentFixture<MetricsComponent>;
  let service: MetricsService;
  let changeDetector: ChangeDetectorRef;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [MetricsComponent],
      providers: [provideHttpClient()],
    })
      .overrideTemplate(MetricsComponent, '')
      .compileComponents();
  });

  beforeEach(() => {
    fixture = TestBed.createComponent(MetricsComponent);
    comp = fixture.componentInstance;
    service = TestBed.inject(MetricsService);
    changeDetector = fixture.debugElement.injector.get(ChangeDetectorRef);
  });

  describe('refresh', () => {
    it('should call refresh on init', () => {
      // GIVEN
      const metrics = {
        garbageCollector: {
          'PS Scavenge': {
            collectionCount: 0,
            collectionTime: 0,
          },
          'PS MarkSweep': {
            collectionCount: 0,
            collectionTime: 0,
          },
        },
      } as unknown as Metrics;
      const threadDump = { threads: [{ threadName: 'thread 1' } as Thread] } as ThreadDump;

      jest.spyOn(service, 'getMetrics').mockReturnValue(of(metrics));
      jest.spyOn(service, 'threadDump').mockReturnValue(of(threadDump));
      jest.spyOn(changeDetector.constructor.prototype, 'markForCheck');

      // WHEN
      comp.ngOnInit();

      // THEN
      expect(service.getMetrics).toHaveBeenCalled();
      expect(comp.metrics()).toEqual(metrics);
      expect(comp.threads()).toEqual(threadDump.threads);
      expect(comp.updatingMetrics()).toBeFalsy();
      expect(changeDetector.constructor.prototype.markForCheck).toHaveBeenCalled();
    });
  });

  describe('metricsKeyExistsAndObjectNotEmpty', () => {
    it('should check that metrics key exists and is not empty', () => {
      // GIVEN
      comp.metrics.set({
        garbageCollector: {
          'PS Scavenge': {
            collectionCount: 0,
            collectionTime: 0,
          },
          'PS MarkSweep': {
            collectionCount: 0,
            collectionTime: 0,
          },
        },
      } as unknown as Metrics);

      // WHEN
      const garbageCollectorKeyExistsAndNotEmpty = comp.metricsKeyExistsAndObjectNotEmpty('garbageCollector');

      // THEN
      expect(garbageCollectorKeyExistsAndNotEmpty).toBeTruthy();
    });

    it('should check that metrics key is empty', () => {
      // GIVEN
      comp.metrics.set({
        garbageCollector: {},
      } as Metrics);

      // WHEN
      const garbageCollectorKeyEmpty = comp.metricsKeyExistsAndObjectNotEmpty('garbageCollector');

      // THEN
      expect(garbageCollectorKeyEmpty).toBeFalsy();
    });
  });
});
