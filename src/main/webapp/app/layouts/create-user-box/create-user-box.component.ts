import { Component, ElementRef, EventEmitter, Input, Output, ViewChild } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ArtemisUserForCreationDTO } from '../../artemis-users/artemisUserForCreationDTO';
import { FormsModule } from '@angular/forms';
import { NgbAlertModule, NgbNavModule, NgbTooltipModule } from '@ng-bootstrap/ng-bootstrap';
import { ArtemisUserPatternDTO } from '../../artemis-users/artemisUserPatternDTO';
import { faCircleInfo, faEye, faEyeSlash, faSpinner } from '@fortawesome/free-solid-svg-icons';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import SharedModule from '../../shared/shared.module';

@Component({
  selector: 'jhi-create-user-box',
  imports: [SharedModule, FormsModule, NgbNavModule, FontAwesomeModule, NgbTooltipModule, NgbAlertModule],
  templateUrl: './create-user-box.component.html',
  styleUrl: './create-user-box.component.scss',
})
export class CreateUserBoxComponent {
  faCircleInfo = faCircleInfo;
  faSpinner = faSpinner;
  faEye = faEye;
  faEyeSlash = faEyeSlash;

  @Input() actionInProgress = false;
  @Input() loading = false;
  @Input() adminAvailable = false;

  username = '';
  password = '';
  id?: number;

  usernamePattern = '';
  passwordPattern = '';
  firstNamePattern = '';
  lastNamePattern = '';
  emailPattern = '';
  isCreateOnArtemis = false;
  from = 1;
  to = 2;

  file?: File;
  @ViewChild('fileInput') fileInput?: ElementRef;

  showPassword = false;

  @Output() createUser = new EventEmitter<ArtemisUserForCreationDTO>();
  @Output() createUserPattern = new EventEmitter<ArtemisUserPatternDTO>();
  @Output() createUserCsv = new EventEmitter<File>();

  onCreate(): void {
    const user: ArtemisUserForCreationDTO = new ArtemisUserForCreationDTO(this.username, this.password, this.id);
    this.createUser.emit(user);
  }

  isValidManually(): boolean {
    /* eslint-disable @typescript-eslint/no-unnecessary-condition */
    return this.username.length > 0 && this.password.length > 0 && (this.id === undefined || this.id === null || this.id > 0);
  }

  onCreatePattern(): void {
    const userPattern: ArtemisUserPatternDTO = new ArtemisUserPatternDTO(
      this.usernamePattern,
      this.passwordPattern,
      this.from,
      this.to,
      this.isCreateOnArtemis,
      this.firstNamePattern,
      this.lastNamePattern,
      this.emailPattern,
    );
    this.createUserPattern.emit(userPattern);
  }

  isValidPattern(): boolean {
    const validForCreateOnArtemis =
      !this.isCreateOnArtemis || (this.firstNamePattern.length > 0 && this.lastNamePattern.length > 0 && this.emailPattern.length > 0);
    return (
      this.usernamePattern.length > 0 && this.passwordPattern.length > 0 && this.from > 0 && this.from < this.to && validForCreateOnArtemis
    );
  }

  onFileSelect(event: any): void {
    if (event.target.files.length === 1) {
      const file: File = event.target.files[0];
      if (file.type === 'text/csv') {
        this.file = event.target.files[0];
      }
    }
  }

  onSubmitCsv(): void {
    if (this.file) {
      this.createUserCsv.emit(this.file);
      this.file = undefined;
      if (this.fileInput) {
        this.fileInput.nativeElement.value = '';
      }
    }
  }
}
