/**
 * @license
 * Copyright Google Inc. All Rights Reserved.
 *
 * Use of this source code is governed by an MIT-style license that can be
 * found in the LICENSE file at https://angular.io/license
 */
import { Directive, Inject, Optional, Self, forwardRef } from '@angular/core';
import { EventEmitter, ObservableWrapper, PromiseWrapper } from '../facade/async';
import { ListWrapper } from '../facade/collection';
import { isPresent } from '../facade/lang';
import { FormGroup } from '../model';
import { NG_ASYNC_VALIDATORS, NG_VALIDATORS } from '../validators';
import { ControlContainer } from './control_container';
import { composeAsyncValidators, composeValidators, setUpControl, setUpFormContainer } from './shared';
export const formDirectiveProvider = 
/*@ts2dart_const*/ { provide: ControlContainer, useExisting: forwardRef(() => NgForm) };
export class NgForm extends ControlContainer {
    constructor(validators, asyncValidators) {
        super();
        this._submitted = false;
        this.ngSubmit = new EventEmitter();
        this.form = new FormGroup({}, null, composeValidators(validators), composeAsyncValidators(asyncValidators));
    }
    get submitted() { return this._submitted; }
    get formDirective() { return this; }
    get control() { return this.form; }
    get path() { return []; }
    get controls() { return this.form.controls; }
    addControl(dir) {
        PromiseWrapper.scheduleMicrotask(() => {
            const container = this._findContainer(dir.path);
            dir._control = container.registerControl(dir.name, dir.control);
            setUpControl(dir.control, dir);
            dir.control.updateValueAndValidity({ emitEvent: false });
        });
    }
    getControl(dir) { return this.form.find(dir.path); }
    removeControl(dir) {
        PromiseWrapper.scheduleMicrotask(() => {
            var container = this._findContainer(dir.path);
            if (isPresent(container)) {
                container.removeControl(dir.name);
            }
        });
    }
    addFormGroup(dir) {
        PromiseWrapper.scheduleMicrotask(() => {
            var container = this._findContainer(dir.path);
            var group = new FormGroup({});
            setUpFormContainer(group, dir);
            container.registerControl(dir.name, group);
            group.updateValueAndValidity({ emitEvent: false });
        });
    }
    removeFormGroup(dir) {
        PromiseWrapper.scheduleMicrotask(() => {
            var container = this._findContainer(dir.path);
            if (isPresent(container)) {
                container.removeControl(dir.name);
            }
        });
    }
    getFormGroup(dir) { return this.form.find(dir.path); }
    updateModel(dir, value) {
        PromiseWrapper.scheduleMicrotask(() => {
            var ctrl = this.form.find(dir.path);
            ctrl.updateValue(value);
        });
    }
    onSubmit() {
        this._submitted = true;
        ObservableWrapper.callEmit(this.ngSubmit, null);
        return false;
    }
    /** @internal */
    _findContainer(path) {
        path.pop();
        return ListWrapper.isEmpty(path) ? this.form : this.form.find(path);
    }
}
/** @nocollapse */
NgForm.decorators = [
    { type: Directive, args: [{
                selector: 'form:not([ngNoForm]):not([formGroup]),ngForm,[ngForm]',
                providers: [formDirectiveProvider],
                host: {
                    '(submit)': 'onSubmit()',
                },
                outputs: ['ngSubmit'],
                exportAs: 'ngForm'
            },] },
];
/** @nocollapse */
NgForm.ctorParameters = [
    { type: Array, decorators: [{ type: Optional }, { type: Self }, { type: Inject, args: [NG_VALIDATORS,] },] },
    { type: Array, decorators: [{ type: Optional }, { type: Self }, { type: Inject, args: [NG_ASYNC_VALIDATORS,] },] },
];
//# sourceMappingURL=ng_form.js.map