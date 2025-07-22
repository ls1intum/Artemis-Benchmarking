import { Component, OnInit, inject, signal } from '@angular/core';
import { ArtemisServer } from '../core/util/artemisServer';
import { ActivatedRoute, Router } from '@angular/router';
import { ArtemisUser } from '../entities/artemis-user/artemisUser';
import { ArtemisUsersService } from './artemis-users.service';
import { ArtemisUserForCreationDTO } from './artemisUserForCreationDTO';
import { NgbCollapse, NgbModal, NgbTooltipModule } from '@ng-bootstrap/ng-bootstrap';
import { CreateUserBoxComponent } from '../layouts/create-user-box/create-user-box.component';
import { faCircleInfo, faEye, faEyeSlash, faMagnifyingGlass, faSpinner } from '@fortawesome/free-solid-svg-icons';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { ArtemisUserPatternDTO } from './artemisUserPatternDTO';
import { FormControl, FormsModule, ReactiveFormsModule } from '@angular/forms';
import { Observable, Subject, map, merge, startWith } from 'rxjs';
import SharedModule from '../shared/shared.module';

@Component({
  selector: 'jhi-artemis-users',
  imports: [SharedModule, NgbCollapse, CreateUserBoxComponent, FontAwesomeModule, NgbTooltipModule, FormsModule, ReactiveFormsModule],
  templateUrl: './artemis-users.component.html',
  styleUrl: './artemis-users.component.scss',
})
export default class ArtemisUsersComponent implements OnInit {
  faEye = faEye;
  faEyeSlash = faEyeSlash;
  faCircleInfo = faCircleInfo;
  faMagnifyingGlass = faMagnifyingGlass;
  faSpinner = faSpinner;

  server: ArtemisServer = ArtemisServer.TS1;
  users: ArtemisUser[] = [];
  users$: Observable<ArtemisUser[]>;

  isCollapsed = true;
  showPasswords = false;
  editedUser?: ArtemisUser;
  adminUser?: ArtemisUser;
  adminUserEdit = signal<ArtemisUser | undefined>(undefined);
  showAdminPassword = false;
  showEditUserPassword = false;
  actionInProgress = false;
  error = signal(false);
  errorMsg = signal('');
  filter = new FormControl('', { nonNullable: true });
  usersChanged = new Subject<void>();
  loadingCreate = false;
  loadingDelete = false;

  protected readonly ArtemisServer = ArtemisServer;

  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private artemisUsersService = inject(ArtemisUsersService);
  private modalService = inject(NgbModal);

  constructor() {
    this.users$ = merge(
      this.filter.valueChanges.pipe(
        startWith(''),
        map(text => this.search(text)),
      ),
      this.usersChanged.pipe(map(() => this.search(this.filter.value))),
    );
  }

  ngOnInit(): void {
    this.route.params.subscribe(params => {
      this.server = params['server'].toUpperCase() as ArtemisServer;
      if (!Object.values(ArtemisServer).includes(this.server)) {
        this.router.navigate(['/404']);
      }
      this.artemisUsersService.getUsers(this.server).subscribe((users: ArtemisUser[]) => {
        this.users = users.filter(u => u.serverWideId !== 0).sort((a, b) => a.serverWideId - b.serverWideId);
        this.adminUser = users.find(user => user.serverWideId === 0);
        this.usersChanged.next(void 0);
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
          this.usersChanged.next(void 0);
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
    this.loadingCreate = true;
    this.artemisUsersService.createUsersFromPattern(this.server, userPatternDTO).subscribe({
      next: (users: ArtemisUser[]) => {
        this.users.push(...users);
        this.users.sort((a, b) => a.serverWideId - b.serverWideId);
        this.actionInProgress = false;
        this.loadingCreate = false;
        this.usersChanged.next(void 0);
      },
      error: error => {
        console.error('Error creating users from pattern:', error);
        let errorMessage = 'Error creating users';
        if (error.error?.errorMessage) {
          errorMessage = `${errorMessage}: ${error.error.errorMessage}`;
        }
        this.showError(errorMessage);
        this.actionInProgress = false;
        this.loadingCreate = false;
      },
    });
  }

  createUserCsv(file: File): void {
    this.actionInProgress = true;
    this.loadingCreate = true;
    this.artemisUsersService.createUsersFromCsv(this.server, file).subscribe({
      next: (users: ArtemisUser[]) => {
        this.users.push(...users);
        this.users.sort((a, b) => a.serverWideId - b.serverWideId);
        this.actionInProgress = false;
        this.loadingCreate = false;
        this.usersChanged.next(void 0);
      },
      error: () => {
        this.showError('Error creating users');
        this.actionInProgress = false;
        this.loadingCreate = false;
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
        this.usersChanged.next(void 0);
      },
      error: () => {
        this.showError('Error deleting user');
        this.actionInProgress = false;
      },
    });
  }

  deleteAll(content: any): void {
    this.actionInProgress = true;
    this.modalService.open(content, { ariaLabelledBy: 'delete-modal-title' }).result.then(
      () => {
        this.loadingDelete = true;
        this.artemisUsersService.deleteByServer(this.server).subscribe({
          next: () => {
            this.users = [];
            this.actionInProgress = false;
            this.loadingDelete = false;
            this.usersChanged.next(void 0);
          },
          error: () => {
            this.showError('Error deleting users');
            this.actionInProgress = false;
            this.loadingDelete = false;
          },
        });
      },
      () => {
        this.actionInProgress = false;
      },
    );
  }

  updateUser(): void {
    if (this.editedUser) {
      this.actionInProgress = true;
      this.artemisUsersService.updateUser(this.editedUser).subscribe({
        next: (user: ArtemisUser) => {
          this.users = this.users.map(u => (u.id === user.id ? user : u));
          this.editedUser = undefined;
          this.usersChanged.next(void 0);
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
    const adminUserEdit = this.adminUserEdit();
    if (adminUserEdit?.id !== undefined) {
      this.actionInProgress = true;
      this.artemisUsersService.updateUser(adminUserEdit).subscribe({
        next: (user: ArtemisUser) => {
          this.adminUser = user;
          this.adminUserEdit.set(undefined);
          this.actionInProgress = false;
        },
        error: () => {
          this.showError('Error updating admin user');
          this.actionInProgress = false;
        },
      });
    } else if (adminUserEdit) {
      this.actionInProgress = true;
      this.artemisUsersService.createUser(this.server, adminUserEdit).subscribe({
        next: (user: ArtemisUser) => {
          this.adminUser = user;
          this.adminUserEdit.set(undefined);
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
    const adminUserEdit = this.adminUserEdit();
    return adminUserEdit !== undefined && adminUserEdit.username.length > 0 && adminUserEdit.password.length > 0;
  }

  editAdmin(): void {
    if (this.adminUser) {
      this.adminUserEdit.set(Object.assign({}, this.adminUser));
    } else {
      this.adminUserEdit.set(new ArtemisUser(undefined, 0, '', '', this.server));
    }
  }

  showError(errorMsg: string): void {
    this.error.set(true);
    this.errorMsg.set(errorMsg);
    setTimeout(() => {
      this.error.set(false);
    }, 3000);
  }

  search(text: string): ArtemisUser[] {
    text = text.toLowerCase();
    return this.users.filter(user => user.username.toLowerCase().includes(text) || user.serverWideId.toString().includes(text));
  }
}
