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
var position_1 = require('../position');
var datepicker_component_1 = require('./datepicker.component');
// import {DatePickerInner} from './datepicker-inner';
// import {DayPicker} from './daypicker';
// import {MonthPicker} from './monthpicker';
// import {YearPicker} from './yearpicker';
var PopupOptions = (function () {
    function PopupOptions(options) {
        Object.assign(this, options);
    }
    return PopupOptions;
}());
var datePickerPopupConfig = {
    datepickerPopup: 'YYYY-MM-dd',
    currentText: 'Today',
    clearText: 'Clear',
    closeText: 'Done',
    closeOnDateSelection: true,
    showButtonBar: true,
    onOpenFocus: true
};
var PopupContainerComponent = (function () {
    function PopupContainerComponent(element, options) {
        // false positive
        /* tslint:disable:no-unused-variable */
        this.showButtonBar = true;
        this.update1 = new core_1.EventEmitter(false);
        this.element = element;
        Object.assign(this, options);
        this.classMap = { 'in': false };
        this.classMap[options.placement] = true;
    }
    PopupContainerComponent.prototype.onUpdate = function ($event) {
        console.log('update', $event);
        if ($event) {
            if ($event.toString() !== '[object Date]') {
                $event = new Date($event);
            }
            this.popupComp.activeDate = $event;
        }
    };
    PopupContainerComponent.prototype.position = function (hostEl) {
        this.display = 'block';
        this.top = '0px';
        this.left = '0px';
        var p = position_1.positionService
            .positionElements(hostEl.nativeElement, this.element.nativeElement.children[0], this.placement, false);
        this.top = p.top + 'px';
    };
    PopupContainerComponent.prototype.getText = function (key) {
        return this[key + 'Text'] || datePickerPopupConfig[key + 'Text'];
    };
    PopupContainerComponent.prototype.isDisabled = function () {
        return false;
    };
    PopupContainerComponent = __decorate([
        core_1.Component({
            selector: 'popup-container',
            events: ['update1'],
            template: "\n    <ul class=\"dropdown-menu\"\n        style=\"display: block\"\n        [ngStyle]=\"{top: top, left: left, display: display}\"\n        [ngClass]=\"classMap\">\n        <li>\n             <datepicker (cupdate)=\"onUpdate($event)\" *ngIf=\"popupComp\" [(ngModel)]=\"popupComp.cd.model\" [show-weeks]=\"true\"></datepicker>\n        </li>\n        <li *ngIf=\"showButtonBar\" style=\"padding:10px 9px 2px\">\n            <span class=\"btn-group pull-left\">\n                 <button type=\"button\" class=\"btn btn-sm btn-info\" (click)=\"select('today')\" ng-disabled=\"isDisabled('today')\">{{ getText('current') }}</button>\n                 <button type=\"button\" class=\"btn btn-sm btn-danger\" (click)=\"select(null)\">{{ getText('clear') }}</button>\n            </span>\n            <button type=\"button\" class=\"btn btn-sm btn-success pull-right\" (click)=\"close()\">{{ getText('close') }}</button>\n        </li>\n    </ul>",
            directives: [common_1.NgClass, common_1.NgStyle, datepicker_component_1.DatePickerComponent, forms_1.FORM_DIRECTIVES, common_1.CORE_DIRECTIVES],
            encapsulation: core_1.ViewEncapsulation.None
        }), 
        __metadata('design:paramtypes', [core_1.ElementRef, PopupOptions])
    ], PopupContainerComponent);
    return PopupContainerComponent;
}());
var DatePickerPopupDirective = (function () {
    function DatePickerPopupDirective(cd, viewContainerRef, renderer, loader) {
        this._isOpen = false;
        this.placement = 'bottom';
        this.cd = cd;
        this.viewContainerRef = viewContainerRef;
        this.renderer = renderer;
        this.loader = loader;
        this.activeDate = cd.model;
    }
    Object.defineProperty(DatePickerPopupDirective.prototype, "activeDate", {
        get: function () {
            return this._activeDate;
        },
        set: function (value) {
            this._activeDate = value;
        },
        enumerable: true,
        configurable: true
    });
    Object.defineProperty(DatePickerPopupDirective.prototype, "isOpen", {
        get: function () {
            return this._isOpen;
        },
        set: function (value) {
            var _this = this;
            var fn = function () {
                _this._isOpen = value;
            };
            if (value === true) {
                this.show(fn);
            }
            if (value === false) {
                this.hide(fn);
            }
        },
        enumerable: true,
        configurable: true
    });
    DatePickerPopupDirective.prototype.hide = function (cb) {
        if (this.popup) {
            this.popup.then(function (componentRef) {
                componentRef.destroy();
                cb();
                return componentRef;
            });
        }
        else {
            cb();
        }
    };
    DatePickerPopupDirective.prototype.show = function (cb) {
        var _this = this;
        var options = new PopupOptions({
            placement: this.placement
        });
        var binding = core_1.ReflectiveInjector.resolve([
            core_1.provide(PopupOptions, { useValue: options })
        ]);
        this.popup = this.loader
            .loadNextToLocation(PopupContainerComponent, this.viewContainerRef, binding)
            .then(function (componentRef) {
            componentRef.instance.position(_this.viewContainerRef);
            componentRef.instance.popupComp = _this;
            /*componentRef.instance.update1.observer({
             next: (newVal) => {
             setProperty(this.renderer, this.elementRef, 'value', newVal);
             }
             });*/
            cb();
            return componentRef;
        });
    };
    DatePickerPopupDirective = __decorate([
        core_1.Directive({
            selector: '[datepickerPopup][ngModel]',
            // prop -> datepickerPopup - format
            properties: ['datepickerPopup', 'isOpen'] /*,
             host: {'(cupdate)': 'onUpdate1($event)'}*/
        }),
        __param(0, core_1.Self()), 
        __metadata('design:paramtypes', [forms_1.NgModel, core_1.ViewContainerRef, core_1.Renderer, core_1.DynamicComponentLoader])
    ], DatePickerPopupDirective);
    return DatePickerPopupDirective;
}());
exports.DatePickerPopupDirective = DatePickerPopupDirective;
