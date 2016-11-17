/**
 * @license
 * Copyright Google Inc. All Rights Reserved.
 *
 * Use of this source code is governed by an MIT-style license that can be
 * found in the LICENSE file at https://angular.io/license
 */
import { Directive, Inject, Input, Optional, Output, Self, forwardRef } from '@angular/core';
import { EventEmitter, ObservableWrapper } from '../../facade/async';
import { ListWrapper, StringMapWrapper } from '../../facade/collection';
import { BaseException } from '../../facade/exceptions';
import { isBlank } from '../../facade/lang';
import { NG_ASYNC_VALIDATORS, NG_VALIDATORS, Validators } from '../../validators';
import { ControlContainer } from '../control_container';
import { composeAsyncValidators, composeValidators, setUpControl, setUpFormContainer } from '../shared';
export const formDirectiveProvider = 
/*@ts2dart_const*/ /* @ts2dart_Provider */ {
    provide: ControlContainer,
    useExisting: forwardRef(() => FormGroupDirective)
};
export class FormGroupDirective extends ControlContainer {
    constructor(_validators, _asyncValidators) {
        super();
        this._validators = _validators;
        this._asyncValidators = _asyncValidators;
        this._submitted = false;
        this.directives = [];
        this.form = null;
        this.ngSubmit = new EventEmitter();
    }
    ngOnChanges(changes) {
        this._checkFormPresent();
        if (StringMapWrapper.contains(changes, 'form')) {
            var sync = composeValidators(this._validators);
            this.form.validator = Validators.compose([this.form.validator, sync]);
            var async = composeAsyncValidators(this._asyncValidators);
            this.form.asyncValidator = Validators.composeAsync([this.form.asyncValidator, async]);
            this.form.updateValueAndValidity({ onlySelf: true, emitEvent: false });
        }
        this._updateDomValue();
    }
    get submitted() { return this._submitted; }
    get formDirective() { return this; }
    get control() { return this.form; }
    get path() { return []; }
    addControl(dir) {
        const ctrl = this.form.find(dir.path);
        setUpControl(ctrl, dir);
        ctrl.updateValueAndValidity({ emitEvent: false });
        this.directives.push(dir);
    }
    getControl(dir) { return this.form.find(dir.path); }
    removeControl(dir) { ListWrapper.remove(this.directives, dir); }
    addFormGroup(dir) {
        var ctrl = this.form.find(dir.path);
        setUpFormContainer(ctrl, dir);
        ctrl.updateValueAndValidity({ emitEvent: false });
    }
    removeFormGroup(dir) { }
    getFormGroup(dir) { return this.form.find(dir.path); }
    addFormArray(dir) {
        var ctrl = this.form.find(dir.path);
        setUpFormContainer(ctrl, dir);
        ctrl.updateValueAndValidity({ emitEvent: false });
    }
    removeFormArray(dir) { }
    getFormArray(dir) { return this.form.find(dir.path); }
    updateModel(dir, value) {
        var ctrl = this.form.find(dir.path);
        ctrl.updateValue(value);
    }
    onSubmit() {
        this._submitted = true;
        ObservableWrapper.callEmit(this.ngSubmit, null);
        return false;
    }
    /** @internal */
    _updateDomValue() {
        this.directives.forEach(dir => {
            var ctrl = this.form.find(dir.path);
            dir.valueAccessor.writeValue(ctrl.value);
        });
    }
    _checkFormPresent() {
        if (isBlank(this.form)) {
            throw new BaseException(`formGroup expects a FormGroup instance. Please pass one in.
           Example: <form [formGroup]="myFormGroup">
      `);
        }
    }
}
/** @nocollapse */
FormGroupDirective.decorators = [
    { type: Directive, args: [{
                selector: '[formGroup]',
                providers: [formDirectiveProvider],
                host: { '(submit)': 'onSubmit()' },
                exportAs: 'ngForm'
            },] },
];
/** @nocollapse */
FormGroupDirective.ctorParameters = [
    { type: Array, decorators: [{ type: Optional }, { type: Self }, { type: Inject, args: [NG_VALIDATORS,] },] },
    { type: Array, decorators: [{ type: Optional }, { type: Self }, { type: Inject, args: [NG_ASYNC_VALIDATORS,] },] },
];
/** @nocollapse */
FormGroupDirective.propDecorators = {
    'form': [{ type: Input, args: ['formGroup',] },],
    'ngSubmit': [{ type: Output },],
};
//# sourceMappingURL=form_group_directive.js.map