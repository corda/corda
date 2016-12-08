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
var __param = (this && this.__param) || function (paramIndex, decorator) {
    return function (target, key) { decorator(target, key, paramIndex); }
};
var core_1 = require('@angular/core');
var common_1 = require('@angular/common');
var forms_1 = require('@angular/forms');
var lang_1 = require('@angular/core/src/facade/lang');
/* tslint:disable */
var KeyboardEvent = lang_1.global.KeyboardEvent;
/* tslint:enable */
var RatingComponent = (function () {
    function RatingComponent(cd) {
        this.onHover = new core_1.EventEmitter(false);
        this.onLeave = new core_1.EventEmitter(false);
        this.onChange = Function.prototype;
        this.onTouched = Function.prototype;
        this.cd = cd;
        cd.valueAccessor = this;
    }
    RatingComponent.prototype.onKeydown = function (event) {
        if ([37, 38, 39, 40].indexOf(event.which) === -1) {
            return;
        }
        event.preventDefault();
        event.stopPropagation();
        var sign = event.which === 38 || event.which === 39 ? 1 : -1;
        this.rate(this.value + sign);
    };
    RatingComponent.prototype.ngOnInit = function () {
        this.max = typeof this.max !== 'undefined' ? this.max : 5;
        this.readonly = this.readonly === true;
        this.stateOn = typeof this.stateOn !== 'undefined'
            ? this.stateOn
            : 'glyphicon-star';
        this.stateOff = typeof this.stateOff !== 'undefined'
            ? this.stateOff
            : 'glyphicon-star-empty';
        this.titles = typeof this.titles !== 'undefined' && this.titles.length > 0
            ? this.titles
            : ['one', 'two', 'three', 'four', 'five'];
        this.range = this.buildTemplateObjects(this.ratingStates, this.max);
    };
    // model -> view
    RatingComponent.prototype.writeValue = function (value) {
        if (value % 1 !== value) {
            this.value = Math.round(value);
            this.preValue = value;
            return;
        }
        this.preValue = value;
        this.value = value;
    };
    RatingComponent.prototype.enter = function (value) {
        if (!this.readonly) {
            this.value = value;
            this.onHover.emit(value);
        }
    };
    RatingComponent.prototype.reset = function () {
        this.value = this.preValue;
        this.onLeave.emit(this.value);
    };
    RatingComponent.prototype.registerOnChange = function (fn) { this.onChange = fn; };
    RatingComponent.prototype.registerOnTouched = function (fn) { this.onTouched = fn; };
    RatingComponent.prototype.buildTemplateObjects = function (ratingStates, max) {
        ratingStates = ratingStates || [];
        var count = ratingStates.length || max;
        var result = [];
        for (var i = 0; i < count; i++) {
            result.push(Object.assign({
                index: i,
                stateOn: this.stateOn,
                stateOff: this.stateOff,
                title: this.titles[i] || i + 1
            }, ratingStates[i] || {}));
        }
        return result;
    };
    RatingComponent.prototype.rate = function (value) {
        if (!this.readonly && value >= 0 && value <= this.range.length) {
            this.writeValue(value);
            this.cd.viewToModelUpdate(value);
        }
    };
    __decorate([
        core_1.Input(), 
        __metadata('design:type', Number)
    ], RatingComponent.prototype, "max", void 0);
    __decorate([
        core_1.Input(), 
        __metadata('design:type', String)
    ], RatingComponent.prototype, "stateOn", void 0);
    __decorate([
        core_1.Input(), 
        __metadata('design:type', String)
    ], RatingComponent.prototype, "stateOff", void 0);
    __decorate([
        core_1.Input(), 
        __metadata('design:type', Boolean)
    ], RatingComponent.prototype, "readonly", void 0);
    __decorate([
        core_1.Input(), 
        __metadata('design:type', Array)
    ], RatingComponent.prototype, "titles", void 0);
    __decorate([
        core_1.Input(), 
        __metadata('design:type', Array)
    ], RatingComponent.prototype, "ratingStates", void 0);
    __decorate([
        core_1.Output(), 
        __metadata('design:type', core_1.EventEmitter)
    ], RatingComponent.prototype, "onHover", void 0);
    __decorate([
        core_1.Output(), 
        __metadata('design:type', core_1.EventEmitter)
    ], RatingComponent.prototype, "onLeave", void 0);
    __decorate([
        core_1.HostListener('keydown', ['$event']), 
        __metadata('design:type', Function), 
        __metadata('design:paramtypes', [Object]), 
        __metadata('design:returntype', void 0)
    ], RatingComponent.prototype, "onKeydown", null);
    RatingComponent = __decorate([
        core_1.Component({
            /* tslint:disable */
            selector: 'rating[ngModel]',
            /* tslint:enable */
            directives: [common_1.NgFor],
            template: "\n    <span (mouseleave)=\"reset()\" (keydown)=\"onKeydown($event)\" tabindex=\"0\" role=\"slider\" aria-valuemin=\"0\" [attr.aria-valuemax]=\"range.length\" [attr.aria-valuenow]=\"value\">\n      <template ngFor let-r [ngForOf]=\"range\" let-index=\"index\">\n        <span class=\"sr-only\">({{ index < value ? '*' : ' ' }})</span>\n        <i (mouseenter)=\"enter(index + 1)\" (click)=\"rate(index + 1)\" class=\"glyphicon\" [ngClass]=\"index < value ? r.stateOn : r.stateOff\" [title]=\"r.title\" ></i>\n      </template>\n    </span>\n  "
        }),
        __param(0, core_1.Self()), 
        __metadata('design:paramtypes', [forms_1.NgModel])
    ], RatingComponent);
    return RatingComponent;
}());
exports.RatingComponent = RatingComponent;
