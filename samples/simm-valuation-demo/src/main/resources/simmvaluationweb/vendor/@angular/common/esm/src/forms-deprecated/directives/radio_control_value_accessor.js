/**
 * @license
 * Copyright Google Inc. All Rights Reserved.
 *
 * Use of this source code is governed by an MIT-style license that can be
 * found in the LICENSE file at https://angular.io/license
 */
import { Directive, ElementRef, Injectable, Injector, Input, Renderer, forwardRef } from '@angular/core';
import { ListWrapper } from '../../facade/collection';
import { isPresent } from '../../facade/lang';
import { NG_VALUE_ACCESSOR } from './control_value_accessor';
import { NgControl } from './ng_control';
export const RADIO_VALUE_ACCESSOR = {
    provide: NG_VALUE_ACCESSOR,
    useExisting: forwardRef(() => RadioControlValueAccessor),
    multi: true
};
export class RadioControlRegistry {
    constructor() {
        this._accessors = [];
    }
    add(control, accessor) {
        this._accessors.push([control, accessor]);
    }
    remove(accessor) {
        var indexToRemove = -1;
        for (var i = 0; i < this._accessors.length; ++i) {
            if (this._accessors[i][1] === accessor) {
                indexToRemove = i;
            }
        }
        ListWrapper.removeAt(this._accessors, indexToRemove);
    }
    select(accessor) {
        this._accessors.forEach((c) => {
            if (this._isSameGroup(c, accessor) && c[1] !== accessor) {
                c[1].fireUncheck();
            }
        });
    }
    _isSameGroup(controlPair, accessor) {
        return controlPair[0].control.root === accessor._control.control.root &&
            controlPair[1].name === accessor.name;
    }
}
/** @nocollapse */
RadioControlRegistry.decorators = [
    { type: Injectable },
];
/**
 * The value provided by the forms API for radio buttons.
 *
 * @experimental
 */
export class RadioButtonState {
    constructor(checked, value) {
        this.checked = checked;
        this.value = value;
    }
}
export class RadioControlValueAccessor {
    constructor(_renderer, _elementRef, _registry, _injector) {
        this._renderer = _renderer;
        this._elementRef = _elementRef;
        this._registry = _registry;
        this._injector = _injector;
        this.onChange = () => { };
        this.onTouched = () => { };
    }
    ngOnInit() {
        this._control = this._injector.get(NgControl);
        this._registry.add(this._control, this);
    }
    ngOnDestroy() { this._registry.remove(this); }
    writeValue(value) {
        this._state = value;
        if (isPresent(value) && value.checked) {
            this._renderer.setElementProperty(this._elementRef.nativeElement, 'checked', true);
        }
    }
    registerOnChange(fn) {
        this._fn = fn;
        this.onChange = () => {
            fn(new RadioButtonState(true, this._state.value));
            this._registry.select(this);
        };
    }
    fireUncheck() { this._fn(new RadioButtonState(false, this._state.value)); }
    registerOnTouched(fn) { this.onTouched = fn; }
}
/** @nocollapse */
RadioControlValueAccessor.decorators = [
    { type: Directive, args: [{
                selector: 'input[type=radio][ngControl],input[type=radio][ngFormControl],input[type=radio][ngModel]',
                host: { '(change)': 'onChange()', '(blur)': 'onTouched()' },
                providers: [RADIO_VALUE_ACCESSOR]
            },] },
];
/** @nocollapse */
RadioControlValueAccessor.ctorParameters = [
    { type: Renderer, },
    { type: ElementRef, },
    { type: RadioControlRegistry, },
    { type: Injector, },
];
/** @nocollapse */
RadioControlValueAccessor.propDecorators = {
    'name': [{ type: Input },],
};
//# sourceMappingURL=radio_control_value_accessor.js.map