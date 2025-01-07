import { Component, input } from '@angular/core';
import { Status } from '../../entities/simulation/simulationRun';
import { faCircle, faCircleCheck, faCircleNotch, faCircleXmark, faCircleExclamation } from '@fortawesome/free-solid-svg-icons';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';

@Component({
  selector: 'jhi-status-icon',
  templateUrl: './status-icon.component.html',
  styleUrls: ['./status-icon.component.scss'],
  imports: [FaIconComponent],
})
export class StatusIconComponent {
  faCircleCheck = faCircleCheck;
  faCircleXmark = faCircleXmark;
  faCircle = faCircle;
  faCircleNotch = faCircleNotch;
  faCircleExclamation = faCircleExclamation;

  status = input<Status>();

  protected readonly Status = Status;
}
