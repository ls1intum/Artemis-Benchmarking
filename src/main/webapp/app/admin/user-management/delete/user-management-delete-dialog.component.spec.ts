jest.mock('@ng-bootstrap/ng-bootstrap');

import { ComponentFixture, TestBed, inject } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { of } from 'rxjs';

import { UserManagementService } from '../service/user-management.service';

import UserManagementDeleteDialogComponent from './user-management-delete-dialog.component';

describe('User Management Delete Component', () => {
  let comp: UserManagementDeleteDialogComponent;
  let fixture: ComponentFixture<UserManagementDeleteDialogComponent>;
  let service: UserManagementService;
  let mockActiveModal: NgbActiveModal;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [UserManagementDeleteDialogComponent],
      providers: [provideHttpClient(), NgbActiveModal],
    })
      .overrideTemplate(UserManagementDeleteDialogComponent, '')
      .compileComponents();
  });

  beforeEach(() => {
    fixture = TestBed.createComponent(UserManagementDeleteDialogComponent);
    comp = fixture.componentInstance;
    service = TestBed.inject(UserManagementService);
    mockActiveModal = TestBed.inject(NgbActiveModal);
  });

  describe('confirmDelete', () => {
    it('Should call delete service on confirmDelete', inject([], () => {
      // GIVEN
      jest.spyOn(service, 'delete').mockReturnValue(of({}));

      // WHEN
      comp.confirmDelete('user');

      // THEN
      expect(service.delete).toHaveBeenCalledWith('user');
      expect(mockActiveModal.close).toHaveBeenCalledWith('deleted');
    }));
  });
});
