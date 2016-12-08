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
var forms_1 = require('@angular/forms');
// TODO: if uncheckable, null should be set to ngModel
// if disabled, button should not be checkable
var ButtonRadioDirective = (function () {
    function ButtonRadioDirective(cd, el) {
        this.onChange = Function.prototype;
        this.onTouched = Function.prototype;
        // hack!
        this.cd = cd;
        this.el = el;
        cd.valueAccessor = this;
    }
    Object.defineProperty(ButtonRadioDirective.prototype, "isActive", {
        get: function () {
            return this.btnRadio === this.value;
        },
        enumerable: true,
        configurable: true
    });
    ButtonRadioDirective.prototype.onClick = function () {
        if (this.uncheckable && this.btnRadio === this.value) {
            return this.cd.viewToModelUpdate(void 0);
        }
        this.cd.viewToModelUpdate(this.btnRadio);
    };
    ButtonRadioDirective.prototype.ngOnInit = function () {
        this.uncheckable = typeof this.uncheckable !== 'undefined';
    };
    Object.defineProperty(ButtonRadioDirective.prototype, "value", {
        // hack view model!
        get: function () {
            return this.cd.viewModel;
        },
        set: function (value) {
            this.cd.viewModel = value;
        },
        enumerable: true,
        configurable: true
    });
    // ControlValueAccessor
    // model -> view
    ButtonRadioDirective.prototype.writeValue = function (value) {
        this.value = value;
    };
    ButtonRadioDirective.prototype.registerOnChange = function (fn) {
        this.onChange = fn;
    };
    ButtonRadioDirective.prototype.registerOnTouched = function (fn) {
        this.onTouched = fn;
    };
    __decorate([
        core_1.Input(), 
        __metadata('design:type', String)
    ], ButtonRadioDirective.prototype, "btnRadio", void 0);
    __decorate([
        core_1.Input(), 
        __metadata('design:type', Boolean)
    ], ButtonRadioDirective.prototype, "uncheckable", void 0);
    __decorate([
        core_1.HostBinding('class.active'), 
        __metadata('design:type', Boolean)
    ], ButtonRadioDirective.prototype, "isActive", null);
    __decorate([
        core_1.HostListener('click'), 
        __metadata('design:type', Function), 
        __metadata('design:paramtypes', []), 
        __metadata('design:returntype', void 0)
    ], ButtonRadioDirective.prototype, "onClick", null);
    ButtonRadioDirective = __decorate([
        core_1.Directive({ selector: '[btnRadio][ngModel]' }),
        __param(0, core_1.Self()), 
        __metadata('design:paramtypes', [forms_1.NgModel, core_1.ElementRef])
    ], ButtonRadioDirective);
    return ButtonRadioDirective;
}());
exports.ButtonRadioDirective = ButtonRadioDirective;
