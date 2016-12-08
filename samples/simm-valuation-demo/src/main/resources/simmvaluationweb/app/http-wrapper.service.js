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
var router_1 = require('@angular/router');
var http_1 = require('@angular/http');
var environment_1 = require('./environment');
var Rx_1 = require('rxjs/Rx');
var HttpWrapperService = (function () {
    function HttpWrapperService(http, router) {
        var _this = this;
        this.http = http;
        this.router = router;
        this.newCounterparty = new core_1.EventEmitter();
        this.step = 0;
        // because components listen on newCounterparty,
        // they need to know there is a new value when view is switched
        router.events.subscribe(function (event) {
            if (event instanceof router_1.NavigationEnd) {
                _this.emitNewCounterparty();
            }
        });
    }
    //new CP events
    HttpWrapperService.prototype.emitNewCounterparty = function () {
        if (this.counterparty) {
            this.newCounterparty.emit({
                value: this.counterparty
            });
        }
    };
    // end new CP events
    // CP getter and setter
    HttpWrapperService.prototype.setCounterparty = function (cp) {
        this.counterparty = cp;
        this.emitNewCounterparty();
        return cp; //chainable
    };
    HttpWrapperService.prototype.getCounterparty = function () {
        return this.counterparty;
    };
    // end CP getter and setter
    // HTTP helpers
    HttpWrapperService.prototype.getPath = function (resource) {
        return environment_1.environment.APIPath + resource;
    };
    // end HTTP helpers
    // HTTP methods
    HttpWrapperService.prototype.getWithCounterparty = function (resource) {
        return this.http.get(this.getPath(this.counterparty + "/" + resource)).map(function (res) { return res.json(); });
    };
    HttpWrapperService.prototype.postWithCounterparty = function (resource, data) {
        return this.http.post(this.getPath(this.counterparty + "/" + resource), data).map(function (res) { return res.json(); });
    };
    HttpWrapperService.prototype.putWithCounterparty = function (resource, data) {
        return this.http.put(this.getPath(this.counterparty + "/" + resource), data).map(function (res) { return res.json(); });
    };
    HttpWrapperService.prototype.getAbsolute = function (resource) {
        return this.http.get(this.getPath(resource)).map(function (res) { return res.json(); });
    };
    HttpWrapperService.prototype.updateDelayedData = function (data) {
        if (!data.portfolio) {
            return; // data hasn't fully returned yet, don't do anything
        }
        var delayedData = {};
        if (this.step > 0) {
            delayedData.portfolio = data.portfolio;
            delayedData.portfolio.agreed = (this.step > 1);
        }
        if (this.step > 2) {
            delayedData.marketData = data.marketData;
            delayedData.marketData.agreed = (this.step > 3);
        }
        if (this.step > 4) {
            delayedData.sensitivities = data.sensitivities;
            delayedData.sensitivities.agreed = (this.step > 5);
        }
        if (this.step > 6) {
            delayedData.initialMargin = data.initialMargin;
            delayedData.initialMargin.agreed = (this.step > 7);
        }
        if (this.step > 8) {
            delayedData.confirmation = data.confirmation;
            delayedData.confirmation.agreed = (this.step > 9);
        }
        if (this.step == 10) {
            this.subscription.unsubscribe();
        }
        return delayedData;
    };
    HttpWrapperService.prototype.startDelayedTimer = function () {
        var _this = this;
        this.step = 0;
        // every x second, update data
        var timer = Rx_1.Observable.timer(1000, 2000);
        this.subscription = timer.subscribe(function (t) { _this.step++; });
    };
    HttpWrapperService.prototype.getDelayedData = function (data) {
        return this.updateDelayedData(data);
    };
    HttpWrapperService = __decorate([
        core_1.Injectable(), 
        __metadata('design:paramtypes', [http_1.Http, router_1.Router])
    ], HttpWrapperService);
    return HttpWrapperService;
}());
exports.HttpWrapperService = HttpWrapperService;
//# sourceMappingURL=http-wrapper.service.js.map