/**
 * @license
 * Copyright Google Inc. All Rights Reserved.
 *
 * Use of this source code is governed by an MIT-style license that can be
 * found in the LICENSE file at https://angular.io/license
 */
import { Directive, Host, Inject, Input, Optional, Output, Self, forwardRef } from '@angular/core';
import { EventEmitter, ObservableWrapper, PromiseWrapper } from '../facade/async';
import { BaseException } from '../facade/exceptions';
import { FormControl } from '../model';
import { NG_ASYNC_VALIDATORS, NG_VALIDATORS } from '../validators';
import { ControlContainer } from './control_container';
import { NG_VALUE_ACCESSOR } from './control_value_accessor';
import { NgControl } from './ng_control';
import { composeAsyncValidators, composeValidators, controlPath, isPropertyUpdated, selectValueAccessor, setUpControl } from './shared';
export const formControlBinding = 
/*@ts2dart_const*/ /* @ts2dart_Provider */ {
    provide: NgControl,
    useExisting: forwardRef(() => NgModel)
};
export class NgModel extends NgControl {
    constructor(_parent, _validators, _asyncValidators, valueAccessors) {
        super();
        this._parent = _parent;
        this._validators = _validators;
        this._asyncValidators = _asyncValidators;
        /** @internal */
        this._control = new FormControl();
        /** @internal */
        this._registered = false;
        this.update = new EventEmitter();
        this.valueAccessor = selectValueAccessor(this, valueAccessors);
    }
    ngOnChanges(changes) {
        this._checkName();
        if (!this._registered)
            this._setUpControl();
        if (isPropertyUpdated(changes, this.viewModel)) {
            this._updateValue(this.model);
            this.viewModel = this.model;
        }
    }
    ngOnDestroy() { this.formDirective && this.formDirective.removeControl(this); }
    get control() { return this._control; }
    get path() {
        return this._parent ? controlPath(this.name, this._parent) : [];
    }
    get formDirective() { return this._parent ? this._parent.formDirective : null; }
    get validator() { return composeValidators(this._validators); }
    get asyncValidator() {
        return composeAsyncValidators(this._asyncValidators);
    }
    viewToModelUpdate(newValue) {
        this.viewModel = newValue;
        ObservableWrapper.callEmit(this.update, newValue);
    }
    _setUpControl() {
        this._isStandalone() ? this._setUpStandalone() :
            this.formDirective.addControl(this);
        this._registered = true;
    }
    _isStandalone() {
        return !this._parent || (this.options && this.options.standalone);
    }
    _setUpStandalone() {
        setUpControl(this._control, this);
        this._control.updateValueAndValidity({ emitEvent: false });
    }
    _checkName() {
        if (this.options && this.options.name)
            this.name = this.options.name;
        if (!this._isStandalone() && !this.name) {
            throw new BaseException(`If ngModel is used within a form tag, either the name attribute must be set
                      or the form control must be defined as 'standalone' in ngModelOptions.

                      Example 1: <input [(ngModel)]="person.firstName" name="first">
                      Example 2: <input [(ngModel)]="person.firstName" [ngModelOptions]="{standalone: true}">
                   `);
        }
    }
    _updateValue(value) {
        PromiseWrapper.scheduleMicrotask(() => { this.control.updateValue(value); });
    }
}
/** @nocollapse */
NgModel.decorators = [
    { type: Directive, args: [{
                selector: '[ngModel]:not([formControlName]):not([formControl])',
                providers: [formControlBinding],
                exportAs: 'ngModel'
            },] },
];
/** @nocollapse */
NgModel.ctorParameters = [
    { type: ControlContainer, decorators: [{ type: Optional }, { type: Host },] },
    { type: Array, decorators: [{ type: Optional }, { type: Self }, { type: Inject, args: [NG_VALIDATORS,] },] },
    { type: Array, decorators: [{ type: Optional }, { type: Self }, { type: Inject, args: [NG_ASYNC_VALIDATORS,] },] },
    { type: Array, decorators: [{ type: Optional }, { type: Self }, { type: Inject, args: [NG_VALUE_ACCESSOR,] },] },
];
/** @nocollapse */
NgModel.propDecorators = {
    'model': [{ type: Input, args: ['ngModel',] },],
    'name': [{ type: Input },],
    'options': [{ type: Input, args: ['ngModelOptions',] },],
    'update': [{ type: Output, args: ['ngModelChange',] },],
};
//# sourceMappingURL=ng_model.js.map