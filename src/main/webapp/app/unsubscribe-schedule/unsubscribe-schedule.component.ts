import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { ActivatedRoute } from '@angular/router';
import { ApplicationConfigService } from '../core/config/application-config.service';

@Component({
  selector: 'jhi-unsubscribe-schedule',
  imports: [CommonModule],
  templateUrl: './unsubscribe-schedule.component.html',
})
export default class UnsubscribeScheduleComponent implements OnInit {
  state = 'PENDING';

  private httpClient = inject(HttpClient);
  private route = inject(ActivatedRoute);
  private applicationConfigService = inject(ApplicationConfigService);

  ngOnInit(): void {
    const key = this.route.snapshot.queryParamMap.get('key') ?? undefined;
    if (key === undefined) {
      this.state = 'ERROR';
      return;
    }
    const endpoint = this.applicationConfigService.getEndpointFor('/api/public/schedules');
    this.httpClient.delete(endpoint + '?key=' + key).subscribe({
      next: () => {
        this.state = 'SUCCESS';
      },
      error: () => {
        this.state = 'ERROR';
      },
    });
  }
}
