/**
 * @license
 * Copyright Google Inc. All Rights Reserved.
 *
 * Use of this source code is governed by an MIT-style license that can be
 * found in the LICENSE file at https://angular.io/license
 */
import { Directive, Inject, Optional, Self, forwardRef } from '@angular/core';
import { EventEmitter, ObservableWrapper } from '../../facade/async';
import { ListWrapper, StringMapWrapper } from '../../facade/collection';
import { BaseException } from '../../facade/exceptions';
import { isBlank } from '../../facade/lang';
import { NG_ASYNC_VALIDATORS, NG_VALIDATORS, Validators } from '../validators';
import { ControlContainer } from './control_container';
import { composeAsyncValidators, composeValidators, setUpControl, setUpControlGroup } from './shared';
export const formDirectiveProvider = 
/*@ts2dart_const*/ /* @ts2dart_Provider */ {
    provide: ControlContainer,
    useExisting: forwardRef(() => NgFormModel)
};
let _formModelWarningDisplayed = false;
export class NgFormModel extends ControlContainer {
    constructor(_validators, _asyncValidators) {
        super();
        this._validators = _validators;
        this._asyncValidators = _asyncValidators;
        this._submitted = false;
        this.form = null;
        this.directives = [];
        this.ngSubmit = new EventEmitter();
        this._displayWarning();
    }
    _displayWarning() {
        // TODO(kara): Update this when the new forms module becomes the default
        if (!_formModelWarningDisplayed) {
            _formModelWarningDisplayed = true;
            console.warn(`
      *It looks like you're using the old forms module. This will be opt-in in the next RC, and
      will eventually be removed in favor of the new forms module. For more information, see:
      https://docs.google.com/document/u/1/d/1RIezQqE4aEhBRmArIAS1mRIZtWFf6JxN_7B4meyWK0Y/pub
    `);
        }
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
        var ctrl = this.form.find(dir.path);
        setUpControl(ctrl, dir);
        ctrl.updateValueAndValidity({ emitEvent: false });
        this.directives.push(dir);
    }
    getControl(dir) { return this.form.find(dir.path); }
    removeControl(dir) { ListWrapper.remove(this.directives, dir); }
    addControlGroup(dir) {
        var ctrl = this.form.find(dir.path);
        setUpControlGroup(ctrl, dir);
        ctrl.updateValueAndValidity({ emitEvent: false });
    }
    removeControlGroup(dir) { }
    getControlGroup(dir) {
        return this.form.find(dir.path);
    }
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
            throw new BaseException(`ngFormModel expects a form. Please pass one in. Example: <form [ngFormModel]="myCoolForm">`);
        }
    }
}
/** @nocollapse */
NgFormModel.decorators = [
    { type: Directive, args: [{
                selector: '[ngFormModel]',
                providers: [formDirectiveProvider],
                inputs: ['form: ngFormModel'],
                host: { '(submit)': 'onSubmit()' },
                outputs: ['ngSubmit'],
                exportAs: 'ngForm'
            },] },
];
/** @nocollapse */
NgFormModel.ctorParameters = [
    { type: Array, decorators: [{ type: Optional }, { type: Self }, { type: Inject, args: [NG_VALIDATORS,] },] },
    { type: Array, decorators: [{ type: Optional }, { type: Self }, { type: Inject, args: [NG_ASYNC_VALIDATORS,] },] },
];
//# sourceMappingURL=ng_form_model.js.map