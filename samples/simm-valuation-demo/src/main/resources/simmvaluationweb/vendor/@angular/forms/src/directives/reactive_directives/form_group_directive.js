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
var exceptions_1 = require('../../facade/exceptions');
var lang_1 = require('../../facade/lang');
var validators_1 = require('../../validators');
var control_container_1 = require('../control_container');
var shared_1 = require('../shared');
exports.formDirectiveProvider = 
/*@ts2dart_const*/ /* @ts2dart_Provider */ {
    provide: control_container_1.ControlContainer,
    useExisting: core_1.forwardRef(function () { return FormGroupDirective; })
};
var FormGroupDirective = (function (_super) {
    __extends(FormGroupDirective, _super);
    function FormGroupDirective(_validators, _asyncValidators) {
        _super.call(this);
        this._validators = _validators;
        this._asyncValidators = _asyncValidators;
        this._submitted = false;
        this.directives = [];
        this.form = null;
        this.ngSubmit = new async_1.EventEmitter();
    }
    FormGroupDirective.prototype.ngOnChanges = function (changes) {
        this._checkFormPresent();
        if (collection_1.StringMapWrapper.contains(changes, 'form')) {
            var sync = shared_1.composeValidators(this._validators);
            this.form.validator = validators_1.Validators.compose([this.form.validator, sync]);
            var async = shared_1.composeAsyncValidators(this._asyncValidators);
            this.form.asyncValidator = validators_1.Validators.composeAsync([this.form.asyncValidator, async]);
            this.form.updateValueAndValidity({ onlySelf: true, emitEvent: false });
        }
        this._updateDomValue();
    };
    Object.defineProperty(FormGroupDirective.prototype, "submitted", {
        get: function () { return this._submitted; },
        enumerable: true,
        configurable: true
    });
    Object.defineProperty(FormGroupDirective.prototype, "formDirective", {
        get: function () { return this; },
        enumerable: true,
        configurable: true
    });
    Object.defineProperty(FormGroupDirective.prototype, "control", {
        get: function () { return this.form; },
        enumerable: true,
        configurable: true
    });
    Object.defineProperty(FormGroupDirective.prototype, "path", {
        get: function () { return []; },
        enumerable: true,
        configurable: true
    });
    FormGroupDirective.prototype.addControl = function (dir) {
        var ctrl = this.form.find(dir.path);
        shared_1.setUpControl(ctrl, dir);
        ctrl.updateValueAndValidity({ emitEvent: false });
        this.directives.push(dir);
    };
    FormGroupDirective.prototype.getControl = function (dir) { return this.form.find(dir.path); };
    FormGroupDirective.prototype.removeControl = function (dir) { collection_1.ListWrapper.remove(this.directives, dir); };
    FormGroupDirective.prototype.addFormGroup = function (dir) {
        var ctrl = this.form.find(dir.path);
        shared_1.setUpFormContainer(ctrl, dir);
        ctrl.updateValueAndValidity({ emitEvent: false });
    };
    FormGroupDirective.prototype.removeFormGroup = function (dir) { };
    FormGroupDirective.prototype.getFormGroup = function (dir) { return this.form.find(dir.path); };
    FormGroupDirective.prototype.addFormArray = function (dir) {
        var ctrl = this.form.find(dir.path);
        shared_1.setUpFormContainer(ctrl, dir);
        ctrl.updateValueAndValidity({ emitEvent: false });
    };
    FormGroupDirective.prototype.removeFormArray = function (dir) { };
    FormGroupDirective.prototype.getFormArray = function (dir) { return this.form.find(dir.path); };
    FormGroupDirective.prototype.updateModel = function (dir, value) {
        var ctrl = this.form.find(dir.path);
        ctrl.updateValue(value);
    };
    FormGroupDirective.prototype.onSubmit = function () {
        this._submitted = true;
        async_1.ObservableWrapper.callEmit(this.ngSubmit, null);
        return false;
    };
    /** @internal */
    FormGroupDirective.prototype._updateDomValue = function () {
        var _this = this;
        this.directives.forEach(function (dir) {
            var ctrl = _this.form.find(dir.path);
            dir.valueAccessor.writeValue(ctrl.value);
        });
    };
    FormGroupDirective.prototype._checkFormPresent = function () {
        if (lang_1.isBlank(this.form)) {
            throw new exceptions_1.BaseException("formGroup expects a FormGroup instance. Please pass one in.\n           Example: <form [formGroup]=\"myFormGroup\">\n      ");
        }
    };
    /** @nocollapse */
    FormGroupDirective.decorators = [
        { type: core_1.Directive, args: [{
                    selector: '[formGroup]',
                    providers: [exports.formDirectiveProvider],
                    host: { '(submit)': 'onSubmit()' },
                    exportAs: 'ngForm'
                },] },
    ];
    /** @nocollapse */
    FormGroupDirective.ctorParameters = [
        { type: Array, decorators: [{ type: core_1.Optional }, { type: core_1.Self }, { type: core_1.Inject, args: [validators_1.NG_VALIDATORS,] },] },
        { type: Array, decorators: [{ type: core_1.Optional }, { type: core_1.Self }, { type: core_1.Inject, args: [validators_1.NG_ASYNC_VALIDATORS,] },] },
    ];
    /** @nocollapse */
    FormGroupDirective.propDecorators = {
        'form': [{ type: core_1.Input, args: ['formGroup',] },],
        'ngSubmit': [{ type: core_1.Output },],
    };
    return FormGroupDirective;
}(control_container_1.ControlContainer));
exports.FormGroupDirective = FormGroupDirective;
//# sourceMappingURL=form_group_directive.js.map