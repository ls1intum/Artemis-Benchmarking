import { Component, input } from '@angular/core';
import { ArtemisServer } from '../../core/util/artemisServer';
import { NgClass } from '@angular/common';

@Component({
  selector: 'jhi-server-badge',
  templateUrl: './server-badge.component.html',
  styleUrls: ['./server-badge.component.scss'],
  imports: [NgClass],
  standalone: true,
})
export class ServerBadgeComponent {
  readonly server = input<ArtemisServer>();
  protected readonly ArtemisServer = ArtemisServer;
}
