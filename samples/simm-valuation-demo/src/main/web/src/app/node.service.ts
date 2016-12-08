import { Injectable } from '@angular/core';
import { Deal } from './Deal'
import { Observable } from 'rxjs/Rx';
let curLoading = {};

let load = (type, promise) => {
  curLoading[type] = true;
  return promise.then((arg) => {
    curLoading[type] = false;
    return arg;
  }, (arg) => {
    curLoading[type] = false;
    throw arg;
  });
};
import { HttpWrapperService } from './http-wrapper.service';

@Injectable()
export class NodeService {
  constructor(private httpWrapperService: HttpWrapperService) {}

  formatDateForNode: Function = (date) => {
    // Produces yyyy-dd-mm. JS is missing proper date formatting libs
    let day = ("0" + (date.getDate())).slice(-2);
    let month = ("0" + (date.getMonth() + 1)).slice(-2);
    return `${date.getFullYear()}-${month}-${day}`;
  };

  getDeal: Function = (dealId) => {
    return load('deal' + dealId, this.httpWrapperService.getWithCounterparty('trades/' + dealId).toPromise())
      .then((resp) => {
        // Do some data modification to simplify the model
        let deal = resp;
        deal.fixedLeg.fixedRate.value = (deal.fixedLeg.fixedRate.value * 100).toString().slice(0, 6);
        return deal;
      });
  };
}
