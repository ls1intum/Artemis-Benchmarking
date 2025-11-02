import { Component, input } from '@angular/core';
import { ArtemisServer } from '../../core/util/artemisServer';
import { NgClass } from '@angular/common';

@Component({
  selector: 'server-badge',
  templateUrl: './server-badge.component.html',
  styleUrls: ['./server-badge.component.scss'],
  imports: [NgClass],
})
export class ServerBadgeComponent {
  server = input<ArtemisServer>();
  protected readonly ArtemisServer = ArtemisServer;
}
