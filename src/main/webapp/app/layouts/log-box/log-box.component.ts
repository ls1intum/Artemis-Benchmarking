import { Component, input } from '@angular/core';
import { LogMessage } from '../../entities/simulation/logMessage';
import { DatePipe, NgClass } from '@angular/common';

@Component({
  selector: 'jhi-log-box',
  templateUrl: './log-box.component.html',
  styleUrls: ['./log-box.component.scss'],
  imports: [NgClass, DatePipe],
})
export class LogBoxComponent {
  logMessages = input<LogMessage[]>();
}
