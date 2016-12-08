/**
 * @license
 * Copyright Google Inc. All Rights Reserved.
 *
 * Use of this source code is governed by an MIT-style license that can be
 * found in the LICENSE file at https://angular.io/license
 */
import { Pipe } from '@angular/core';
import { StringWrapper, isBlank, isStringMap } from '../facade/lang';
import { NgLocalization, getPluralCategory } from '../localization';
import { InvalidPipeArgumentException } from './invalid_pipe_argument_exception';
const _INTERPOLATION_REGEXP = /#/g;
export class I18nPluralPipe {
    constructor(_localization) {
        this._localization = _localization;
    }
    transform(value, pluralMap) {
        if (isBlank(value))
            return '';
        if (!isStringMap(pluralMap)) {
            throw new InvalidPipeArgumentException(I18nPluralPipe, pluralMap);
        }
        const key = getPluralCategory(value, Object.getOwnPropertyNames(pluralMap), this._localization);
        return StringWrapper.replaceAll(pluralMap[key], _INTERPOLATION_REGEXP, value.toString());
    }
}
/** @nocollapse */
I18nPluralPipe.decorators = [
    { type: Pipe, args: [{ name: 'i18nPlural', pure: true },] },
];
/** @nocollapse */
I18nPluralPipe.ctorParameters = [
    { type: NgLocalization, },
];
//# sourceMappingURL=i18n_plural_pipe.js.map