import { AccountService } from 'app/core/auth/account.service';
import { AppPageTitleStrategy } from 'app/app-page-title-strategy';
import { Component, OnInit } from '@angular/core';

@Component({
  selector: 'jhi-main',
  templateUrl: './main.component.html',
  providers: [AppPageTitleStrategy],
})
export default class MainComponent implements OnInit {
  constructor(private accountService: AccountService) {}

  ngOnInit(): void {
    // try to log in automatically
    this.accountService.identity().subscribe();
  }
}
