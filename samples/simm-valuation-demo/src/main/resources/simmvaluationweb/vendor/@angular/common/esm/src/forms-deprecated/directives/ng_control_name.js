/**
 * @license
 * Copyright Google Inc. All Rights Reserved.
 *
 * Use of this source code is governed by an MIT-style license that can be
 * found in the LICENSE file at https://angular.io/license
 */
import { Directive, Host, Inject, Optional, Self, SkipSelf, forwardRef } from '@angular/core';
import { EventEmitter, ObservableWrapper } from '../../facade/async';
import { NG_ASYNC_VALIDATORS, NG_VALIDATORS } from '../validators';
import { ControlContainer } from './control_container';
import { NG_VALUE_ACCESSOR } from './control_value_accessor';
import { NgControl } from './ng_control';
import { composeAsyncValidators, composeValidators, controlPath, isPropertyUpdated, selectValueAccessor } from './shared';
export const controlNameBinding = 
/*@ts2dart_const*/ /* @ts2dart_Provider */ {
    provide: NgControl,
    useExisting: forwardRef(() => NgControlName)
};
export class NgControlName extends NgControl {
    constructor(_parent, _validators, _asyncValidators, valueAccessors) {
        super();
        this._parent = _parent;
        this._validators = _validators;
        this._asyncValidators = _asyncValidators;
        /** @internal */
        this.update = new EventEmitter();
        this._added = false;
        this.valueAccessor = selectValueAccessor(this, valueAccessors);
    }
    ngOnChanges(changes) {
        if (!this._added) {
            this.formDirective.addControl(this);
            this._added = true;
        }
        if (isPropertyUpdated(changes, this.viewModel)) {
            this.viewModel = this.model;
            this.formDirective.updateModel(this, this.model);
        }
    }
    ngOnDestroy() { this.formDirective.removeControl(this); }
    viewToModelUpdate(newValue) {
        this.viewModel = newValue;
        ObservableWrapper.callEmit(this.update, newValue);
    }
    get path() { return controlPath(this.name, this._parent); }
    get formDirective() { return this._parent.formDirective; }
    get validator() { return composeValidators(this._validators); }
    get asyncValidator() {
        return composeAsyncValidators(this._asyncValidators);
    }
    get control() { return this.formDirective.getControl(this); }
}
/** @nocollapse */
NgControlName.decorators = [
    { type: Directive, args: [{
                selector: '[ngControl]',
                providers: [controlNameBinding],
                inputs: ['name: ngControl', 'model: ngModel'],
                outputs: ['update: ngModelChange'],
                exportAs: 'ngForm'
            },] },
];
/** @nocollapse */
NgControlName.ctorParameters = [
    { type: ControlContainer, decorators: [{ type: Host }, { type: SkipSelf },] },
    { type: Array, decorators: [{ type: Optional }, { type: Self }, { type: Inject, args: [NG_VALIDATORS,] },] },
    { type: Array, decorators: [{ type: Optional }, { type: Self }, { type: Inject, args: [NG_ASYNC_VALIDATORS,] },] },
    { type: Array, decorators: [{ type: Optional }, { type: Self }, { type: Inject, args: [NG_VALUE_ACCESSOR,] },] },
];
//# sourceMappingURL=ng_control_name.js.map