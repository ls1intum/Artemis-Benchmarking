import { Component, input } from '@angular/core';
import { Mode } from '../../entities/simulation/simulation';
import { NgbAlert } from '@ng-bootstrap/ng-bootstrap';

@Component({
  selector: 'jhi-mode-explanation',
  templateUrl: './mode-explanation.component.html',
  styleUrls: ['./mode-explanation.component.scss'],
  imports: [NgbAlert],
})
export class ModeExplanationComponent {
  mode = input<Mode>();
  cleanupEnabled = input(false);

  protected readonly Mode = Mode;
}
