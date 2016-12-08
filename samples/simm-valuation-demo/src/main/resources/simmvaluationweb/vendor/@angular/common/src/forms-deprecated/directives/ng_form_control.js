/**
 * @license
 * Copyright Google Inc. All Rights Reserved.
 *
 * Use of this source code is governed by an MIT-style license that can be
 * found in the LICENSE file at https://angular.io/license
 */
"use strict";
var __extends = (this && this.__extends) || function (d, b) {
    for (var p in b) if (b.hasOwnProperty(p)) d[p] = b[p];
    function __() { this.constructor = d; }
    d.prototype = b === null ? Object.create(b) : (__.prototype = b.prototype, new __());
};
var core_1 = require('@angular/core');
var async_1 = require('../../facade/async');
var collection_1 = require('../../facade/collection');
var validators_1 = require('../validators');
var control_value_accessor_1 = require('./control_value_accessor');
var ng_control_1 = require('./ng_control');
var shared_1 = require('./shared');
exports.formControlBinding = 
/*@ts2dart_const*/ /* @ts2dart_Provider */ {
    provide: ng_control_1.NgControl,
    useExisting: core_1.forwardRef(function () { return NgFormControl; })
};
var NgFormControl = (function (_super) {
    __extends(NgFormControl, _super);
    function NgFormControl(_validators, _asyncValidators, valueAccessors) {
        _super.call(this);
        this._validators = _validators;
        this._asyncValidators = _asyncValidators;
        this.update = new async_1.EventEmitter();
        this.valueAccessor = shared_1.selectValueAccessor(this, valueAccessors);
    }
    NgFormControl.prototype.ngOnChanges = function (changes) {
        if (this._isControlChanged(changes)) {
            shared_1.setUpControl(this.form, this);
            this.form.updateValueAndValidity({ emitEvent: false });
        }
        if (shared_1.isPropertyUpdated(changes, this.viewModel)) {
            this.form.updateValue(this.model);
            this.viewModel = this.model;
        }
    };
    Object.defineProperty(NgFormControl.prototype, "path", {
        get: function () { return []; },
        enumerable: true,
        configurable: true
    });
    Object.defineProperty(NgFormControl.prototype, "validator", {
        get: function () { return shared_1.composeValidators(this._validators); },
        enumerable: true,
        configurable: true
    });
    Object.defineProperty(NgFormControl.prototype, "asyncValidator", {
        get: function () {
            return shared_1.composeAsyncValidators(this._asyncValidators);
        },
        enumerable: true,
        configurable: true
    });
    Object.defineProperty(NgFormControl.prototype, "control", {
        get: function () { return this.form; },
        enumerable: true,
        configurable: true
    });
    NgFormControl.prototype.viewToModelUpdate = function (newValue) {
        this.viewModel = newValue;
        async_1.ObservableWrapper.callEmit(this.update, newValue);
    };
    NgFormControl.prototype._isControlChanged = function (changes) {
        return collection_1.StringMapWrapper.contains(changes, 'form');
    };
    /** @nocollapse */
    NgFormControl.decorators = [
        { type: core_1.Directive, args: [{
                    selector: '[ngFormControl]',
                    providers: [exports.formControlBinding],
                    inputs: ['form: ngFormControl', 'model: ngModel'],
                    outputs: ['update: ngModelChange'],
                    exportAs: 'ngForm'
                },] },
    ];
    /** @nocollapse */
    NgFormControl.ctorParameters = [
        { type: Array, decorators: [{ type: core_1.Optional }, { type: core_1.Self }, { type: core_1.Inject, args: [validators_1.NG_VALIDATORS,] },] },
        { type: Array, decorators: [{ type: core_1.Optional }, { type: core_1.Self }, { type: core_1.Inject, args: [validators_1.NG_ASYNC_VALIDATORS,] },] },
        { type: Array, decorators: [{ type: core_1.Optional }, { type: core_1.Self }, { type: core_1.Inject, args: [control_value_accessor_1.NG_VALUE_ACCESSOR,] },] },
    ];
    return NgFormControl;
}(ng_control_1.NgControl));
exports.NgFormControl = NgFormControl;
//# sourceMappingURL=ng_form_control.js.map