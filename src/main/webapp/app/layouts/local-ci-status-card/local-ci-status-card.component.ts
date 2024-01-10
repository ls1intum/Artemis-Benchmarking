import { Component, Input } from '@angular/core';
import { LocalCIStatus } from '../../entities/simulation/localCIStatus';
import { DecimalPipe } from '@angular/common';

@Component({
  selector: 'jhi-local-ci-status-card',
  standalone: true,
  imports: [DecimalPipe],
  templateUrl: './local-ci-status-card.component.html',
  styleUrl: './local-ci-status-card.component.scss',
})
export class LocalCiStatusCardComponent {
  @Input() localCIStatus!: LocalCIStatus;
}
