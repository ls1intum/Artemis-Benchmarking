import SharedModule from 'app/shared/shared.module';
import { JvmMetrics } from 'app/admin/metrics/metrics.model';
import { Component, Input } from '@angular/core';

@Component({
  standalone: true,
  selector: 'jhi-jvm-memory',
  templateUrl: './jvm-memory.component.html',
  imports: [SharedModule],
})
export class JvmMemoryComponent {
  /**
   * object containing all jvm memory metrics
   */
  @Input() jvmMemoryMetrics?: { [key: string]: JvmMetrics };

  /**
   * boolean field saying if the metrics are in the process of being updated
   */
  @Input() updating?: boolean;
}
