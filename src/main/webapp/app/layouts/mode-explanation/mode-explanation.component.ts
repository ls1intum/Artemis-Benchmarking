import { Component, Input } from '@angular/core';
import { Mode } from '../../entities/simulation/simulation';

@Component({
  selector: 'jhi-mode-explanation',
  templateUrl: './mode-explanation.component.html',
  styleUrls: ['./mode-explanation.component.scss'],
})
export class ModeExplanationComponent {
  @Input() mode?: Mode;
  @Input() cleanupEnabled: boolean = false;

  protected readonly Mode = Mode;
}
