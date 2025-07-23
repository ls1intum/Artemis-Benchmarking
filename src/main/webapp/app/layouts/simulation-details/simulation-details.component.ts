import { Component, input, computed } from '@angular/core';
import { Simulation, getTextRepresentation, Mode } from '../../entities/simulation/simulation';
import { DatePipe } from '@angular/common';
import { ServerBadgeComponent } from '../server-badge/server-badge.component';

@Component({
  selector: 'jhi-simulation-details',
  templateUrl: './simulation-details.component.html',
  styleUrls: ['./simulation-details.component.scss'],
  imports: [DatePipe, ServerBadgeComponent],
})
export class SimulationDetailsComponent {
  simulation = input.required<Simulation>();
  modeText = computed(() => getTextRepresentation(this.simulation().mode));

  protected readonly Mode = Mode;
}
