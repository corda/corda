/**
 * @license
 * Copyright Google Inc. All Rights Reserved.
 *
 * Use of this source code is governed by an MIT-style license that can be
 * found in the LICENSE file at https://angular.io/license
 */
"use strict";
var core_1 = require('@angular/core');
var collection_1 = require('../../facade/collection');
var lang_1 = require('../../facade/lang');
var control_value_accessor_1 = require('./control_value_accessor');
exports.SELECT_VALUE_ACCESSOR = {
    provide: control_value_accessor_1.NG_VALUE_ACCESSOR,
    useExisting: core_1.forwardRef(function () { return SelectControlValueAccessor; }),
    multi: true
};
function _buildValueString(id, value) {
    if (lang_1.isBlank(id))
        return "" + value;
    if (!lang_1.isPrimitive(value))
        value = 'Object';
    return lang_1.StringWrapper.slice(id + ": " + value, 0, 50);
}
function _extractId(valueString) {
    return valueString.split(':')[0];
}
var SelectControlValueAccessor = (function () {
    function SelectControlValueAccessor(_renderer, _elementRef) {
        this._renderer = _renderer;
        this._elementRef = _elementRef;
        /** @internal */
        this._optionMap = new Map();
        /** @internal */
        this._idCounter = 0;
        this.onChange = function (_) { };
        this.onTouched = function () { };
    }
    SelectControlValueAccessor.prototype.writeValue = function (value) {
        this.value = value;
        var valueString = _buildValueString(this._getOptionId(value), value);
        this._renderer.setElementProperty(this._elementRef.nativeElement, 'value', valueString);
    };
    SelectControlValueAccessor.prototype.registerOnChange = function (fn) {
        var _this = this;
        this.onChange = function (valueString) {
            _this.value = valueString;
            fn(_this._getOptionValue(valueString));
        };
    };
    SelectControlValueAccessor.prototype.registerOnTouched = function (fn) { this.onTouched = fn; };
    /** @internal */
    SelectControlValueAccessor.prototype._registerOption = function () { return (this._idCounter++).toString(); };
    /** @internal */
    SelectControlValueAccessor.prototype._getOptionId = function (value) {
        for (var _i = 0, _a = collection_1.MapWrapper.keys(this._optionMap); _i < _a.length; _i++) {
            var id = _a[_i];
            if (lang_1.looseIdentical(this._optionMap.get(id), value))
                return id;
        }
        return null;
    };
    /** @internal */
    SelectControlValueAccessor.prototype._getOptionValue = function (valueString) {
        var value = this._optionMap.get(_extractId(valueString));
        return lang_1.isPresent(value) ? value : valueString;
    };
    /** @nocollapse */
    SelectControlValueAccessor.decorators = [
        { type: core_1.Directive, args: [{
                    selector: 'select:not([multiple])[ngControl],select:not([multiple])[ngFormControl],select:not([multiple])[ngModel]',
                    host: { '(change)': 'onChange($event.target.value)', '(blur)': 'onTouched()' },
                    providers: [exports.SELECT_VALUE_ACCESSOR]
                },] },
    ];
    /** @nocollapse */
    SelectControlValueAccessor.ctorParameters = [
        { type: core_1.Renderer, },
        { type: core_1.ElementRef, },
    ];
    return SelectControlValueAccessor;
}());
exports.SelectControlValueAccessor = SelectControlValueAccessor;
var NgSelectOption = (function () {
    function NgSelectOption(_element, _renderer, _select) {
        this._element = _element;
        this._renderer = _renderer;
        this._select = _select;
        if (lang_1.isPresent(this._select))
            this.id = this._select._registerOption();
    }
    Object.defineProperty(NgSelectOption.prototype, "ngValue", {
        set: function (value) {
            if (this._select == null)
                return;
            this._select._optionMap.set(this.id, value);
            this._setElementValue(_buildValueString(this.id, value));
            this._select.writeValue(this._select.value);
        },
        enumerable: true,
        configurable: true
    });
    Object.defineProperty(NgSelectOption.prototype, "value", {
        set: function (value) {
            this._setElementValue(value);
            if (lang_1.isPresent(this._select))
                this._select.writeValue(this._select.value);
        },
        enumerable: true,
        configurable: true
    });
    /** @internal */
    NgSelectOption.prototype._setElementValue = function (value) {
        this._renderer.setElementProperty(this._element.nativeElement, 'value', value);
    };
    NgSelectOption.prototype.ngOnDestroy = function () {
        if (lang_1.isPresent(this._select)) {
            this._select._optionMap.delete(this.id);
            this._select.writeValue(this._select.value);
        }
    };
    /** @nocollapse */
    NgSelectOption.decorators = [
        { type: core_1.Directive, args: [{ selector: 'option' },] },
    ];
    /** @nocollapse */
    NgSelectOption.ctorParameters = [
        { type: core_1.ElementRef, },
        { type: core_1.Renderer, },
        { type: SelectControlValueAccessor, decorators: [{ type: core_1.Optional }, { type: core_1.Host },] },
    ];
    /** @nocollapse */
    NgSelectOption.propDecorators = {
        'ngValue': [{ type: core_1.Input, args: ['ngValue',] },],
        'value': [{ type: core_1.Input, args: ['value',] },],
    };
    return NgSelectOption;
}());
exports.NgSelectOption = NgSelectOption;
//# sourceMappingURL=select_control_value_accessor.js.map