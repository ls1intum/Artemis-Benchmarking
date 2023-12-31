import { Component, Input } from '@angular/core';
import { Status } from '../../entities/simulation/simulationRun';
import { faCircle, faCircleCheck, faCircleNotch, faCircleXmark, faCircleExclamation } from '@fortawesome/free-solid-svg-icons';

@Component({
  selector: 'jhi-status-icon',
  templateUrl: './status-icon.component.html',
  styleUrls: ['./status-icon.component.scss'],
})
export class StatusIconComponent {
  faCircleCheck = faCircleCheck;
  faCircleXmark = faCircleXmark;
  faCircle = faCircle;
  faCircleNotch = faCircleNotch;
  faCircleExclamation = faCircleExclamation;

  @Input() status?: Status;

  protected readonly Status = Status;
}
