import { Component, input } from '@angular/core';
import { CiStatus } from '../../entities/simulation/ciStatus';
import { DecimalPipe } from '@angular/common';

@Component({
  selector: 'jhi-ci-status-card',
  imports: [DecimalPipe],
  templateUrl: './ci-status-card.component.html',
  styleUrl: './ci-status-card.component.scss',
  standalone: true,
})
export class CiStatusCardComponent {
  ciStatus = input.required<CiStatus>();
}
