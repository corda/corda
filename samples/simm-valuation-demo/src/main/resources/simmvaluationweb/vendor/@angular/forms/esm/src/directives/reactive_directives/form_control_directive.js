/**
 * @license
 * Copyright Google Inc. All Rights Reserved.
 *
 * Use of this source code is governed by an MIT-style license that can be
 * found in the LICENSE file at https://angular.io/license
 */
import { Directive, Inject, Input, Optional, Output, Self, forwardRef } from '@angular/core';
import { EventEmitter, ObservableWrapper } from '../../facade/async';
import { StringMapWrapper } from '../../facade/collection';
import { NG_ASYNC_VALIDATORS, NG_VALIDATORS } from '../../validators';
import { NG_VALUE_ACCESSOR } from '../control_value_accessor';
import { NgControl } from '../ng_control';
import { composeAsyncValidators, composeValidators, isPropertyUpdated, selectValueAccessor, setUpControl } from '../shared';
export const formControlBinding = 
/*@ts2dart_const*/ /* @ts2dart_Provider */ {
    provide: NgControl,
    useExisting: forwardRef(() => FormControlDirective)
};
export class FormControlDirective extends NgControl {
    constructor(_validators, _asyncValidators, valueAccessors) {
        super();
        this._validators = _validators;
        this._asyncValidators = _asyncValidators;
        this.update = new EventEmitter();
        this.valueAccessor = selectValueAccessor(this, valueAccessors);
    }
    ngOnChanges(changes) {
        if (this._isControlChanged(changes)) {
            setUpControl(this.form, this);
            this.form.updateValueAndValidity({ emitEvent: false });
        }
        if (isPropertyUpdated(changes, this.viewModel)) {
            this.form.updateValue(this.model);
            this.viewModel = this.model;
        }
    }
    get path() { return []; }
    get validator() { return composeValidators(this._validators); }
    get asyncValidator() {
        return composeAsyncValidators(this._asyncValidators);
    }
    get control() { return this.form; }
    viewToModelUpdate(newValue) {
        this.viewModel = newValue;
        ObservableWrapper.callEmit(this.update, newValue);
    }
    _isControlChanged(changes) {
        return StringMapWrapper.contains(changes, 'form');
    }
}
/** @nocollapse */
FormControlDirective.decorators = [
    { type: Directive, args: [{ selector: '[formControl]', providers: [formControlBinding], exportAs: 'ngForm' },] },
];
/** @nocollapse */
FormControlDirective.ctorParameters = [
    { type: Array, decorators: [{ type: Optional }, { type: Self }, { type: Inject, args: [NG_VALIDATORS,] },] },
    { type: Array, decorators: [{ type: Optional }, { type: Self }, { type: Inject, args: [NG_ASYNC_VALIDATORS,] },] },
    { type: Array, decorators: [{ type: Optional }, { type: Self }, { type: Inject, args: [NG_VALUE_ACCESSOR,] },] },
];
/** @nocollapse */
FormControlDirective.propDecorators = {
    'form': [{ type: Input, args: ['formControl',] },],
    'model': [{ type: Input, args: ['ngModel',] },],
    'update': [{ type: Output, args: ['ngModelChange',] },],
};
//# sourceMappingURL=form_control_directive.js.map