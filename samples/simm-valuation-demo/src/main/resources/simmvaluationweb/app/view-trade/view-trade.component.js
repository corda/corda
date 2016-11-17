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
var node_service_1 = require('../node.service');
var router_1 = require('@angular/router');
var ViewTradeComponent = (function () {
    function ViewTradeComponent(nodeService, route) {
        this.nodeService = nodeService;
        this.route = route;
        this.deal = {
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
    }
    ViewTradeComponent.prototype.ngOnInit = function () {
        var _this = this;
        this.route.params.map(function (params) { return params['tradeId']; }).subscribe(function (tradeId) {
            _this.showDeal(tradeId);
        });
    };
    ViewTradeComponent.prototype.showDeal = function (tradeId) {
        var _this = this;
        this.nodeService.getDeal(tradeId)
            .then(function (deal) {
            _this.deal = deal;
        })
            .catch(function (err) {
            console.error(err);
        });
    };
    ViewTradeComponent = __decorate([
        core_1.Component({
            moduleId: module.id,
            selector: 'app-view-trade',
            templateUrl: 'view-trade.component.html',
            styleUrls: ['../app.component.css', 'view-trade.component.css'],
            providers: [node_service_1.NodeService],
            directives: [router_1.ROUTER_DIRECTIVES] // necessary for routerLink
        }), 
        __metadata('design:paramtypes', [node_service_1.NodeService, router_1.ActivatedRoute])
    ], ViewTradeComponent);
    return ViewTradeComponent;
}());
exports.ViewTradeComponent = ViewTradeComponent;
//# sourceMappingURL=view-trade.component.js.map