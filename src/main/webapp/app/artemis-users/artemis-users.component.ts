import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ArtemisServer } from '../core/util/artemisServer';
import { ActivatedRoute, Router } from '@angular/router';
import { ArtemisUser } from '../entities/artemis-user/artemisUser';
import { ArtemisUsersService } from './artemis-users.service';
import { ArtemisUserForCreationDTO } from './artemisUserForCreationDTO';
import { NgbCollapse, NgbTooltipModule } from '@ng-bootstrap/ng-bootstrap';
import { CreateUserBoxComponent } from '../layouts/create-user-box/create-user-box.component';
import { faEye, faEyeSlash } from '@fortawesome/free-solid-svg-icons';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { ArtemisUserPatternDTO } from './artemisUserPatternDTO';
import { FormsModule } from '@angular/forms';

@Component({
  selector: 'jhi-artemis-users',
  standalone: true,
  imports: [CommonModule, NgbCollapse, CreateUserBoxComponent, FontAwesomeModule, NgbTooltipModule, FormsModule],
  templateUrl: './artemis-users.component.html',
  styleUrl: './artemis-users.component.scss',
})
export class ArtemisUsersComponent implements OnInit {
  faEye = faEye;
  faEyeSlash = faEyeSlash;

  server: ArtemisServer = ArtemisServer.TS1;
  users: ArtemisUser[] = [];

  isCollapsed = true;
  showPasswords = false;
  editedUser?: ArtemisUser;
  adminUser?: ArtemisUser;
  adminUserCopy?: ArtemisUser;
  showAdminPassword = false;
  actionInProgress = false;
  error = false;
  errorMsg = '';

  protected readonly ArtemisServer = ArtemisServer;

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private artemisUsersService: ArtemisUsersService,
  ) {}

  ngOnInit(): void {
    this.route.params.subscribe(params => {
      this.server = params['server'].toUpperCase() as ArtemisServer;
      if (!Object.values(ArtemisServer).includes(this.server)) {
        this.router.navigate(['/404']);
      }
      this.artemisUsersService.getUsers(this.server).subscribe((users: ArtemisUser[]) => {
        this.users = users.filter(u => u.serverWideId !== 0).sort((a, b) => a.serverWideId - b.serverWideId);
        this.adminUser = users.find(user => user.serverWideId === 0);
      });
    });
  }

  createUser(userDTO: ArtemisUserForCreationDTO): void {
    this.actionInProgress = true;
    this.artemisUsersService.createUser(this.server, userDTO).subscribe({
      next: (user: ArtemisUser) => {
        if (user.serverWideId === 0) {
          this.adminUser = user;
        } else {
          this.users.push(user);
          this.users.sort((a, b) => a.serverWideId - b.serverWideId);
        }
        this.actionInProgress = false;
      },
      error: () => {
        this.showError('Error creating user');
        this.actionInProgress = false;
      },
    });
  }

  createUserPattern(userPatternDTO: ArtemisUserPatternDTO): void {
    this.actionInProgress = true;
    this.artemisUsersService.createUsersFromPattern(this.server, userPatternDTO).subscribe({
      next: (users: ArtemisUser[]) => {
        this.users.push(...users);
        this.users.sort((a, b) => a.serverWideId - b.serverWideId);
        this.actionInProgress = false;
      },
      error: () => {
        this.showError('Error creating users');
        this.actionInProgress = false;
      },
    });
  }

  deleteUser(user: ArtemisUser): void {
    if (user.id === undefined) {
      return;
    }
    this.actionInProgress = true;
    this.artemisUsersService.deleteById(user.id).subscribe({
      next: () => {
        this.users = this.users.filter(u => u.id !== user.id);
        this.actionInProgress = false;
      },
      error: () => {
        this.showError('Error deleting user');
        this.actionInProgress = false;
      },
    });
  }

  deleteAll(): void {
    this.actionInProgress = true;
    this.artemisUsersService.deleteByServer(this.server).subscribe({
      next: () => {
        this.users = [];
        this.actionInProgress = false;
      },
      error: () => {
        this.showError('Error deleting users');
        this.actionInProgress = false;
      },
    });
  }

  updateUser(): void {
    if (this.editedUser) {
      this.actionInProgress = true;
      this.artemisUsersService.updateUser(this.editedUser).subscribe({
        next: (user: ArtemisUser) => {
          this.users = this.users.map(u => (u.id === user.id ? user : u));
          this.editedUser = undefined;
          this.actionInProgress = false;
        },
        error: () => {
          this.showError('Error updating user');
          this.actionInProgress = false;
        },
      });
    }
  }

  updateAdminUser(): void {
    if (this.adminUserCopy?.id !== undefined) {
      this.actionInProgress = true;
      this.artemisUsersService.updateUser(this.adminUserCopy).subscribe({
        next: (user: ArtemisUser) => {
          this.adminUser = user;
          this.adminUserCopy = undefined;
          this.actionInProgress = false;
        },
        error: () => {
          this.showError('Error updating admin user');
          this.actionInProgress = false;
        },
      });
    } else if (this.adminUserCopy) {
      this.actionInProgress = true;
      this.artemisUsersService.createUser(this.server, this.adminUserCopy).subscribe({
        next: (user: ArtemisUser) => {
          this.adminUser = user;
          this.adminUserCopy = undefined;
          this.actionInProgress = false;
        },
        error: () => {
          this.showError('Error creating admin user');
          this.actionInProgress = false;
        },
      });
    }
  }

  editValid(): boolean {
    return this.editedUser !== undefined && this.editedUser.username.length > 0 && this.editedUser.password.length > 0;
  }

  adminValid(): boolean {
    return this.adminUserCopy !== undefined && this.adminUserCopy.username.length > 0 && this.adminUserCopy.password.length > 0;
  }

  editAdmin(): void {
    if (this.adminUser) {
      this.adminUserCopy = Object.assign({}, this.adminUser);
    } else {
      this.adminUserCopy = new ArtemisUser(undefined, 0, '', '', this.server);
    }
  }

  showError(errorMsg: string): void {
    this.error = true;
    this.errorMsg = errorMsg;
    setTimeout(() => {
      this.error = false;
    }, 3000);
  }
}
