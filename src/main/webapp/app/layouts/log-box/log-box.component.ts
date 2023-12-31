import { Component, Input } from '@angular/core';
import { LogMessage } from '../../entities/simulation/logMessage';

@Component({
  selector: 'jhi-log-box',
  templateUrl: './log-box.component.html',
  styleUrls: ['./log-box.component.scss'],
})
export class LogBoxComponent {
  @Input() logMessages?: LogMessage[];
}
