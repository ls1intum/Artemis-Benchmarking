import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { faArrowUpRightFromSquare } from '@fortawesome/free-solid-svg-icons';

import SharedModule from 'app/shared/shared.module';
import { ArtemisServer } from 'app/core/util/artemisServer';
import { ServerConfigurationsService } from './server-configurations.service';
import { ArtemisServerConfiguration } from './server-configuration.model';

@Component({
  selector: 'server-configurations',
  templateUrl: './server-configurations.component.html',
  styleUrl: './server-configurations.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [SharedModule],
})
export default class ServerConfigurationsComponent implements OnInit {
  configurations = signal<ArtemisServerConfiguration[] | undefined>(undefined);
  isLoading = signal(false);
  hasError = signal(false);
  readonly faArrowUpRightFromSquare = faArrowUpRightFromSquare;

  sortedConfigurations = computed(() => {
    const configs = this.configurations() ?? [];
    return [...configs].sort((a, b) => {
      const aIndex = this.serverOrder.indexOf(a.server);
      const bIndex = this.serverOrder.indexOf(b.server);
      if (aIndex === -1 || bIndex === -1) {
        return a.server.localeCompare(b.server);
      }
      return aIndex - bIndex;
    });
  });

  private readonly serverOrder = Object.values(ArtemisServer);
  private readonly serverConfigurationsService = inject(ServerConfigurationsService);

  ngOnInit(): void {
    this.reload();
  }

  reload(): void {
    this.isLoading.set(true);
    this.hasError.set(false);
    this.serverConfigurationsService.getServerConfigurations().subscribe({
      next: configs => {
        this.configurations.set(configs);
      },
      error: () => {
        this.configurations.set([]);
        this.hasError.set(true);
      },
      complete: () => {
        this.isLoading.set(false);
      },
    });
  }

  formatEnvironmentUrl(url: string): string {
    const withoutProtocol = url.replace(/^https?:\/\//, '');
    return withoutProtocol.replace(/\/$/, '');
  }

  hasInstances(instances: string[] | undefined | null): boolean {
    return Boolean(instances?.length);
  }
}
