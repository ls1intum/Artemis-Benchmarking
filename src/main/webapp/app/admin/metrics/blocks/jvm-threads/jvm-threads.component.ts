import { Component, inject, signal, ChangeDetectionStrategy, ChangeDetectorRef, input, effect } from '@angular/core';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';

import SharedModule from 'app/shared/shared.module';
import { Thread, ThreadState } from 'app/admin/metrics/metrics.model';
import { MetricsModalThreadsComponent } from '../metrics-modal-threads/metrics-modal-threads.component';

@Component({
  selector: 'jhi-jvm-threads',
  templateUrl: './jvm-threads.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [SharedModule],
})
export class JvmThreadsComponent {
  threadStats = signal({
    all: 0,
    runnable: 0,
    timedWaiting: 0,
    waiting: 0,
    blocked: 0,
  });

  threads = input<Thread[]>([]);

  private readonly changeDetector = inject(ChangeDetectorRef);
  private readonly modalService = inject(NgbModal);

  constructor() {
    effect(() => this.computeThreadStats());
  }

  open(): void {
    const modalRef = this.modalService.open(MetricsModalThreadsComponent);
    modalRef.componentInstance.threads = this.threads();
  }

  private computeThreadStats(): void {
    this.threads().forEach(thread => {
      switch (thread.threadState) {
        case ThreadState.Runnable:
          this.threadStats().runnable += 1;
          break;
        case ThreadState.Waiting:
          this.threadStats().waiting += 1;
          break;
        case ThreadState.TimedWaiting:
          this.threadStats().timedWaiting += 1;
          break;
        case ThreadState.Blocked:
          this.threadStats().blocked += 1;
          break;
      }
    });

    this.threadStats().all =
      this.threadStats().runnable + this.threadStats().waiting + this.threadStats().timedWaiting + this.threadStats().blocked;
    this.changeDetector.markForCheck();
  }
}
