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
var async_1 = require('../facade/async');
var collection_1 = require('../facade/collection');
var lang_1 = require('../facade/lang');
var model_1 = require('../model');
var validators_1 = require('../validators');
var control_container_1 = require('./control_container');
var shared_1 = require('./shared');
exports.formDirectiveProvider = 
/*@ts2dart_const*/ { provide: control_container_1.ControlContainer, useExisting: core_1.forwardRef(function () { return NgForm; }) };
var NgForm = (function (_super) {
    __extends(NgForm, _super);
    function NgForm(validators, asyncValidators) {
        _super.call(this);
        this._submitted = false;
        this.ngSubmit = new async_1.EventEmitter();
        this.form = new model_1.FormGroup({}, null, shared_1.composeValidators(validators), shared_1.composeAsyncValidators(asyncValidators));
    }
    Object.defineProperty(NgForm.prototype, "submitted", {
        get: function () { return this._submitted; },
        enumerable: true,
        configurable: true
    });
    Object.defineProperty(NgForm.prototype, "formDirective", {
        get: function () { return this; },
        enumerable: true,
        configurable: true
    });
    Object.defineProperty(NgForm.prototype, "control", {
        get: function () { return this.form; },
        enumerable: true,
        configurable: true
    });
    Object.defineProperty(NgForm.prototype, "path", {
        get: function () { return []; },
        enumerable: true,
        configurable: true
    });
    Object.defineProperty(NgForm.prototype, "controls", {
        get: function () { return this.form.controls; },
        enumerable: true,
        configurable: true
    });
    NgForm.prototype.addControl = function (dir) {
        var _this = this;
        async_1.PromiseWrapper.scheduleMicrotask(function () {
            var container = _this._findContainer(dir.path);
            dir._control = container.registerControl(dir.name, dir.control);
            shared_1.setUpControl(dir.control, dir);
            dir.control.updateValueAndValidity({ emitEvent: false });
        });
    };
    NgForm.prototype.getControl = function (dir) { return this.form.find(dir.path); };
    NgForm.prototype.removeControl = function (dir) {
        var _this = this;
        async_1.PromiseWrapper.scheduleMicrotask(function () {
            var container = _this._findContainer(dir.path);
            if (lang_1.isPresent(container)) {
                container.removeControl(dir.name);
            }
        });
    };
    NgForm.prototype.addFormGroup = function (dir) {
        var _this = this;
        async_1.PromiseWrapper.scheduleMicrotask(function () {
            var container = _this._findContainer(dir.path);
            var group = new model_1.FormGroup({});
            shared_1.setUpFormContainer(group, dir);
            container.registerControl(dir.name, group);
            group.updateValueAndValidity({ emitEvent: false });
        });
    };
    NgForm.prototype.removeFormGroup = function (dir) {
        var _this = this;
        async_1.PromiseWrapper.scheduleMicrotask(function () {
            var container = _this._findContainer(dir.path);
            if (lang_1.isPresent(container)) {
                container.removeControl(dir.name);
            }
        });
    };
    NgForm.prototype.getFormGroup = function (dir) { return this.form.find(dir.path); };
    NgForm.prototype.updateModel = function (dir, value) {
        var _this = this;
        async_1.PromiseWrapper.scheduleMicrotask(function () {
            var ctrl = _this.form.find(dir.path);
            ctrl.updateValue(value);
        });
    };
    NgForm.prototype.onSubmit = function () {
        this._submitted = true;
        async_1.ObservableWrapper.callEmit(this.ngSubmit, null);
        return false;
    };
    /** @internal */
    NgForm.prototype._findContainer = function (path) {
        path.pop();
        return collection_1.ListWrapper.isEmpty(path) ? this.form : this.form.find(path);
    };
    /** @nocollapse */
    NgForm.decorators = [
        { type: core_1.Directive, args: [{
                    selector: 'form:not([ngNoForm]):not([formGroup]),ngForm,[ngForm]',
                    providers: [exports.formDirectiveProvider],
                    host: {
                        '(submit)': 'onSubmit()',
                    },
                    outputs: ['ngSubmit'],
                    exportAs: 'ngForm'
                },] },
    ];
    /** @nocollapse */
    NgForm.ctorParameters = [
        { type: Array, decorators: [{ type: core_1.Optional }, { type: core_1.Self }, { type: core_1.Inject, args: [validators_1.NG_VALIDATORS,] },] },
        { type: Array, decorators: [{ type: core_1.Optional }, { type: core_1.Self }, { type: core_1.Inject, args: [validators_1.NG_ASYNC_VALIDATORS,] },] },
    ];
    return NgForm;
}(control_container_1.ControlContainer));
exports.NgForm = NgForm;
//# sourceMappingURL=ng_form.js.map