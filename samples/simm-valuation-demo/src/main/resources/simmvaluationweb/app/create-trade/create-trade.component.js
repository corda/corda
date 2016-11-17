"use strict";
var __decorate = (this && this.__decorate) || function (decorators, target, key, desc) {
    var c = arguments.length, r = c < 3 ? target : desc === null ? desc = Object.getOwnPropertyDescriptor(target, key) : desc, d;
    if (typeof Reflect === "object" && typeof Reflect.decorate === "function") r = Reflect.decorate(decorators, target, key, desc);
    else for (var i = decorators.length - 1; i >= 0; i--) if (d = decorators[i]) r = (c < 3 ? d(r) : c > 3 ? d(target, key, r) : d(target, key)) || r;
    return c > 3 && r && Object.defineProperty(target, key, r), r;
};
var __metadata = (this && this.__metadata) || function (k, v) {
    if (typeof Reflect === "object" && typeof Reflect.metadata === "function") return Reflect.metadata(k, v);
};
var core_1 = require('@angular/core');
var irs_service_1 = require('../irs.service');
var node_service_1 = require('../node.service');
var common_1 = require('@angular/common');
var router_1 = require('@angular/router');
var http_wrapper_service_1 = require('../http-wrapper.service');
var DealParams = (function () {
    function DealParams() {
        this.id = "" + (100 + Math.floor((Math.random() * 900)));
        this.convention = "USD_FIXED_6M_LIBOR_3M";
        this.buySell = "BUY";
        this.notional = "1000000";
        this.fixedRate = "0.015";
    }
    return DealParams;
}());
var CreateTradeComponent = (function () {
    function CreateTradeComponent(irsService, nodeService, location, router, httpWrapperService) {
        var _this = this;
        this.irsService = irsService;
        this.nodeService = nodeService;
        this.location = location;
        this.router = router;
        this.httpWrapperService = httpWrapperService;
        this.formError = "";
        this.createDeal = function () {
            var that = _this;
            _this.httpWrapperService.putWithCounterparty("trades", _this.deal)
                .toPromise().then(function () {
                _this.router.navigateByUrl("/view-trade/" + _this.deal.id);
            }).catch(function (error) {
                that.formError = error;
            });
        };
        this.dayCountBasisLookup = Object.keys(this.irsService.lookupTable);
        this.deal = new DealParams();
        this.deal.tradeDate = this.nodeService.formatDateForNode(new Date());
        this.deal.startDate = this.nodeService.formatDateForNode(new Date());
        this.deal.endDate = this.nodeService.formatDateForNode(new Date(2020, 1, 1));
        this.deal.convention = "EUR_FIXED_1Y_EURIBOR_3M";
        this.deal.description = "description";
    }
    CreateTradeComponent.prototype.ngOnInit = function () { };
    CreateTradeComponent = __decorate([
        core_1.Component({
            moduleId: module.id,
            selector: 'app-create-trade',
            templateUrl: 'create-trade.component.html',
            styleUrls: ['../app.component.css', 'create-trade.component.css'],
            providers: [irs_service_1.IRSService, node_service_1.NodeService, common_1.Location]
        }), 
        __metadata('design:paramtypes', [irs_service_1.IRSService, node_service_1.NodeService, common_1.Location, router_1.Router, http_wrapper_service_1.HttpWrapperService])
    ], CreateTradeComponent);
    return CreateTradeComponent;
}());
exports.CreateTradeComponent = CreateTradeComponent;
//# sourceMappingURL=create-trade.component.js.map