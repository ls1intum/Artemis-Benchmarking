jest.mock('app/core/auth/account.service');

import { Component, ElementRef, WritableSignal, signal, viewChild } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';

import { AccountService } from 'app/core/auth/account.service';
import { Account } from 'app/core/auth/account.model';

import HasAnyAuthorityDirective from './has-any-authority.directive';

@Component({
  imports: [HasAnyAuthorityDirective],
  template: ` <div *jhiHasAnyAuthority="'ROLE_ADMIN'" #content></div> `,
})
class TestHasAnyAuthorityDirectiveComponent {
  content = viewChild<ElementRef>('content');
}

describe('HasAnyAuthorityDirective tests', () => {
  let mockAccountService: AccountService;
  let currentAccount: WritableSignal<Account | undefined>;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [TestHasAnyAuthorityDirectiveComponent],
      providers: [provideHttpClient(), AccountService],
    });
  });

  beforeEach(() => {
    mockAccountService = TestBed.inject(AccountService);
    currentAccount = signal<Account | undefined>({ activated: true, authorities: [] } as any);
    mockAccountService.trackCurrentAccount = jest.fn(() => currentAccount);
  });

  describe('set jhiHasAnyAuthority', () => {
    it('should show restricted content to user if user has required role', () => {
      // GIVEN
      mockAccountService.hasAnyAuthority = jest.fn(() => true);
      const fixture = TestBed.createComponent(TestHasAnyAuthorityDirectiveComponent);
      const comp = fixture.componentInstance;
      fixture.detectChanges();

      // WHEN
      fixture.detectChanges();

      // THEN
      expect(comp.content).toBeDefined();
    });

    it('should not show restricted content to user if user has not required role', () => {
      // GIVEN
      mockAccountService.hasAnyAuthority = jest.fn(() => false);
      const fixture = TestBed.createComponent(TestHasAnyAuthorityDirectiveComponent);
      const comp = fixture.componentInstance;
      fixture.detectChanges();

      // WHEN
      fixture.detectChanges();

      // THEN
      expect(comp.content()).toBeUndefined();
    });
  });

  describe('change authorities', () => {
    it('should show or not show restricted content correctly if user authorities are changing', () => {
      // GIVEN
      mockAccountService.hasAnyAuthority = jest.fn((): boolean => Boolean(currentAccount()));
      const fixture = TestBed.createComponent(TestHasAnyAuthorityDirectiveComponent);
      const comp = fixture.componentInstance;
      fixture.detectChanges();

      // WHEN
      fixture.detectChanges();

      // THEN
      expect(comp.content()).toBeDefined();

      // GIVEN
      currentAccount.set(undefined);

      // WHEN
      fixture.detectChanges();

      // THEN
      expect(comp.content()).toBeUndefined();

      // WHEN
      currentAccount.set({ activated: true, authorities: ['foo'] } as any);
      fixture.detectChanges();

      // THEN
      expect(comp.content).toBeDefined();
    });
  });
});
