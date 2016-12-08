/**
 * @license
 * Copyright Google Inc. All Rights Reserved.
 *
 * Use of this source code is governed by an MIT-style license that can be
 * found in the LICENSE file at https://angular.io/license
 */
import { Directive, ElementRef, Renderer, forwardRef } from '@angular/core';
import { NG_VALUE_ACCESSOR } from './control_value_accessor';
export const CHECKBOX_VALUE_ACCESSOR = {
    provide: NG_VALUE_ACCESSOR,
    useExisting: forwardRef(() => CheckboxControlValueAccessor),
    multi: true
};
export class CheckboxControlValueAccessor {
    constructor(_renderer, _elementRef) {
        this._renderer = _renderer;
        this._elementRef = _elementRef;
        this.onChange = (_) => { };
        this.onTouched = () => { };
    }
    writeValue(value) {
        this._renderer.setElementProperty(this._elementRef.nativeElement, 'checked', value);
    }
    registerOnChange(fn) { this.onChange = fn; }
    registerOnTouched(fn) { this.onTouched = fn; }
}
/** @nocollapse */
CheckboxControlValueAccessor.decorators = [
    { type: Directive, args: [{
                selector: 'input[type=checkbox][ngControl],input[type=checkbox][ngFormControl],input[type=checkbox][ngModel]',
                host: { '(change)': 'onChange($event.target.checked)', '(blur)': 'onTouched()' },
                providers: [CHECKBOX_VALUE_ACCESSOR]
            },] },
];
/** @nocollapse */
CheckboxControlValueAccessor.ctorParameters = [
    { type: Renderer, },
    { type: ElementRef, },
];
//# sourceMappingURL=checkbox_value_accessor.js.map