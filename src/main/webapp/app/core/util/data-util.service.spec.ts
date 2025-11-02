import { TestBed } from '@angular/core/testing';

import { DataUtils } from './data-util.service';

describe('Data Utils Service Test', () => {
  let service: DataUtils;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [DataUtils],
    });
    service = TestBed.inject(DataUtils);
  });

  describe('byteSize', () => {
    it('should return the bytesize of the text', () => {
      expect(service.byteSize('Hello Benchmarking')).toBe(`13.5 bytes`);
    });
  });

  describe('openFile', () => {
    it('should open the file in the new window', () => {
      const newWindow = window;
      // eslint-disable-next-line @typescript-eslint/no-deprecated
      newWindow.document.write = jest.fn();
      window.open = jest.fn(() => newWindow);
      window.URL.createObjectURL = jest.fn();
      const data = 'SkhpcHN0ZXI=';
      const contentType = 'text/plain';
      service.openFile(data, contentType);
      expect(window.open).toHaveBeenCalledTimes(1);
    });
  });
});
