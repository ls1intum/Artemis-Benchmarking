import { TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it } from 'vitest';

import { SortService } from './sort.service';

describe('SortService', () => {
  let service: SortService;

  beforeEach(() => {
    service = TestBed.inject(SortService);
  });

  describe('startSort', () => {
    const rows = [{ name: 'item 10' }, { name: 'item 2' }, { name: 'Item 1' }];

    it('sorts ascending and compares numbers inside text naturally', () => {
      const sorted = [...rows].sort(service.startSort({ predicate: 'name', order: 'asc' }));

      expect(sorted.map(r => r.name)).toEqual(['Item 1', 'item 2', 'item 10']);
    });

    it('sorts descending', () => {
      const sorted = [...rows].sort(service.startSort({ predicate: 'name', order: 'desc' }));

      expect(sorted.map(r => r.name)).toEqual(['item 10', 'item 2', 'Item 1']);
    });

    it('falls back to the second criterion when the first one ties', () => {
      const data = [
        { group: 'a', id: 2 },
        { group: 'a', id: 1 },
        { group: 'b', id: 0 },
      ];

      const sorted = [...data].sort(service.startSort({ predicate: 'group', order: 'asc' }, { predicate: 'id', order: 'asc' }));

      expect(sorted.map(r => r.id)).toEqual([1, 2, 0]);
    });
  });

  describe('parseSortParam', () => {
    it('splits predicate and order', () => {
      expect(service.parseSortParam('name,desc')).toEqual({ predicate: 'name', order: 'desc' });
    });

    it('accepts a bare predicate', () => {
      expect(service.parseSortParam('name')).toEqual({ predicate: 'name' });
    });

    it('treats a missing parameter as no sorting', () => {
      expect(service.parseSortParam(undefined)).toEqual({ predicate: undefined });
    });
  });

  describe('buildSortParam', () => {
    it('combines predicate and order', () => {
      expect(service.buildSortParam({ predicate: 'name', order: 'asc' })).toEqual(['name,asc']);
    });

    it('appends the fallback criterion unless it is already the predicate', () => {
      expect(service.buildSortParam({ predicate: 'name', order: 'desc' }, 'id')).toEqual(['name,desc', 'id,asc']);
      expect(service.buildSortParam({ predicate: 'id', order: 'desc' }, 'id')).toEqual(['id,desc']);
    });

    it('yields nothing without a predicate', () => {
      expect(service.buildSortParam({})).toEqual([]);
    });
  });
});
