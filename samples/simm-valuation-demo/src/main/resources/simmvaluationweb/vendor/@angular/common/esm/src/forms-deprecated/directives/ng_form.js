/**
 * @license
 * Copyright Google Inc. All Rights Reserved.
 *
 * Use of this source code is governed by an MIT-style license that can be
 * found in the LICENSE file at https://angular.io/license
 */
import { Directive, Inject, Optional, Self, forwardRef } from '@angular/core';
import { EventEmitter, ObservableWrapper, PromiseWrapper } from '../../facade/async';
import { ListWrapper } from '../../facade/collection';
import { isPresent } from '../../facade/lang';
import { Control, ControlGroup } from '../model';
import { NG_ASYNC_VALIDATORS, NG_VALIDATORS } from '../validators';
import { ControlContainer } from './control_container';
import { composeAsyncValidators, composeValidators, setUpControl, setUpControlGroup } from './shared';
export const formDirectiveProvider = 
/*@ts2dart_const*/ { provide: ControlContainer, useExisting: forwardRef(() => NgForm) };
let _formWarningDisplayed = false;
export class NgForm extends ControlContainer {
    constructor(validators, asyncValidators) {
        super();
        this._submitted = false;
        this.ngSubmit = new EventEmitter();
        this._displayWarning();
        this.form = new ControlGroup({}, null, composeValidators(validators), composeAsyncValidators(asyncValidators));
    }
    _displayWarning() {
        // TODO(kara): Update this when the new forms module becomes the default
        if (!_formWarningDisplayed) {
            _formWarningDisplayed = true;
            console.warn(`
      *It looks like you're using the old forms module. This will be opt-in in the next RC, and
      will eventually be removed in favor of the new forms module. For more information, see:
      https://docs.google.com/document/u/1/d/1RIezQqE4aEhBRmArIAS1mRIZtWFf6JxN_7B4meyWK0Y/pub
    `);
        }
    }
    get submitted() { return this._submitted; }
    get formDirective() { return this; }
    get control() { return this.form; }
    get path() { return []; }
    get controls() { return this.form.controls; }
    addControl(dir) {
        PromiseWrapper.scheduleMicrotask(() => {
            var container = this._findContainer(dir.path);
            var ctrl = new Control();
            setUpControl(ctrl, dir);
            container.registerControl(dir.name, ctrl);
            ctrl.updateValueAndValidity({ emitEvent: false });
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
    addControlGroup(dir) {
        PromiseWrapper.scheduleMicrotask(() => {
            var container = this._findContainer(dir.path);
            var group = new ControlGroup({});
            setUpControlGroup(group, dir);
            container.registerControl(dir.name, group);
            group.updateValueAndValidity({ emitEvent: false });
        });
    }
    removeControlGroup(dir) {
        PromiseWrapper.scheduleMicrotask(() => {
            var container = this._findContainer(dir.path);
            if (isPresent(container)) {
                container.removeControl(dir.name);
            }
        });
    }
    getControlGroup(dir) {
        return this.form.find(dir.path);
    }
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
                selector: 'form:not([ngNoForm]):not([ngFormModel]),ngForm,[ngForm]',
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