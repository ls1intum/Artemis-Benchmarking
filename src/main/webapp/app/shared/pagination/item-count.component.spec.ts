import { ComponentFixture, TestBed } from '@angular/core/testing';

import ItemCountComponent from './item-count.component';
import { input } from '@angular/core';

describe('ItemCountComponent test', () => {
  let comp: ItemCountComponent;
  let fixture: ComponentFixture<ItemCountComponent>;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [ItemCountComponent],
    }).compileComponents();
  });

  beforeEach(() => {
    fixture = TestBed.createComponent(ItemCountComponent);
    comp = fixture.componentInstance;
  });

  describe('UI logic tests', () => {
    it('should initialize with undefined', () => {
      expect(comp.first()).toBeUndefined();
      expect(comp.second()).toBeUndefined();
      expect(comp.total()).toBeUndefined();
    });

    it('should set calculated numbers to undefined if the page value is not yet defined', () => {
      // GIVEN
      TestBed.runInInjectionContext(() => {
        comp.page = input<number | undefined>(undefined);
        comp.totalItems = input<number | undefined>(0);
        comp.itemsPerPage = input<number | undefined>(10);
      });

      // THEN
      expect(comp.first()).toBeUndefined();
      expect(comp.second()).toBeUndefined();
    });

    it('should change the content on page change', () => {
      // GIVEN
      TestBed.runInInjectionContext(() => {
        comp.page = input<number | undefined>(1);
        comp.totalItems = input<number | undefined>(100);
        comp.itemsPerPage = input<number | undefined>(10);
      });

      // THEN
      expect(comp.first()).toBe(1);
      expect(comp.second()).toBe(10);
      expect(comp.total()).toBe(100);
    });

    it('should change the content on page 2 change', () => {
      // GIVEN
      TestBed.runInInjectionContext(() => {
        comp.page = input<number | undefined>(2);
        comp.totalItems = input<number | undefined>(100);
        comp.itemsPerPage = input<number | undefined>(10);
      });

      // THEN
      expect(comp.first()).toBe(11);
      expect(comp.second()).toBe(20);
      expect(comp.total()).toBe(100);
    });

    it('should set the second number to totalItems if this is the last page which contains less than itemsPerPage items', () => {
      // GIVEN
      TestBed.runInInjectionContext(() => {
        comp.page = input<number | undefined>(2);
        comp.totalItems = input<number | undefined>(16);
        comp.itemsPerPage = input<number | undefined>(10);
      });

      // THEN
      expect(comp.first()).toBe(11);
      expect(comp.second()).toBe(16);
      expect(comp.total()).toBe(16);
    });
  });
});
