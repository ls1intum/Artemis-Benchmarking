import { Component, Input } from '@angular/core';
import { ArtemisServer } from '../../core/util/artemisServer';
import { NgClass } from '@angular/common';

@Component({
  selector: 'jhi-server-badge',
  templateUrl: './server-badge.component.html',
  styleUrls: ['./server-badge.component.scss'],
  imports: [NgClass],
})
export class ServerBadgeComponent {
  @Input() server?: ArtemisServer;
  protected readonly ArtemisServer = ArtemisServer;
}
