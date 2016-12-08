/* tslint:disable:no-unused-variable */

import { addProviders, async, inject } from '@angular/core/testing';
import { HttpWrapperService } from './http-wrapper.service';

describe('Service: HttpWrapper', () => {
  beforeEach(() => {
    addProviders([HttpWrapperService]);
  });

  it('should ...',
    inject([HttpWrapperService],
      (service: HttpWrapperService) => {
        expect(service).toBeTruthy();
      }));
});
