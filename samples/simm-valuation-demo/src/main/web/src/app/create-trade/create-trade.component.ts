import { Component, OnInit } from '@angular/core';
import { IRSService } from '../irs.service';
import { NodeService } from '../node.service';
import { Location } from '@angular/common';
import { Router } from '@angular/router';
import { HttpWrapperService } from '../http-wrapper.service';

class DealParams {
  id: string = `${100 + Math.floor((Math.random() * 900))}`;
  description: string;
  counterparty: string;
  tradeDate: string;
  convention: string = "USD_FIXED_6M_LIBOR_3M";
  startDate: string;
  endDate: string;
  buySell: string = "BUY";
  notional: string = "1000000";
  fixedRate: string = "0.015";
}

@Component({
  moduleId: module.id,
  selector: 'app-create-trade',
  templateUrl: 'create-trade.component.html',
  styleUrls: ['../app.component.css', 'create-trade.component.css'],
  providers: [IRSService, NodeService, Location]
})
export class CreateTradeComponent implements OnInit {
  dayCountBasisLookup: string[];
  deal: DealParams;
  formError: string = "";

  constructor(
    private irsService: IRSService,
    private nodeService: NodeService,
    private location: Location,
    private router: Router,
    private httpWrapperService: HttpWrapperService
  ) {
    this.dayCountBasisLookup = Object.keys(this.irsService.lookupTable);
    this.deal = new DealParams();
    this.deal.tradeDate = this.nodeService.formatDateForNode(new Date());
    this.deal.startDate = this.nodeService.formatDateForNode(new Date());
    this.deal.endDate = this.nodeService.formatDateForNode(new Date(2020, 1, 1));
    this.deal.convention = "EUR_FIXED_1Y_EURIBOR_3M";
    this.deal.description = "description";
  }

  ngOnInit() {}

  createDeal = () => {
    var that = this;
    this.httpWrapperService.putWithCounterparty("trades", this.deal)
      .toPromise().then(() => {
        this.router.navigateByUrl(`/view-trade/${this.deal.id}`);
      }).catch((error) => {
        that.formError = error;
      });
  };

}
