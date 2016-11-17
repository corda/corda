/**
 * @license
 * Copyright Google Inc. All Rights Reserved.
 *
 * Use of this source code is governed by an MIT-style license that can be
 * found in the LICENSE file at https://angular.io/license
 */
"use strict";
var core_1 = require('@angular/core');
var lang_1 = require('../../facade/lang');
var control_value_accessor_1 = require('./control_value_accessor');
exports.NUMBER_VALUE_ACCESSOR = {
    provide: control_value_accessor_1.NG_VALUE_ACCESSOR,
    useExisting: core_1.forwardRef(function () { return NumberValueAccessor; }),
    multi: true
};
var NumberValueAccessor = (function () {
    function NumberValueAccessor(_renderer, _elementRef) {
        this._renderer = _renderer;
        this._elementRef = _elementRef;
        this.onChange = function (_) { };
        this.onTouched = function () { };
    }
    NumberValueAccessor.prototype.writeValue = function (value) {
        this._renderer.setElementProperty(this._elementRef.nativeElement, 'value', value);
    };
    NumberValueAccessor.prototype.registerOnChange = function (fn) {
        this.onChange = function (value) { fn(value == '' ? null : lang_1.NumberWrapper.parseFloat(value)); };
    };
    NumberValueAccessor.prototype.registerOnTouched = function (fn) { this.onTouched = fn; };
    /** @nocollapse */
    NumberValueAccessor.decorators = [
        { type: core_1.Directive, args: [{
                    selector: 'input[type=number][ngControl],input[type=number][ngFormControl],input[type=number][ngModel]',
                    host: {
                        '(change)': 'onChange($event.target.value)',
                        '(input)': 'onChange($event.target.value)',
                        '(blur)': 'onTouched()'
                    },
                    providers: [exports.NUMBER_VALUE_ACCESSOR]
                },] },
    ];
    /** @nocollapse */
    NumberValueAccessor.ctorParameters = [
        { type: core_1.Renderer, },
        { type: core_1.ElementRef, },
    ];
    return NumberValueAccessor;
}());
exports.NumberValueAccessor = NumberValueAccessor;
//# sourceMappingURL=number_value_accessor.js.map