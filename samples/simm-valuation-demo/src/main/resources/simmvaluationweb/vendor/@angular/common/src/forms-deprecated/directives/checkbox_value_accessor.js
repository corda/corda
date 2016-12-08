/**
 * @license
 * Copyright Google Inc. All Rights Reserved.
 *
 * Use of this source code is governed by an MIT-style license that can be
 * found in the LICENSE file at https://angular.io/license
 */
"use strict";
var core_1 = require('@angular/core');
var control_value_accessor_1 = require('./control_value_accessor');
exports.CHECKBOX_VALUE_ACCESSOR = {
    provide: control_value_accessor_1.NG_VALUE_ACCESSOR,
    useExisting: core_1.forwardRef(function () { return CheckboxControlValueAccessor; }),
    multi: true
};
var CheckboxControlValueAccessor = (function () {
    function CheckboxControlValueAccessor(_renderer, _elementRef) {
        this._renderer = _renderer;
        this._elementRef = _elementRef;
        this.onChange = function (_) { };
        this.onTouched = function () { };
    }
    CheckboxControlValueAccessor.prototype.writeValue = function (value) {
        this._renderer.setElementProperty(this._elementRef.nativeElement, 'checked', value);
    };
    CheckboxControlValueAccessor.prototype.registerOnChange = function (fn) { this.onChange = fn; };
    CheckboxControlValueAccessor.prototype.registerOnTouched = function (fn) { this.onTouched = fn; };
    /** @nocollapse */
    CheckboxControlValueAccessor.decorators = [
        { type: core_1.Directive, args: [{
                    selector: 'input[type=checkbox][ngControl],input[type=checkbox][ngFormControl],input[type=checkbox][ngModel]',
                    host: { '(change)': 'onChange($event.target.checked)', '(blur)': 'onTouched()' },
                    providers: [exports.CHECKBOX_VALUE_ACCESSOR]
                },] },
    ];
    /** @nocollapse */
    CheckboxControlValueAccessor.ctorParameters = [
        { type: core_1.Renderer, },
        { type: core_1.ElementRef, },
    ];
    return CheckboxControlValueAccessor;
}());
exports.CheckboxControlValueAccessor = CheckboxControlValueAccessor;
//# sourceMappingURL=checkbox_value_accessor.js.map