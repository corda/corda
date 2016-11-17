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
var ng_control_1 = require('./ng_control');
exports.RADIO_VALUE_ACCESSOR = {
    provide: control_value_accessor_1.NG_VALUE_ACCESSOR,
    useExisting: core_1.forwardRef(function () { return RadioControlValueAccessor; }),
    multi: true
};
var RadioControlRegistry = (function () {
    function RadioControlRegistry() {
        this._accessors = [];
    }
    RadioControlRegistry.prototype.add = function (control, accessor) {
        this._accessors.push([control, accessor]);
    };
    RadioControlRegistry.prototype.remove = function (accessor) {
        var indexToRemove = -1;
        for (var i = 0; i < this._accessors.length; ++i) {
            if (this._accessors[i][1] === accessor) {
                indexToRemove = i;
            }
        }
        collection_1.ListWrapper.removeAt(this._accessors, indexToRemove);
    };
    RadioControlRegistry.prototype.select = function (accessor) {
        var _this = this;
        this._accessors.forEach(function (c) {
            if (_this._isSameGroup(c, accessor) && c[1] !== accessor) {
                c[1].fireUncheck();
            }
        });
    };
    RadioControlRegistry.prototype._isSameGroup = function (controlPair, accessor) {
        return controlPair[0].control.root === accessor._control.control.root &&
            controlPair[1].name === accessor.name;
    };
    /** @nocollapse */
    RadioControlRegistry.decorators = [
        { type: core_1.Injectable },
    ];
    return RadioControlRegistry;
}());
exports.RadioControlRegistry = RadioControlRegistry;
/**
 * The value provided by the forms API for radio buttons.
 *
 * @experimental
 */
var RadioButtonState = (function () {
    function RadioButtonState(checked, value) {
        this.checked = checked;
        this.value = value;
    }
    return RadioButtonState;
}());
exports.RadioButtonState = RadioButtonState;
var RadioControlValueAccessor = (function () {
    function RadioControlValueAccessor(_renderer, _elementRef, _registry, _injector) {
        this._renderer = _renderer;
        this._elementRef = _elementRef;
        this._registry = _registry;
        this._injector = _injector;
        this.onChange = function () { };
        this.onTouched = function () { };
    }
    RadioControlValueAccessor.prototype.ngOnInit = function () {
        this._control = this._injector.get(ng_control_1.NgControl);
        this._registry.add(this._control, this);
    };
    RadioControlValueAccessor.prototype.ngOnDestroy = function () { this._registry.remove(this); };
    RadioControlValueAccessor.prototype.writeValue = function (value) {
        this._state = value;
        if (lang_1.isPresent(value) && value.checked) {
            this._renderer.setElementProperty(this._elementRef.nativeElement, 'checked', true);
        }
    };
    RadioControlValueAccessor.prototype.registerOnChange = function (fn) {
        var _this = this;
        this._fn = fn;
        this.onChange = function () {
            fn(new RadioButtonState(true, _this._state.value));
            _this._registry.select(_this);
        };
    };
    RadioControlValueAccessor.prototype.fireUncheck = function () { this._fn(new RadioButtonState(false, this._state.value)); };
    RadioControlValueAccessor.prototype.registerOnTouched = function (fn) { this.onTouched = fn; };
    /** @nocollapse */
    RadioControlValueAccessor.decorators = [
        { type: core_1.Directive, args: [{
                    selector: 'input[type=radio][ngControl],input[type=radio][ngFormControl],input[type=radio][ngModel]',
                    host: { '(change)': 'onChange()', '(blur)': 'onTouched()' },
                    providers: [exports.RADIO_VALUE_ACCESSOR]
                },] },
    ];
    /** @nocollapse */
    RadioControlValueAccessor.ctorParameters = [
        { type: core_1.Renderer, },
        { type: core_1.ElementRef, },
        { type: RadioControlRegistry, },
        { type: core_1.Injector, },
    ];
    /** @nocollapse */
    RadioControlValueAccessor.propDecorators = {
        'name': [{ type: core_1.Input },],
    };
    return RadioControlValueAccessor;
}());
exports.RadioControlValueAccessor = RadioControlValueAccessor;
//# sourceMappingURL=radio_control_value_accessor.js.map