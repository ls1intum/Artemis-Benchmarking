import { Component, EventEmitter, Input, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ArtemisUserForCreationDTO } from '../../artemis-users/artemisUserForCreationDTO';
import { FormsModule } from '@angular/forms';
import { NgbNavModule } from '@ng-bootstrap/ng-bootstrap';
import { ArtemisUserPatternDTO } from '../../artemis-users/artemisUserPatternDTO';

@Component({
  selector: 'jhi-create-user-box',
  standalone: true,
  imports: [CommonModule, FormsModule, NgbNavModule],
  templateUrl: './create-user-box.component.html',
  styleUrl: './create-user-box.component.scss',
})
export class CreateUserBoxComponent {
  @Input() actionInProgress = false;

  username: string = '';
  password: string = '';
  id?: number;

  usernamePattern: string = '';
  passwordPattern: string = '';
  from: number = 1;
  to: number = 2;

  @Output() createUser = new EventEmitter<ArtemisUserForCreationDTO>();
  @Output() createUserPattern = new EventEmitter<ArtemisUserPatternDTO>();

  onCreate(): void {
    const user: ArtemisUserForCreationDTO = new ArtemisUserForCreationDTO(this.username, this.password, this.id);
    this.createUser.emit(user);
  }

  isValidManually(): boolean {
    /* eslint-disable @typescript-eslint/no-unnecessary-condition */
    return this.username.length > 0 && this.password.length > 0 && (this.id === undefined || this.id === null || this.id > 0);
  }

  onCreatePattern(): void {
    const userPattern: ArtemisUserPatternDTO = new ArtemisUserPatternDTO(this.usernamePattern, this.passwordPattern, this.from, this.to);
    this.createUserPattern.emit(userPattern);
  }

  isValidPattern(): boolean {
    return this.usernamePattern.length > 0 && this.passwordPattern.length > 0 && this.from > 0 && this.from < this.to;
  }
}
