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
var NgTablePagingDirective = (function () {
    function NgTablePagingDirective() {
        this.ngTablePaging = true;
        this.tableChanged = new core_1.EventEmitter();
    }
    Object.defineProperty(NgTablePagingDirective.prototype, "config", {
        get: function () {
            return this.ngTablePaging;
        },
        set: function (value) {
            this.ngTablePaging = value;
        },
        enumerable: true,
        configurable: true
    });
    NgTablePagingDirective.prototype.onChangePage = function (event) {
        if (this.ngTablePaging) {
            this.tableChanged.emit({ paging: event });
        }
    };
    __decorate([
        core_1.Input(), 
        __metadata('design:type', Boolean)
    ], NgTablePagingDirective.prototype, "ngTablePaging", void 0);
    __decorate([
        core_1.Output(), 
        __metadata('design:type', core_1.EventEmitter)
    ], NgTablePagingDirective.prototype, "tableChanged", void 0);
    __decorate([
        core_1.Input(), 
        __metadata('design:type', Object)
    ], NgTablePagingDirective.prototype, "config", null);
    __decorate([
        core_1.HostListener('pagechanged', ['$event']), 
        __metadata('design:type', Function), 
        __metadata('design:paramtypes', [Object]), 
        __metadata('design:returntype', void 0)
    ], NgTablePagingDirective.prototype, "onChangePage", null);
    NgTablePagingDirective = __decorate([
        core_1.Directive({ selector: '[ngTablePaging]' }), 
        __metadata('design:paramtypes', [])
    ], NgTablePagingDirective);
    return NgTablePagingDirective;
}());
exports.NgTablePagingDirective = NgTablePagingDirective;
