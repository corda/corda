/* tslint:disable:no-unused-variable */

import { addProviders, async, inject } from '@angular/core/testing';
import { NodeService } from './node.service';

describe('Service: Node', () => {
  beforeEach(() => {
    addProviders([NodeService]);
  });

  it('should ...',
    inject([NodeService],
      (service: NodeService) => {
        expect(service).toBeTruthy();
      }));
});
