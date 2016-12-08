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
var validators_1 = require('../validators');
var control_container_1 = require('./control_container');
var control_value_accessor_1 = require('./control_value_accessor');
var ng_control_1 = require('./ng_control');
var shared_1 = require('./shared');
exports.controlNameBinding = 
/*@ts2dart_const*/ /* @ts2dart_Provider */ {
    provide: ng_control_1.NgControl,
    useExisting: core_1.forwardRef(function () { return NgControlName; })
};
var NgControlName = (function (_super) {
    __extends(NgControlName, _super);
    function NgControlName(_parent, _validators, _asyncValidators, valueAccessors) {
        _super.call(this);
        this._parent = _parent;
        this._validators = _validators;
        this._asyncValidators = _asyncValidators;
        /** @internal */
        this.update = new async_1.EventEmitter();
        this._added = false;
        this.valueAccessor = shared_1.selectValueAccessor(this, valueAccessors);
    }
    NgControlName.prototype.ngOnChanges = function (changes) {
        if (!this._added) {
            this.formDirective.addControl(this);
            this._added = true;
        }
        if (shared_1.isPropertyUpdated(changes, this.viewModel)) {
            this.viewModel = this.model;
            this.formDirective.updateModel(this, this.model);
        }
    };
    NgControlName.prototype.ngOnDestroy = function () { this.formDirective.removeControl(this); };
    NgControlName.prototype.viewToModelUpdate = function (newValue) {
        this.viewModel = newValue;
        async_1.ObservableWrapper.callEmit(this.update, newValue);
    };
    Object.defineProperty(NgControlName.prototype, "path", {
        get: function () { return shared_1.controlPath(this.name, this._parent); },
        enumerable: true,
        configurable: true
    });
    Object.defineProperty(NgControlName.prototype, "formDirective", {
        get: function () { return this._parent.formDirective; },
        enumerable: true,
        configurable: true
    });
    Object.defineProperty(NgControlName.prototype, "validator", {
        get: function () { return shared_1.composeValidators(this._validators); },
        enumerable: true,
        configurable: true
    });
    Object.defineProperty(NgControlName.prototype, "asyncValidator", {
        get: function () {
            return shared_1.composeAsyncValidators(this._asyncValidators);
        },
        enumerable: true,
        configurable: true
    });
    Object.defineProperty(NgControlName.prototype, "control", {
        get: function () { return this.formDirective.getControl(this); },
        enumerable: true,
        configurable: true
    });
    /** @nocollapse */
    NgControlName.decorators = [
        { type: core_1.Directive, args: [{
                    selector: '[ngControl]',
                    providers: [exports.controlNameBinding],
                    inputs: ['name: ngControl', 'model: ngModel'],
                    outputs: ['update: ngModelChange'],
                    exportAs: 'ngForm'
                },] },
    ];
    /** @nocollapse */
    NgControlName.ctorParameters = [
        { type: control_container_1.ControlContainer, decorators: [{ type: core_1.Host }, { type: core_1.SkipSelf },] },
        { type: Array, decorators: [{ type: core_1.Optional }, { type: core_1.Self }, { type: core_1.Inject, args: [validators_1.NG_VALIDATORS,] },] },
        { type: Array, decorators: [{ type: core_1.Optional }, { type: core_1.Self }, { type: core_1.Inject, args: [validators_1.NG_ASYNC_VALIDATORS,] },] },
        { type: Array, decorators: [{ type: core_1.Optional }, { type: core_1.Self }, { type: core_1.Inject, args: [control_value_accessor_1.NG_VALUE_ACCESSOR,] },] },
    ];
    return NgControlName;
}(ng_control_1.NgControl));
exports.NgControlName = NgControlName;
//# sourceMappingURL=ng_control_name.js.map