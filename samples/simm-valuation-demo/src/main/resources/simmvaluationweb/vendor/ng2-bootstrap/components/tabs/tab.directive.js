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
var tabset_component_1 = require('./tabset.component');
/* tslint:disable */
var TabDirective = (function () {
    function TabDirective(tabset) {
        this.select = new core_1.EventEmitter(false);
        this.deselect = new core_1.EventEmitter(false);
        this.removed = new core_1.EventEmitter(false);
        this.addClass = true;
        this.tabset = tabset;
        this.tabset.addTab(this);
    }
    Object.defineProperty(TabDirective.prototype, "active", {
        /** tab active state toggle */
        get: function () {
            return this._active;
        },
        set: function (active) {
            var _this = this;
            if (this.disabled && active || !active) {
                if (!active) {
                    this._active = active;
                }
                this.deselect.emit(this);
                return;
            }
            this._active = active;
            this.select.emit(this);
            this.tabset.tabs.forEach(function (tab) {
                if (tab !== _this) {
                    tab.active = false;
                }
            });
        },
        enumerable: true,
        configurable: true
    });
    TabDirective.prototype.ngOnInit = function () { this.removable = !!this.removable; };
    TabDirective.prototype.ngOnDestroy = function () {
        this.tabset.removeTab(this);
    };
    __decorate([
        core_1.Input(), 
        __metadata('design:type', String)
    ], TabDirective.prototype, "heading", void 0);
    __decorate([
        core_1.Input(), 
        __metadata('design:type', Boolean)
    ], TabDirective.prototype, "disabled", void 0);
    __decorate([
        core_1.Input(), 
        __metadata('design:type', Boolean)
    ], TabDirective.prototype, "removable", void 0);
    __decorate([
        core_1.HostBinding('class.active'),
        core_1.Input(), 
        __metadata('design:type', Boolean)
    ], TabDirective.prototype, "active", null);
    __decorate([
        core_1.Output(), 
        __metadata('design:type', core_1.EventEmitter)
    ], TabDirective.prototype, "select", void 0);
    __decorate([
        core_1.Output(), 
        __metadata('design:type', core_1.EventEmitter)
    ], TabDirective.prototype, "deselect", void 0);
    __decorate([
        core_1.Output(), 
        __metadata('design:type', core_1.EventEmitter)
    ], TabDirective.prototype, "removed", void 0);
    __decorate([
        core_1.HostBinding('class.tab-pane'), 
        __metadata('design:type', Boolean)
    ], TabDirective.prototype, "addClass", void 0);
    TabDirective = __decorate([
        core_1.Directive({ selector: 'tab, [tab]' }), 
        __metadata('design:paramtypes', [tabset_component_1.TabsetComponent])
    ], TabDirective);
    return TabDirective;
}());
exports.TabDirective = TabDirective;
