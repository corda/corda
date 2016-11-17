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
var forms_1 = require('@angular/forms');
var typeahead_utils_1 = require('./typeahead-utils');
var typeahead_container_component_1 = require('./typeahead-container.component');
var typeahead_options_class_1 = require('./typeahead-options.class');
var Observable_1 = require('rxjs/Observable');
require('rxjs/add/observable/from');
require('rxjs/add/operator/debounceTime');
require('rxjs/add/operator/filter');
require('rxjs/add/operator/map');
require('rxjs/add/operator/mergeMap');
require('rxjs/add/operator/toArray');
var lang_1 = require('@angular/core/src/facade/lang');
/* tslint:disable */
var KeyboardEvent = lang_1.global.KeyboardEvent;
/* tslint:enable */
var TypeaheadDirective = (function () {
    function TypeaheadDirective(control, viewContainerRef, element, renderer, loader) {
        this.typeaheadLoading = new core_1.EventEmitter(false);
        this.typeaheadNoResults = new core_1.EventEmitter(false);
        this.typeaheadOnSelect = new core_1.EventEmitter(false);
        this.typeaheadMinLength = void 0;
        this.typeaheadAsync = void 0;
        this.typeaheadLatinize = true;
        this.typeaheadSingleWords = true;
        this.typeaheadWordDelimiters = ' ';
        this.typeaheadPhraseDelimiters = '\'"';
        this.isTypeaheadOptionsListActive = false;
        this.keyUpEventEmitter = new core_1.EventEmitter();
        this.placement = 'bottom-left';
        this.element = element;
        this.ngControl = control;
        this.viewContainerRef = viewContainerRef;
        this.renderer = renderer;
        this.loader = loader;
    }
    TypeaheadDirective.prototype.onChange = function (e) {
        if (this.container) {
            // esc
            if (e.keyCode === 27) {
                this.hide();
                return;
            }
            // up
            if (e.keyCode === 38) {
                this.container.prevActiveMatch();
                return;
            }
            // down
            if (e.keyCode === 40) {
                this.container.nextActiveMatch();
                return;
            }
            // enter
            if (e.keyCode === 13) {
                this.container.selectActiveMatch();
                return;
            }
        }
        if (e.target.value.trim().length >= this.typeaheadMinLength) {
            this.typeaheadLoading.emit(true);
            this.keyUpEventEmitter.emit(e.target.value);
        }
        else {
            this.typeaheadLoading.emit(false);
            this.typeaheadNoResults.emit(false);
            this.hide();
        }
    };
    TypeaheadDirective.prototype.onFocus = function () {
        if (this.typeaheadMinLength === 0) {
            this.typeaheadLoading.emit(true);
            this.keyUpEventEmitter.emit('');
        }
    };
    TypeaheadDirective.prototype.onBlur = function () {
        if (this.container && !this.container.isFocused) {
            this.hide();
        }
    };
    TypeaheadDirective.prototype.onKeydown = function (e) {
        // no container - no problems
        if (!this.container) {
            return;
        }
        // if items is visible - prevent form submition
        if (e.keyCode === 13) {
            e.preventDefault();
            return;
        }
        // if tab default browser behavior will select next input field, and therefore we should close the items list
        if (e.keyCode === 9) {
            this.hide();
            return;
        }
    };
    TypeaheadDirective.prototype.ngOnInit = function () {
        this.typeaheadOptionsLimit = this.typeaheadOptionsLimit || 20;
        this.typeaheadMinLength = this.typeaheadMinLength === void 0 ? 1 : this.typeaheadMinLength;
        this.typeaheadWaitMs = this.typeaheadWaitMs || 0;
        // async should be false in case of array
        if (this.typeaheadAsync === undefined && !(this.typeahead instanceof Observable_1.Observable)) {
            this.typeaheadAsync = false;
        }
        if (this.typeahead instanceof Observable_1.Observable) {
            this.typeaheadAsync = true;
        }
        if (this.typeaheadAsync) {
            this.asyncActions();
        }
        else {
            this.syncActions();
        }
    };
    TypeaheadDirective.prototype.changeModel = function (value) {
        var valueStr = ((typeof value === 'object' && this.typeaheadOptionField)
            ? value[this.typeaheadOptionField]
            : value).toString();
        this.ngControl.viewToModelUpdate(valueStr);
        this.ngControl.control.updateValue(valueStr);
        this.hide();
    };
    Object.defineProperty(TypeaheadDirective.prototype, "matches", {
        get: function () {
            return this._matches;
        },
        enumerable: true,
        configurable: true
    });
    TypeaheadDirective.prototype.show = function (matches) {
        var _this = this;
        var options = new typeahead_options_class_1.TypeaheadOptions({
            typeaheadRef: this,
            placement: this.placement,
            animation: false
        });
        var binding = core_1.ReflectiveInjector.resolve([
            core_1.provide(typeahead_options_class_1.TypeaheadOptions, { useValue: options })
        ]);
        this.popup = this.loader
            .loadNextToLocation(typeahead_container_component_1.TypeaheadContainerComponent, this.viewContainerRef, binding)
            .then(function (componentRef) {
            componentRef.instance.position(_this.viewContainerRef.element);
            _this.container = componentRef.instance;
            _this.container.parent = _this;
            // This improves the speedas it won't have to be done for each list item
            var normalizedQuery = (_this.typeaheadLatinize
                ? typeahead_utils_1.TypeaheadUtils.latinize(_this.ngControl.control.value)
                : _this.ngControl.control.value).toString()
                .toLowerCase();
            _this.container.query = _this.typeaheadSingleWords
                ? typeahead_utils_1.TypeaheadUtils.tokenize(normalizedQuery, _this.typeaheadWordDelimiters, _this.typeaheadPhraseDelimiters)
                : normalizedQuery;
            _this.container.matches = matches;
            _this.container.field = _this.typeaheadOptionField;
            _this.element.nativeElement.focus();
            return componentRef;
        });
    };
    TypeaheadDirective.prototype.hide = function () {
        var _this = this;
        if (this.container) {
            this.popup.then(function (componentRef) {
                componentRef.destroy();
                _this.container = void 0;
                return componentRef;
            });
        }
    };
    TypeaheadDirective.prototype.asyncActions = function () {
        var _this = this;
        this.keyUpEventEmitter
            .debounceTime(this.typeaheadWaitMs)
            .mergeMap(function () { return _this.typeahead; })
            .subscribe(function (matches) {
            _this._matches = matches.slice(0, _this.typeaheadOptionsLimit);
            _this.finalizeAsyncCall();
        }, function (err) {
            console.error(err);
        });
    };
    TypeaheadDirective.prototype.syncActions = function () {
        var _this = this;
        this.keyUpEventEmitter
            .debounceTime(this.typeaheadWaitMs)
            .mergeMap(function (value) {
            var normalizedQuery = _this.normalizeQuery(value);
            return Observable_1.Observable.from(_this.typeahead)
                .filter(function (option) {
                return option && _this.testMatch(_this.prepareOption(option).toLowerCase(), normalizedQuery);
            })
                .toArray();
        })
            .subscribe(function (matches) {
            _this._matches = matches.slice(0, _this.typeaheadOptionsLimit);
            _this.finalizeAsyncCall();
        }, function (err) {
            console.error(err);
        });
    };
    TypeaheadDirective.prototype.prepareOption = function (option) {
        var match;
        if (typeof option === 'object' &&
            option[this.typeaheadOptionField]) {
            match = this.typeaheadLatinize ?
                typeahead_utils_1.TypeaheadUtils.latinize(option[this.typeaheadOptionField].toString()) :
                option[this.typeaheadOptionField].toString();
        }
        if (typeof option === 'string') {
            match = this.typeaheadLatinize ?
                typeahead_utils_1.TypeaheadUtils.latinize(option.toString()) :
                option.toString();
        }
        return match;
    };
    TypeaheadDirective.prototype.normalizeQuery = function (value) {
        // If singleWords, break model here to not be doing extra work on each iteration
        var normalizedQuery = (this.typeaheadLatinize ? typeahead_utils_1.TypeaheadUtils.latinize(value) : value)
            .toString()
            .toLowerCase();
        normalizedQuery = this.typeaheadSingleWords ?
            typeahead_utils_1.TypeaheadUtils.tokenize(normalizedQuery, this.typeaheadWordDelimiters, this.typeaheadPhraseDelimiters) :
            normalizedQuery;
        return normalizedQuery;
    };
    TypeaheadDirective.prototype.testMatch = function (match, test) {
        var spaceLength;
        if (typeof test === 'object') {
            spaceLength = test.length;
            for (var i = 0; i < spaceLength; i += 1) {
                if (test[i].length > 0 && match.indexOf(test[i]) < 0) {
                    return false;
                }
            }
            return true;
        }
        else {
            return match.indexOf(test) >= 0;
        }
    };
    TypeaheadDirective.prototype.finalizeAsyncCall = function () {
        this.typeaheadLoading.emit(false);
        this.typeaheadNoResults.emit(this.matches.length <= 0);
        if (this._matches.length <= 0) {
            this.hide();
            return;
        }
        if (this.container && this._matches.length > 0) {
            // This improves the speedas it won't have to be done for each list item
            var normalizedQuery = (this.typeaheadLatinize
                ? typeahead_utils_1.TypeaheadUtils.latinize(this.ngControl.control.value)
                : this.ngControl.control.value).toString()
                .toLowerCase();
            this.container.query = this.typeaheadSingleWords
                ? typeahead_utils_1.TypeaheadUtils.tokenize(normalizedQuery, this.typeaheadWordDelimiters, this.typeaheadPhraseDelimiters)
                : normalizedQuery;
            this.container.matches = this._matches;
        }
        if (!this.container && this._matches.length > 0) {
            this.show(this._matches);
        }
    };
    __decorate([
        core_1.Output(), 
        __metadata('design:type', core_1.EventEmitter)
    ], TypeaheadDirective.prototype, "typeaheadLoading", void 0);
    __decorate([
        core_1.Output(), 
        __metadata('design:type', core_1.EventEmitter)
    ], TypeaheadDirective.prototype, "typeaheadNoResults", void 0);
    __decorate([
        core_1.Output(), 
        __metadata('design:type', core_1.EventEmitter)
    ], TypeaheadDirective.prototype, "typeaheadOnSelect", void 0);
    __decorate([
        core_1.Input(), 
        __metadata('design:type', Object)
    ], TypeaheadDirective.prototype, "typeahead", void 0);
    __decorate([
        core_1.Input(), 
        __metadata('design:type', Number)
    ], TypeaheadDirective.prototype, "typeaheadMinLength", void 0);
    __decorate([
        core_1.Input(), 
        __metadata('design:type', Number)
    ], TypeaheadDirective.prototype, "typeaheadWaitMs", void 0);
    __decorate([
        core_1.Input(), 
        __metadata('design:type', Number)
    ], TypeaheadDirective.prototype, "typeaheadOptionsLimit", void 0);
    __decorate([
        core_1.Input(), 
        __metadata('design:type', String)
    ], TypeaheadDirective.prototype, "typeaheadOptionField", void 0);
    __decorate([
        core_1.Input(), 
        __metadata('design:type', Boolean)
    ], TypeaheadDirective.prototype, "typeaheadAsync", void 0);
    __decorate([
        core_1.Input(), 
        __metadata('design:type', Boolean)
    ], TypeaheadDirective.prototype, "typeaheadLatinize", void 0);
    __decorate([
        core_1.Input(), 
        __metadata('design:type', Boolean)
    ], TypeaheadDirective.prototype, "typeaheadSingleWords", void 0);
    __decorate([
        core_1.Input(), 
        __metadata('design:type', String)
    ], TypeaheadDirective.prototype, "typeaheadWordDelimiters", void 0);
    __decorate([
        core_1.Input(), 
        __metadata('design:type', String)
    ], TypeaheadDirective.prototype, "typeaheadPhraseDelimiters", void 0);
    __decorate([
        core_1.HostListener('keyup', ['$event']), 
        __metadata('design:type', Function), 
        __metadata('design:paramtypes', [Object]), 
        __metadata('design:returntype', void 0)
    ], TypeaheadDirective.prototype, "onChange", null);
    __decorate([
        core_1.HostListener('focus', ['$event.target']), 
        __metadata('design:type', Function), 
        __metadata('design:paramtypes', []), 
        __metadata('design:returntype', void 0)
    ], TypeaheadDirective.prototype, "onFocus", null);
    __decorate([
        core_1.HostListener('blur'), 
        __metadata('design:type', Function), 
        __metadata('design:paramtypes', []), 
        __metadata('design:returntype', void 0)
    ], TypeaheadDirective.prototype, "onBlur", null);
    __decorate([
        core_1.HostListener('keydown', ['$event']), 
        __metadata('design:type', Function), 
        __metadata('design:paramtypes', [Object]), 
        __metadata('design:returntype', void 0)
    ], TypeaheadDirective.prototype, "onKeydown", null);
    TypeaheadDirective = __decorate([
        core_1.Directive({
            /* tslint:disable */
            selector: '[typeahead][ngModel],[typeahead][formControlName]'
        }), 
        __metadata('design:paramtypes', [forms_1.NgControl, core_1.ViewContainerRef, core_1.ElementRef, core_1.Renderer, core_1.DynamicComponentLoader])
    ], TypeaheadDirective);
    return TypeaheadDirective;
}());
exports.TypeaheadDirective = TypeaheadDirective;
