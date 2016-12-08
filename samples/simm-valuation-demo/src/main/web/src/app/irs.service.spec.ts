/* tslint:disable:no-unused-variable */

import { addProviders, async, inject } from '@angular/core/testing';
import { IRSService } from './irs.service';

describe('Service: IRS', () => {
  beforeEach(() => {
    addProviders([IRSService]);
  });

  it('should ...',
    inject([IRSService],
      (service: IRSService) => {
        expect(service).toBeTruthy();
      }));
});
