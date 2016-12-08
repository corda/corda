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
var curLoading = {};
var load = function (type, promise) {
    curLoading[type] = true;
    return promise.then(function (arg) {
        curLoading[type] = false;
        return arg;
    }, function (arg) {
        curLoading[type] = false;
        throw arg;
    });
};
var http_wrapper_service_1 = require('./http-wrapper.service');
var NodeService = (function () {
    function NodeService(httpWrapperService) {
        var _this = this;
        this.httpWrapperService = httpWrapperService;
        this.formatDateForNode = function (date) {
            // Produces yyyy-dd-mm. JS is missing proper date formatting libs
            var day = ("0" + (date.getDate())).slice(-2);
            var month = ("0" + (date.getMonth() + 1)).slice(-2);
            return date.getFullYear() + "-" + month + "-" + day;
        };
        this.getDeal = function (dealId) {
            return load('deal' + dealId, _this.httpWrapperService.getWithCounterparty('trades/' + dealId).toPromise())
                .then(function (resp) {
                // Do some data modification to simplify the model
                var deal = resp;
                deal.fixedLeg.fixedRate.value = (deal.fixedLeg.fixedRate.value * 100).toString().slice(0, 6);
                return deal;
            });
        };
    }
    NodeService = __decorate([
        core_1.Injectable(), 
        __metadata('design:paramtypes', [http_wrapper_service_1.HttpWrapperService])
    ], NodeService);
    return NodeService;
}());
exports.NodeService = NodeService;
//# sourceMappingURL=node.service.js.map