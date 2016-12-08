/**
 * @license
 * Copyright Google Inc. All Rights Reserved.
 *
 * Use of this source code is governed by an MIT-style license that can be
 * found in the LICENSE file at https://angular.io/license
 */
import { Attribute, Directive, forwardRef } from '@angular/core';
import { NumberWrapper } from '../../facade/lang';
import { NG_VALIDATORS, Validators } from '../validators';
const REQUIRED = Validators.required;
export const REQUIRED_VALIDATOR = {
    provide: NG_VALIDATORS,
    useValue: REQUIRED,
    multi: true
};
export class RequiredValidator {
}
/** @nocollapse */
RequiredValidator.decorators = [
    { type: Directive, args: [{
                selector: '[required][ngControl],[required][ngFormControl],[required][ngModel]',
                providers: [REQUIRED_VALIDATOR]
            },] },
];
/**
 * Provivder which adds {@link MinLengthValidator} to {@link NG_VALIDATORS}.
 *
 * ## Example:
 *
 * {@example common/forms/ts/validators/validators.ts region='min'}
 */
export const MIN_LENGTH_VALIDATOR = {
    provide: NG_VALIDATORS,
    useExisting: forwardRef(() => MinLengthValidator),
    multi: true
};
export class MinLengthValidator {
    constructor(minLength) {
        this._validator = Validators.minLength(NumberWrapper.parseInt(minLength, 10));
    }
    validate(c) { return this._validator(c); }
}
/** @nocollapse */
MinLengthValidator.decorators = [
    { type: Directive, args: [{
                selector: '[minlength][ngControl],[minlength][ngFormControl],[minlength][ngModel]',
                providers: [MIN_LENGTH_VALIDATOR]
            },] },
];
/** @nocollapse */
MinLengthValidator.ctorParameters = [
    { type: undefined, decorators: [{ type: Attribute, args: ['minlength',] },] },
];
/**
 * Provider which adds {@link MaxLengthValidator} to {@link NG_VALIDATORS}.
 *
 * ## Example:
 *
 * {@example common/forms/ts/validators/validators.ts region='max'}
 */
export const MAX_LENGTH_VALIDATOR = {
    provide: NG_VALIDATORS,
    useExisting: forwardRef(() => MaxLengthValidator),
    multi: true
};
export class MaxLengthValidator {
    constructor(maxLength) {
        this._validator = Validators.maxLength(NumberWrapper.parseInt(maxLength, 10));
    }
    validate(c) { return this._validator(c); }
}
/** @nocollapse */
MaxLengthValidator.decorators = [
    { type: Directive, args: [{
                selector: '[maxlength][ngControl],[maxlength][ngFormControl],[maxlength][ngModel]',
                providers: [MAX_LENGTH_VALIDATOR]
            },] },
];
/** @nocollapse */
MaxLengthValidator.ctorParameters = [
    { type: undefined, decorators: [{ type: Attribute, args: ['maxlength',] },] },
];
export const PATTERN_VALIDATOR = {
    provide: NG_VALIDATORS,
    useExisting: forwardRef(() => PatternValidator),
    multi: true
};
export class PatternValidator {
    constructor(pattern) {
        this._validator = Validators.pattern(pattern);
    }
    validate(c) { return this._validator(c); }
}
/** @nocollapse */
PatternValidator.decorators = [
    { type: Directive, args: [{
                selector: '[pattern][ngControl],[pattern][ngFormControl],[pattern][ngModel]',
                providers: [PATTERN_VALIDATOR]
            },] },
];
/** @nocollapse */
PatternValidator.ctorParameters = [
    { type: undefined, decorators: [{ type: Attribute, args: ['pattern',] },] },
];
//# sourceMappingURL=validators.js.map