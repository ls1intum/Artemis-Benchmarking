import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { ActivatedRoute } from '@angular/router';
import { ApplicationConfigService } from '../core/config/application-config.service';

@Component({
  selector: 'jhi-unsubscribe-schedule',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './unsubscribe-schedule.component.html',
})
export class UnsubscribeScheduleComponent implements OnInit {
  state = 'PENDING';

  constructor(
    private httpClient: HttpClient,
    private route: ActivatedRoute,
    private applicationConfigService: ApplicationConfigService,
  ) {}

  ngOnInit(): void {
    const key = this.route.snapshot.queryParamMap.get('key');
    if (key === null) {
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
