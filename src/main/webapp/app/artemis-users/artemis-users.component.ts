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

@Component({
  selector: 'jhi-artemis-users',
  standalone: true,
  imports: [CommonModule, NgbCollapse, CreateUserBoxComponent, FontAwesomeModule, NgbTooltipModule],
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
        this.users = users.sort((a, b) => a.serverWideId - b.serverWideId);
      });
    });
  }

  createUser(userDTO: ArtemisUserForCreationDTO): void {
    this.artemisUsersService.createUser(this.server, userDTO).subscribe((user: ArtemisUser) => {
      this.users.push(user);
      this.users.sort((a, b) => a.serverWideId - b.serverWideId);
    });
  }

  createUserPattern(userPatternDTO: ArtemisUserPatternDTO): void {
    this.artemisUsersService.createUsersFromPattern(this.server, userPatternDTO).subscribe((users: ArtemisUser[]) => {
      this.users.push(...users);
      this.users.sort((a, b) => a.serverWideId - b.serverWideId);
    });
  }

  deleteUser(id: number): void {
    this.artemisUsersService.deleteById(id).subscribe(() => {
      this.users = this.users.filter(user => user.id !== id);
    });
  }

  deleteAll(): void {
    this.artemisUsersService.deleteByServer(this.server).subscribe(() => {
      this.users = [];
    });
  }
}
