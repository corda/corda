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
var progressConfig = {
    animate: true,
    max: 100
};
// todo: progress element conflict with bootstrap.css
// todo: need hack: replace host element with div
/* tslint:disable */
var ProgressDirective = (function () {
    function ProgressDirective() {
        this.addClass = true;
        this.bars = [];
    }
    Object.defineProperty(ProgressDirective.prototype, "max", {
        get: function () {
            return this._max;
        },
        set: function (v) {
            this._max = v;
            this.bars.forEach(function (bar) {
                bar.recalculatePercentage();
            });
        },
        enumerable: true,
        configurable: true
    });
    ProgressDirective.prototype.ngOnInit = function () {
        this.animate = this.animate !== false;
        this.max = typeof this.max === 'number' ? this.max : progressConfig.max;
    };
    ProgressDirective.prototype.addBar = function (bar) {
        if (!this.animate) {
            bar.transition = 'none';
        }
        this.bars.push(bar);
    };
    ProgressDirective.prototype.removeBar = function (bar) {
        this.bars.splice(this.bars.indexOf(bar), 1);
    };
    __decorate([
        core_1.Input(), 
        __metadata('design:type', Boolean)
    ], ProgressDirective.prototype, "animate", void 0);
    __decorate([
        core_1.HostBinding('attr.max'),
        core_1.Input(), 
        __metadata('design:type', Number)
    ], ProgressDirective.prototype, "max", null);
    __decorate([
        core_1.HostBinding('class.progress'), 
        __metadata('design:type', Boolean)
    ], ProgressDirective.prototype, "addClass", void 0);
    ProgressDirective = __decorate([
        core_1.Directive({ selector: 'bs-progress, [progress]' }), 
        __metadata('design:paramtypes', [])
    ], ProgressDirective);
    return ProgressDirective;
}());
exports.ProgressDirective = ProgressDirective;
