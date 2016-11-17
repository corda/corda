/**
 * @license
 * Copyright Google Inc. All Rights Reserved.
 *
 * Use of this source code is governed by an MIT-style license that can be
 * found in the LICENSE file at https://angular.io/license
 */
import { Directive, ElementRef, Host, Input, Optional, Renderer, forwardRef } from '@angular/core';
import { MapWrapper } from '../../facade/collection';
import { StringWrapper, isBlank, isPresent, isPrimitive, isString, looseIdentical } from '../../facade/lang';
import { NG_VALUE_ACCESSOR } from './control_value_accessor';
const SELECT_MULTIPLE_VALUE_ACCESSOR = {
    provide: NG_VALUE_ACCESSOR,
    useExisting: forwardRef(() => SelectMultipleControlValueAccessor),
    multi: true
};
function _buildValueString(id, value) {
    if (isBlank(id))
        return `${value}`;
    if (isString(value))
        value = `'${value}'`;
    if (!isPrimitive(value))
        value = 'Object';
    return StringWrapper.slice(`${id}: ${value}`, 0, 50);
}
function _extractId(valueString) {
    return valueString.split(':')[0];
}
/** Mock interface for HTMLCollection */
class HTMLCollection {
}
export class SelectMultipleControlValueAccessor {
    constructor() {
        /** @internal */
        this._optionMap = new Map();
        /** @internal */
        this._idCounter = 0;
        this.onChange = (_) => { };
        this.onTouched = () => { };
    }
    writeValue(value) {
        this.value = value;
        if (value == null)
            return;
        let values = value;
        // convert values to ids
        let ids = values.map((v) => this._getOptionId(v));
        this._optionMap.forEach((opt, o) => { opt._setSelected(ids.indexOf(o.toString()) > -1); });
    }
    registerOnChange(fn) {
        this.onChange = (_) => {
            let selected = [];
            if (_.hasOwnProperty('selectedOptions')) {
                let options = _.selectedOptions;
                for (var i = 0; i < options.length; i++) {
                    let opt = options.item(i);
                    let val = this._getOptionValue(opt.value);
                    selected.push(val);
                }
            }
            else {
                let options = _.options;
                for (var i = 0; i < options.length; i++) {
                    let opt = options.item(i);
                    if (opt.selected) {
                        let val = this._getOptionValue(opt.value);
                        selected.push(val);
                    }
                }
            }
            fn(selected);
        };
    }
    registerOnTouched(fn) { this.onTouched = fn; }
    /** @internal */
    _registerOption(value) {
        let id = (this._idCounter++).toString();
        this._optionMap.set(id, value);
        return id;
    }
    /** @internal */
    _getOptionId(value) {
        for (let id of MapWrapper.keys(this._optionMap)) {
            if (looseIdentical(this._optionMap.get(id)._value, value))
                return id;
        }
        return null;
    }
    /** @internal */
    _getOptionValue(valueString) {
        let opt = this._optionMap.get(_extractId(valueString));
        return isPresent(opt) ? opt._value : valueString;
    }
}
/** @nocollapse */
SelectMultipleControlValueAccessor.decorators = [
    { type: Directive, args: [{
                selector: 'select[multiple][ngControl],select[multiple][ngFormControl],select[multiple][ngModel]',
                host: { '(input)': 'onChange($event.target)', '(blur)': 'onTouched()' },
                providers: [SELECT_MULTIPLE_VALUE_ACCESSOR]
            },] },
];
/** @nocollapse */
SelectMultipleControlValueAccessor.ctorParameters = [];
export class NgSelectMultipleOption {
    constructor(_element, _renderer, _select) {
        this._element = _element;
        this._renderer = _renderer;
        this._select = _select;
        if (isPresent(this._select)) {
            this.id = this._select._registerOption(this);
        }
    }
    set ngValue(value) {
        if (this._select == null)
            return;
        this._value = value;
        this._setElementValue(_buildValueString(this.id, value));
        this._select.writeValue(this._select.value);
    }
    set value(value) {
        if (isPresent(this._select)) {
            this._value = value;
            this._setElementValue(_buildValueString(this.id, value));
            this._select.writeValue(this._select.value);
        }
        else {
            this._setElementValue(value);
        }
    }
    /** @internal */
    _setElementValue(value) {
        this._renderer.setElementProperty(this._element.nativeElement, 'value', value);
    }
    /** @internal */
    _setSelected(selected) {
        this._renderer.setElementProperty(this._element.nativeElement, 'selected', selected);
    }
    ngOnDestroy() {
        if (isPresent(this._select)) {
            this._select._optionMap.delete(this.id);
            this._select.writeValue(this._select.value);
        }
    }
}
/** @nocollapse */
NgSelectMultipleOption.decorators = [
    { type: Directive, args: [{ selector: 'option' },] },
];
/** @nocollapse */
NgSelectMultipleOption.ctorParameters = [
    { type: ElementRef, },
    { type: Renderer, },
    { type: SelectMultipleControlValueAccessor, decorators: [{ type: Optional }, { type: Host },] },
];
/** @nocollapse */
NgSelectMultipleOption.propDecorators = {
    'ngValue': [{ type: Input, args: ['ngValue',] },],
    'value': [{ type: Input, args: ['value',] },],
};
export const SELECT_DIRECTIVES = [SelectMultipleControlValueAccessor, NgSelectMultipleOption];
//# sourceMappingURL=select_multiple_control_value_accessor.js.map