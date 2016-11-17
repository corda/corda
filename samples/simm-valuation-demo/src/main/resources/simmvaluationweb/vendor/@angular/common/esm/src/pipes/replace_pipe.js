/**
 * @license
 * Copyright Google Inc. All Rights Reserved.
 *
 * Use of this source code is governed by an MIT-style license that can be
 * found in the LICENSE file at https://angular.io/license
 */
import { Pipe } from '@angular/core';
import { RegExpWrapper, StringWrapper, isBlank, isFunction, isNumber, isString } from '../facade/lang';
import { InvalidPipeArgumentException } from './invalid_pipe_argument_exception';
export class ReplacePipe {
    transform(value, pattern, replacement) {
        if (isBlank(value)) {
            return value;
        }
        if (!this._supportedInput(value)) {
            throw new InvalidPipeArgumentException(ReplacePipe, value);
        }
        var input = value.toString();
        if (!this._supportedPattern(pattern)) {
            throw new InvalidPipeArgumentException(ReplacePipe, pattern);
        }
        if (!this._supportedReplacement(replacement)) {
            throw new InvalidPipeArgumentException(ReplacePipe, replacement);
        }
        if (isFunction(replacement)) {
            const rgxPattern = isString(pattern) ? RegExpWrapper.create(pattern) : pattern;
            return StringWrapper.replaceAllMapped(input, rgxPattern, replacement);
        }
        if (pattern instanceof RegExp) {
            // use the replaceAll variant
            return StringWrapper.replaceAll(input, pattern, replacement);
        }
        return StringWrapper.replace(input, pattern, replacement);
    }
    _supportedInput(input) { return isString(input) || isNumber(input); }
    _supportedPattern(pattern) {
        return isString(pattern) || pattern instanceof RegExp;
    }
    _supportedReplacement(replacement) {
        return isString(replacement) || isFunction(replacement);
    }
}
/** @nocollapse */
ReplacePipe.decorators = [
    { type: Pipe, args: [{ name: 'replace' },] },
];
//# sourceMappingURL=replace_pipe.js.map