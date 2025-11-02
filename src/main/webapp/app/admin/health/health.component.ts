import { Component, OnInit, inject } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';

import { HealthService } from './health.service';
import { Health, HEALTH_LABELS, HEALTH_STATUS_LABELS, HealthEntry, HealthStatus } from './health.model';
import SharedModule from '../../shared/shared.module';
import HealthModalComponent from './modal/health-modal.component';

@Component({
  selector: 'health',
  templateUrl: './health.component.html',
  imports: [SharedModule],
})
export default class HealthComponent implements OnInit {
  health?: Health;

  readonly HEALTH_LABELS = HEALTH_LABELS;
  readonly HEALTH_STATUS_LABELS = HEALTH_STATUS_LABELS;

  private readonly modalService = inject(NgbModal);
  private readonly healthService = inject(HealthService);

  ngOnInit(): void {
    this.refresh();
  }

  getBadgeClass(statusState: HealthStatus): string {
    if (statusState === 'UP') {
      return 'bg-success';
    }
    return 'bg-danger';
  }

  refresh(): void {
    this.healthService.checkHealth().subscribe({
      next: health => (this.health = health),
      error: (error: HttpErrorResponse) => {
        if (error.status === 503) {
          this.health = error.error;
        }
      },
    });
  }

  showHealth(health: HealthEntry): void {
    const modalRef = this.modalService.open(HealthModalComponent);
    modalRef.componentInstance.health = health;
  }
}
