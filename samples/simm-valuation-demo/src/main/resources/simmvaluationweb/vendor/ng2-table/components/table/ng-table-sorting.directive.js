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
var NgTableSortingDirective = (function () {
    function NgTableSortingDirective() {
        this.sortChanged = new core_1.EventEmitter();
    }
    Object.defineProperty(NgTableSortingDirective.prototype, "config", {
        get: function () {
            return this.ngTableSorting;
        },
        set: function (value) {
            this.ngTableSorting = value;
        },
        enumerable: true,
        configurable: true
    });
    NgTableSortingDirective.prototype.onToggleSort = function (event) {
        if (event) {
            event.preventDefault();
        }
        if (this.ngTableSorting && this.column && this.column.sort !== false) {
            switch (this.column.sort) {
                case 'asc':
                    this.column.sort = 'desc';
                    break;
                case 'desc':
                    this.column.sort = '';
                    break;
                default:
                    this.column.sort = 'asc';
                    break;
            }
            this.sortChanged.emit(this.column);
        }
    };
    __decorate([
        core_1.Input(), 
        __metadata('design:type', Object)
    ], NgTableSortingDirective.prototype, "ngTableSorting", void 0);
    __decorate([
        core_1.Input(), 
        __metadata('design:type', Object)
    ], NgTableSortingDirective.prototype, "column", void 0);
    __decorate([
        core_1.Output(), 
        __metadata('design:type', core_1.EventEmitter)
    ], NgTableSortingDirective.prototype, "sortChanged", void 0);
    __decorate([
        core_1.Input(), 
        __metadata('design:type', Object)
    ], NgTableSortingDirective.prototype, "config", null);
    __decorate([
        core_1.HostListener('click', ['$event', '$target']), 
        __metadata('design:type', Function), 
        __metadata('design:paramtypes', [Object]), 
        __metadata('design:returntype', void 0)
    ], NgTableSortingDirective.prototype, "onToggleSort", null);
    NgTableSortingDirective = __decorate([
        core_1.Directive({ selector: '[ngTableSorting]' }), 
        __metadata('design:paramtypes', [])
    ], NgTableSortingDirective);
    return NgTableSortingDirective;
}());
exports.NgTableSortingDirective = NgTableSortingDirective;
