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
var common_1 = require('@angular/common');
var ng2_select_1 = require('ng2-select/ng2-select');
var http_wrapper_service_1 = require('./http-wrapper.service');
var AppComponent = (function () {
    function AppComponent(httpWrapperService) {
        this.httpWrapperService = httpWrapperService;
        this.counterParties = [];
        this.counterparty = null;
    }
    AppComponent.prototype.selected = function (value) { };
    ;
    AppComponent.prototype.refreshValue = function (value) {
        this.counterparty = this.httpWrapperService.setCounterparty(value.id);
    };
    AppComponent.prototype.renderX500Name = function (x500Name) {
        var name = x500Name;
        x500Name.split(',').forEach(function (element) {
            var keyValue = element.split('=');
            if (keyValue[0].toUpperCase() == 'O') {
                name = keyValue[1];
            }
        });
        return name;
    };
    AppComponent.prototype.ngOnInit = function () {
        var _this = this;
        this.httpWrapperService.getAbsolute("whoami").toPromise().then(function (data) {
            _this.whoAmI = _this.renderX500Name(data.self.text);
            _this.counterParties = data.counterparties.map(function (x) {
                return {
                    id: x.id,
                    text: _this.renderX500Name(x.text)
                };
            });
            if (_this.counterParties.length == 0) {
                console.log("/whoami is returning no counterparties, the whole app won't run", data);
            }
        }).catch(function (error) {
            console.log("Error loading who am i (this is really bad, the whole app will not work)", error);
        });
    };
    AppComponent = __decorate([
        core_1.Component({
            moduleId: module.id,
            selector: 'app-root',
            templateUrl: 'app.component.html',
            styleUrls: ['app.component.css', '../vendor/ng2-select/components/css/ng2-select.css'],
            directives: [
                router_1.ROUTER_DIRECTIVES,
                common_1.NgClass,
                ng2_select_1.SELECT_DIRECTIVES
            ],
            encapsulation: core_1.ViewEncapsulation.None,
            providers: [http_wrapper_service_1.HttpWrapperService] // don't declare in children, so that it's a "singleton"
        }), 
        __metadata('design:paramtypes', [http_wrapper_service_1.HttpWrapperService])
    ], AppComponent);
    return AppComponent;
}());
exports.AppComponent = AppComponent;
//# sourceMappingURL=app.component.js.map