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
/* beautify preserve:end */
var core_1 = require('@angular/core');
var http_wrapper_service_1 = require('../http-wrapper.service');
var Rx_1 = require('rxjs/Rx');
var ValuationsComponent = (function () {
    function ValuationsComponent(httpWrapperService) {
        this.httpWrapperService = httpWrapperService;
        this.data = {};
        this.formattedData = {
            sensitivitiesCurves: []
        };
        this.fullData = {};
    }
    ValuationsComponent.prototype.startCalculations = function () {
        var _this = this;
        console.log("Starting calculations");
        this.fullData = {};
        this.data = {}; // outdated data, delete it
        this.calculateClicked = true; // show loading spinners
        // demo magic - this is to ensure we use the right valuation date
        this.httpWrapperService.postWithCounterparty("portfolio/valuations/calculate", { valuationDate: "2016-06-06" })
            .toPromise().then(function (data) {
            _this.fullData = data;
            _this.businessDate = data.businessDate; // in case it's valuations for a different date now
            _this.httpWrapperService.startDelayedTimer(); // demo magic
            _this.getData();
        });
    };
    ValuationsComponent.prototype.getData = function () {
        this.data = this.httpWrapperService.getDelayedData(this.fullData);
        if (this.data && this.data.sensitivities) {
            this.formattedData.sensitivitiesCurves = this.getSensitivitiesCurves(this.data.sensitivities);
        }
        // scroll to bottom of page
        var spinners = document.getElementById("loadingSpinners");
        if (spinners) {
            setTimeout(function () {
                $("html, body").animate({ scrollTop: $(document).height() }, 1000);
            }, 100); // wait for spinners to have gone below latest element
        }
    };
    // TODO: make this independent from the actual curve names
    ValuationsComponent.prototype.getSensitivitiesCurves = function (sensitivities) {
        var formattedSensitivities = []; // formattedSensitivities
        // loop on the first curve, knowing that the other curves have the same values
        for (var key in sensitivities.curves["EUR-DSCON-BIMM"]) {
            if (sensitivities.curves["EUR-DSCON-BIMM"].hasOwnProperty(key)) {
                var obj = {
                    tenor: key,
                    "EUR-DSCON-BIMM": sensitivities.curves["EUR-DSCON-BIMM"][key],
                    "EUR-EURIBOR3M-BIMM": sensitivities.curves["EUR-EURIBOR3M-BIMM"][key]
                };
                formattedSensitivities.push(obj);
            }
        }
        return formattedSensitivities;
    };
    ValuationsComponent.prototype.ngOnInit = function () {
        var _this = this;
        this.httpWrapperService.getAbsolute("business-date").toPromise().then(function (data) {
            _this.businessDate = data.businessDate;
        }).catch(function (error) {
            console.log("Error loading business date", error);
        });
        // check for new data periodically
        // higher timeout because makes debugging annoying, put to 2000 for production
        this.timer = Rx_1.Observable.timer(0, 2000);
        this.timerSubscription = (this.timer.subscribe(function () { return _this.getData(); }));
        // but also check for new data when counterparty changes
        this.counterpartySubscription = this.httpWrapperService.newCounterparty.subscribe(function (state) {
            _this.getData();
        });
    };
    ValuationsComponent.prototype.ngOnDestroy = function () {
        this.timerSubscription.unsubscribe();
        this.counterpartySubscription.unsubscribe();
    };
    ValuationsComponent = __decorate([
        core_1.Component({
            moduleId: module.id,
            selector: 'app-valuations',
            templateUrl: 'valuations.component.html',
            styleUrls: ['valuations.component.css'],
            directives: []
        }), 
        __metadata('design:paramtypes', [http_wrapper_service_1.HttpWrapperService])
    ], ValuationsComponent);
    return ValuationsComponent;
}());
exports.ValuationsComponent = ValuationsComponent;
//# sourceMappingURL=valuations.component.js.map