import { TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it } from 'vitest';

import { ParseLinks } from './parse-links.service';

describe('ParseLinks', () => {
  let service: ParseLinks;

  beforeEach(() => {
    service = TestBed.inject(ParseLinks);
  });

  it('parses every relation of a Link header into its query parameters', () => {
    const header = '</api/simulations?page=2&size=20>; rel="next", </api/simulations?page=0&size=20>; rel="first"';

    expect(service.parseAll(header)).toEqual({
      next: { page: '2', size: '20' },
      first: { page: '0', size: '20' },
    });
  });

  it('decodes URL-encoded query parameter values', () => {
    const links = service.parseAll('</api/x?query=a%20b>; rel="next"');

    expect(links['next']?.['query']).toBe('a b');
  });

  it('reduces the header to page numbers per relation', () => {
    const header = '</api/simulations?page=3&size=20>; rel="last", </api/simulations?page=1&size=20>; rel="prev"';

    expect(service.parse(header)).toEqual({ last: 3, prev: 1 });
  });

  it('rejects an empty header', () => {
    expect(() => service.parseAll('')).toThrow('input must not be of zero length');
  });

  it('rejects a header section without a relation', () => {
    expect(() => service.parseAll('</api/simulations?page=2>')).toThrow('section could not be split on ";"');
  });
});
