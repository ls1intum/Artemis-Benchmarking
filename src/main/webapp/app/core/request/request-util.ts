import { HttpParams } from '@angular/common/http';

export const createRequestOption = (req?: any): HttpParams => {
  let options: HttpParams = new HttpParams();

  if (req) {
    for (const [key, val] of Object.entries(req)) {
      // catches both undefined and null
      if (val != null) {
        const values = (Array.isArray(val) ? val : [val]).filter(v => v !== '');
        for (const value of values) {
          options = options.append(key, value);
        }
      }
    }
  }

  return options;
};
