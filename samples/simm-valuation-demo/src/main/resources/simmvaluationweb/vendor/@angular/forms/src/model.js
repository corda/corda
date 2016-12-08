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
var shared_1 = require('./directives/shared');
var async_1 = require('./facade/async');
var collection_1 = require('./facade/collection');
var lang_1 = require('./facade/lang');
/**
 * Indicates that a FormControl is valid, i.e. that no errors exist in the input value.
 */
exports.VALID = 'VALID';
/**
 * Indicates that a FormControl is invalid, i.e. that an error exists in the input value.
 */
exports.INVALID = 'INVALID';
/**
 * Indicates that a FormControl is pending, i.e. that async validation is occurring and
 * errors are not yet available for the input value.
 */
exports.PENDING = 'PENDING';
function isControl(control) {
    return control instanceof AbstractControl;
}
exports.isControl = isControl;
function _find(control, path) {
    if (lang_1.isBlank(path))
        return null;
    if (!(path instanceof Array)) {
        path = path.split('/');
    }
    if (path instanceof Array && collection_1.ListWrapper.isEmpty(path))
        return null;
    return path.reduce(function (v, name) {
        if (v instanceof FormGroup) {
            return lang_1.isPresent(v.controls[name]) ? v.controls[name] : null;
        }
        else if (v instanceof FormArray) {
            var index = name;
            return lang_1.isPresent(v.at(index)) ? v.at(index) : null;
        }
        else {
            return null;
        }
    }, control);
}
function toObservable(r) {
    return lang_1.isPromise(r) ? async_1.ObservableWrapper.fromPromise(r) : r;
}
function coerceToValidator(validator) {
    return Array.isArray(validator) ? shared_1.composeValidators(validator) : validator;
}
function coerceToAsyncValidator(asyncValidator) {
    return Array.isArray(asyncValidator) ? shared_1.composeAsyncValidators(asyncValidator) : asyncValidator;
}
/**
 * @experimental
 */
var AbstractControl = (function () {
    function AbstractControl(validator, asyncValidator) {
        this.validator = validator;
        this.asyncValidator = asyncValidator;
        this._pristine = true;
        this._touched = false;
    }
    Object.defineProperty(AbstractControl.prototype, "value", {
        get: function () { return this._value; },
        enumerable: true,
        configurable: true
    });
    Object.defineProperty(AbstractControl.prototype, "status", {
        get: function () { return this._status; },
        enumerable: true,
        configurable: true
    });
    Object.defineProperty(AbstractControl.prototype, "valid", {
        get: function () { return this._status === exports.VALID; },
        enumerable: true,
        configurable: true
    });
    Object.defineProperty(AbstractControl.prototype, "errors", {
        /**
         * Returns the errors of this control.
         */
        get: function () { return this._errors; },
        enumerable: true,
        configurable: true
    });
    Object.defineProperty(AbstractControl.prototype, "pristine", {
        get: function () { return this._pristine; },
        enumerable: true,
        configurable: true
    });
    Object.defineProperty(AbstractControl.prototype, "dirty", {
        get: function () { return !this.pristine; },
        enumerable: true,
        configurable: true
    });
    Object.defineProperty(AbstractControl.prototype, "touched", {
        get: function () { return this._touched; },
        enumerable: true,
        configurable: true
    });
    Object.defineProperty(AbstractControl.prototype, "untouched", {
        get: function () { return !this._touched; },
        enumerable: true,
        configurable: true
    });
    Object.defineProperty(AbstractControl.prototype, "valueChanges", {
        get: function () { return this._valueChanges; },
        enumerable: true,
        configurable: true
    });
    Object.defineProperty(AbstractControl.prototype, "statusChanges", {
        get: function () { return this._statusChanges; },
        enumerable: true,
        configurable: true
    });
    Object.defineProperty(AbstractControl.prototype, "pending", {
        get: function () { return this._status == exports.PENDING; },
        enumerable: true,
        configurable: true
    });
    AbstractControl.prototype.setAsyncValidators = function (newValidator) {
        this.asyncValidator = coerceToAsyncValidator(newValidator);
    };
    AbstractControl.prototype.clearAsyncValidators = function () { this.asyncValidator = null; };
    AbstractControl.prototype.setValidators = function (newValidator) {
        this.validator = coerceToValidator(newValidator);
    };
    AbstractControl.prototype.clearValidators = function () { this.validator = null; };
    AbstractControl.prototype.markAsTouched = function () { this._touched = true; };
    AbstractControl.prototype.markAsDirty = function (_a) {
        var onlySelf = (_a === void 0 ? {} : _a).onlySelf;
        onlySelf = lang_1.normalizeBool(onlySelf);
        this._pristine = false;
        if (lang_1.isPresent(this._parent) && !onlySelf) {
            this._parent.markAsDirty({ onlySelf: onlySelf });
        }
    };
    AbstractControl.prototype.markAsPending = function (_a) {
        var onlySelf = (_a === void 0 ? {} : _a).onlySelf;
        onlySelf = lang_1.normalizeBool(onlySelf);
        this._status = exports.PENDING;
        if (lang_1.isPresent(this._parent) && !onlySelf) {
            this._parent.markAsPending({ onlySelf: onlySelf });
        }
    };
    AbstractControl.prototype.setParent = function (parent) { this._parent = parent; };
    AbstractControl.prototype.updateValueAndValidity = function (_a) {
        var _b = _a === void 0 ? {} : _a, onlySelf = _b.onlySelf, emitEvent = _b.emitEvent;
        onlySelf = lang_1.normalizeBool(onlySelf);
        emitEvent = lang_1.isPresent(emitEvent) ? emitEvent : true;
        this._updateValue();
        this._errors = this._runValidator();
        this._status = this._calculateStatus();
        if (this._status == exports.VALID || this._status == exports.PENDING) {
            this._runAsyncValidator(emitEvent);
        }
        if (emitEvent) {
            async_1.ObservableWrapper.callEmit(this._valueChanges, this._value);
            async_1.ObservableWrapper.callEmit(this._statusChanges, this._status);
        }
        if (lang_1.isPresent(this._parent) && !onlySelf) {
            this._parent.updateValueAndValidity({ onlySelf: onlySelf, emitEvent: emitEvent });
        }
    };
    AbstractControl.prototype._runValidator = function () {
        return lang_1.isPresent(this.validator) ? this.validator(this) : null;
    };
    AbstractControl.prototype._runAsyncValidator = function (emitEvent) {
        var _this = this;
        if (lang_1.isPresent(this.asyncValidator)) {
            this._status = exports.PENDING;
            this._cancelExistingSubscription();
            var obs = toObservable(this.asyncValidator(this));
            this._asyncValidationSubscription = async_1.ObservableWrapper.subscribe(obs, function (res) { return _this.setErrors(res, { emitEvent: emitEvent }); });
        }
    };
    AbstractControl.prototype._cancelExistingSubscription = function () {
        if (lang_1.isPresent(this._asyncValidationSubscription)) {
            async_1.ObservableWrapper.dispose(this._asyncValidationSubscription);
        }
    };
    /**
     * Sets errors on a form control.
     *
     * This is used when validations are run not automatically, but manually by the user.
     *
     * Calling `setErrors` will also update the validity of the parent control.
     *
     * ## Usage
     *
     * ```
     * var login = new FormControl("someLogin");
     * login.setErrors({
     *   "notUnique": true
     * });
     *
     * expect(login.valid).toEqual(false);
     * expect(login.errors).toEqual({"notUnique": true});
     *
     * login.updateValue("someOtherLogin");
     *
     * expect(login.valid).toEqual(true);
     * ```
     */
    AbstractControl.prototype.setErrors = function (errors, _a) {
        var emitEvent = (_a === void 0 ? {} : _a).emitEvent;
        emitEvent = lang_1.isPresent(emitEvent) ? emitEvent : true;
        this._errors = errors;
        this._updateControlsErrors(emitEvent);
    };
    AbstractControl.prototype.find = function (path) { return _find(this, path); };
    AbstractControl.prototype.getError = function (errorCode, path) {
        if (path === void 0) { path = null; }
        var control = lang_1.isPresent(path) && !collection_1.ListWrapper.isEmpty(path) ? this.find(path) : this;
        if (lang_1.isPresent(control) && lang_1.isPresent(control._errors)) {
            return collection_1.StringMapWrapper.get(control._errors, errorCode);
        }
        else {
            return null;
        }
    };
    AbstractControl.prototype.hasError = function (errorCode, path) {
        if (path === void 0) { path = null; }
        return lang_1.isPresent(this.getError(errorCode, path));
    };
    Object.defineProperty(AbstractControl.prototype, "root", {
        get: function () {
            var x = this;
            while (lang_1.isPresent(x._parent)) {
                x = x._parent;
            }
            return x;
        },
        enumerable: true,
        configurable: true
    });
    /** @internal */
    AbstractControl.prototype._updateControlsErrors = function (emitEvent) {
        this._status = this._calculateStatus();
        if (emitEvent) {
            async_1.ObservableWrapper.callEmit(this._statusChanges, this._status);
        }
        if (lang_1.isPresent(this._parent)) {
            this._parent._updateControlsErrors(emitEvent);
        }
    };
    /** @internal */
    AbstractControl.prototype._initObservables = function () {
        this._valueChanges = new async_1.EventEmitter();
        this._statusChanges = new async_1.EventEmitter();
    };
    AbstractControl.prototype._calculateStatus = function () {
        if (lang_1.isPresent(this._errors))
            return exports.INVALID;
        if (this._anyControlsHaveStatus(exports.PENDING))
            return exports.PENDING;
        if (this._anyControlsHaveStatus(exports.INVALID))
            return exports.INVALID;
        return exports.VALID;
    };
    return AbstractControl;
}());
exports.AbstractControl = AbstractControl;
/**
 * Defines a part of a form that cannot be divided into other controls. `FormControl`s have values
 * and
 * validation state, which is determined by an optional validation function.
 *
 * `FormControl` is one of the three fundamental building blocks used to define forms in Angular,
 * along
 * with {@link FormGroup} and {@link FormArray}.
 *
 * ## Usage
 *
 * By default, a `FormControl` is created for every `<input>` or other form component.
 * With {@link FormControlDirective} or {@link FormGroupDirective} an existing {@link FormControl}
 * can be bound to a DOM element instead. This `FormControl` can be configured with a custom
 * validation function.
 *
 * @experimental
 */
var FormControl = (function (_super) {
    __extends(FormControl, _super);
    function FormControl(value, validator, asyncValidator) {
        if (value === void 0) { value = null; }
        if (validator === void 0) { validator = null; }
        if (asyncValidator === void 0) { asyncValidator = null; }
        _super.call(this, coerceToValidator(validator), coerceToAsyncValidator(asyncValidator));
        /** @internal */
        this._onChange = [];
        this._value = value;
        this.updateValueAndValidity({ onlySelf: true, emitEvent: false });
        this._initObservables();
    }
    /**
     * Set the value of the form control to `value`.
     *
     * If `onlySelf` is `true`, this change will only affect the validation of this `FormControl`
     * and not its parent component. If `emitEvent` is `true`, this change will cause a
     * `valueChanges` event on the `FormControl` to be emitted. Both of these options default to
     * `false`.
     *
     * If `emitModelToViewChange` is `true`, the view will be notified about the new value
     * via an `onChange` event. This is the default behavior if `emitModelToViewChange` is not
     * specified.
     */
    FormControl.prototype.updateValue = function (value, _a) {
        var _this = this;
        var _b = _a === void 0 ? {} : _a, onlySelf = _b.onlySelf, emitEvent = _b.emitEvent, emitModelToViewChange = _b.emitModelToViewChange;
        emitModelToViewChange = lang_1.isPresent(emitModelToViewChange) ? emitModelToViewChange : true;
        this._value = value;
        if (this._onChange.length && emitModelToViewChange) {
            this._onChange.forEach(function (changeFn) { return changeFn(_this._value); });
        }
        this.updateValueAndValidity({ onlySelf: onlySelf, emitEvent: emitEvent });
    };
    /**
     * @internal
     */
    FormControl.prototype._updateValue = function () { };
    /**
     * @internal
     */
    FormControl.prototype._anyControlsHaveStatus = function (status) { return false; };
    /**
     * Register a listener for change events.
     */
    FormControl.prototype.registerOnChange = function (fn) { this._onChange.push(fn); };
    return FormControl;
}(AbstractControl));
exports.FormControl = FormControl;
/**
 * Defines a part of a form, of fixed length, that can contain other controls.
 *
 * A `FormGroup` aggregates the values of each {@link FormControl} in the group.
 * The status of a `FormGroup` depends on the status of its children.
 * If one of the controls in a group is invalid, the entire group is invalid.
 * Similarly, if a control changes its value, the entire group changes as well.
 *
 * `FormGroup` is one of the three fundamental building blocks used to define forms in Angular,
 * along with {@link FormControl} and {@link FormArray}. {@link FormArray} can also contain other
 * controls, but is of variable length.
 *
 * ### Example ([live demo](http://plnkr.co/edit/23DESOpbNnBpBHZt1BR4?p=preview))
 *
 * @experimental
 */
var FormGroup = (function (_super) {
    __extends(FormGroup, _super);
    function FormGroup(controls, optionals, validator, asyncValidator) {
        if (optionals === void 0) { optionals = null; }
        if (validator === void 0) { validator = null; }
        if (asyncValidator === void 0) { asyncValidator = null; }
        _super.call(this, validator, asyncValidator);
        this.controls = controls;
        this._optionals = lang_1.isPresent(optionals) ? optionals : {};
        this._initObservables();
        this._setParentForControls();
        this.updateValueAndValidity({ onlySelf: true, emitEvent: false });
    }
    /**
     * Register a control with the group's list of controls.
     */
    FormGroup.prototype.registerControl = function (name, control) {
        if (this.controls[name])
            return this.controls[name];
        this.controls[name] = control;
        control.setParent(this);
        return control;
    };
    /**
     * Add a control to this group.
     */
    FormGroup.prototype.addControl = function (name, control) {
        this.registerControl(name, control);
        this.updateValueAndValidity();
    };
    /**
     * Remove a control from this group.
     */
    FormGroup.prototype.removeControl = function (name) {
        collection_1.StringMapWrapper.delete(this.controls, name);
        this.updateValueAndValidity();
    };
    /**
     * Mark the named control as non-optional.
     */
    FormGroup.prototype.include = function (controlName) {
        collection_1.StringMapWrapper.set(this._optionals, controlName, true);
        this.updateValueAndValidity();
    };
    /**
     * Mark the named control as optional.
     */
    FormGroup.prototype.exclude = function (controlName) {
        collection_1.StringMapWrapper.set(this._optionals, controlName, false);
        this.updateValueAndValidity();
    };
    /**
     * Check whether there is a control with the given name in the group.
     */
    FormGroup.prototype.contains = function (controlName) {
        var c = collection_1.StringMapWrapper.contains(this.controls, controlName);
        return c && this._included(controlName);
    };
    /** @internal */
    FormGroup.prototype._setParentForControls = function () {
        var _this = this;
        collection_1.StringMapWrapper.forEach(this.controls, function (control, name) { control.setParent(_this); });
    };
    /** @internal */
    FormGroup.prototype._updateValue = function () { this._value = this._reduceValue(); };
    /** @internal */
    FormGroup.prototype._anyControlsHaveStatus = function (status) {
        var _this = this;
        var res = false;
        collection_1.StringMapWrapper.forEach(this.controls, function (control, name) {
            res = res || (_this.contains(name) && control.status == status);
        });
        return res;
    };
    /** @internal */
    FormGroup.prototype._reduceValue = function () {
        return this._reduceChildren({}, function (acc, control, name) {
            acc[name] = control.value;
            return acc;
        });
    };
    /** @internal */
    FormGroup.prototype._reduceChildren = function (initValue, fn) {
        var _this = this;
        var res = initValue;
        collection_1.StringMapWrapper.forEach(this.controls, function (control, name) {
            if (_this._included(name)) {
                res = fn(res, control, name);
            }
        });
        return res;
    };
    /** @internal */
    FormGroup.prototype._included = function (controlName) {
        var isOptional = collection_1.StringMapWrapper.contains(this._optionals, controlName);
        return !isOptional || collection_1.StringMapWrapper.get(this._optionals, controlName);
    };
    return FormGroup;
}(AbstractControl));
exports.FormGroup = FormGroup;
/**
 * Defines a part of a form, of variable length, that can contain other controls.
 *
 * A `FormArray` aggregates the values of each {@link FormControl} in the group.
 * The status of a `FormArray` depends on the status of its children.
 * If one of the controls in a group is invalid, the entire array is invalid.
 * Similarly, if a control changes its value, the entire array changes as well.
 *
 * `FormArray` is one of the three fundamental building blocks used to define forms in Angular,
 * along with {@link FormControl} and {@link FormGroup}. {@link FormGroup} can also contain
 * other controls, but is of fixed length.
 *
 * ## Adding or removing controls
 *
 * To change the controls in the array, use the `push`, `insert`, or `removeAt` methods
 * in `FormArray` itself. These methods ensure the controls are properly tracked in the
 * form's hierarchy. Do not modify the array of `AbstractControl`s used to instantiate
 * the `FormArray` directly, as that will result in strange and unexpected behavior such
 * as broken change detection.
 *
 * ### Example ([live demo](http://plnkr.co/edit/23DESOpbNnBpBHZt1BR4?p=preview))
 *
 * @experimental
 */
var FormArray = (function (_super) {
    __extends(FormArray, _super);
    function FormArray(controls, validator, asyncValidator) {
        if (validator === void 0) { validator = null; }
        if (asyncValidator === void 0) { asyncValidator = null; }
        _super.call(this, validator, asyncValidator);
        this.controls = controls;
        this._initObservables();
        this._setParentForControls();
        this.updateValueAndValidity({ onlySelf: true, emitEvent: false });
    }
    /**
     * Get the {@link AbstractControl} at the given `index` in the array.
     */
    FormArray.prototype.at = function (index) { return this.controls[index]; };
    /**
     * Insert a new {@link AbstractControl} at the end of the array.
     */
    FormArray.prototype.push = function (control) {
        this.controls.push(control);
        control.setParent(this);
        this.updateValueAndValidity();
    };
    /**
     * Insert a new {@link AbstractControl} at the given `index` in the array.
     */
    FormArray.prototype.insert = function (index, control) {
        collection_1.ListWrapper.insert(this.controls, index, control);
        control.setParent(this);
        this.updateValueAndValidity();
    };
    /**
     * Remove the control at the given `index` in the array.
     */
    FormArray.prototype.removeAt = function (index) {
        collection_1.ListWrapper.removeAt(this.controls, index);
        this.updateValueAndValidity();
    };
    Object.defineProperty(FormArray.prototype, "length", {
        /**
         * Length of the control array.
         */
        get: function () { return this.controls.length; },
        enumerable: true,
        configurable: true
    });
    /** @internal */
    FormArray.prototype._updateValue = function () { this._value = this.controls.map(function (control) { return control.value; }); };
    /** @internal */
    FormArray.prototype._anyControlsHaveStatus = function (status) {
        return this.controls.some(function (c) { return c.status == status; });
    };
    /** @internal */
    FormArray.prototype._setParentForControls = function () {
        var _this = this;
        this.controls.forEach(function (control) { control.setParent(_this); });
    };
    return FormArray;
}(AbstractControl));
exports.FormArray = FormArray;
//# sourceMappingURL=model.js.map