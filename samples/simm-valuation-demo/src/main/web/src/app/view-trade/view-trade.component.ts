import { Component, OnInit } from '@angular/core';
import { NodeService } from '../node.service';
import { ROUTER_DIRECTIVES, ActivatedRoute } from '@angular/router';

@Component({
  moduleId: module.id,
  selector: 'app-view-trade',
  templateUrl: 'view-trade.component.html',
  styleUrls: ['../app.component.css', 'view-trade.component.css'],
  providers: [NodeService],
  directives: [ROUTER_DIRECTIVES] // necessary for routerLink
})
export class ViewTradeComponent implements OnInit {
  deal: Object = {
    fixedLeg: {
      notional: {},
      fixedRate: {},
      paymentCalendar: {}
    },
    floatingLeg: {
      notional: {},
      paymentCalendar: {},
      fixingCalendar: {}
    },
    common: {
      interestRate: {
        tenor: {}
      }
    }
  };

  constructor(private nodeService: NodeService, private route: ActivatedRoute) {

  }

  ngOnInit() {
    this.route.params.map(params => params['tradeId']).subscribe((tradeId) => {
      this.showDeal(tradeId);
    });
  }

  showDeal(tradeId: string) {
    this.nodeService.getDeal(tradeId)
      .then((deal) => {
        this.deal = deal;
      })
      .catch((err) => {
        console.error(err);
      });
  }
}
