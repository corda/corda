/**
 * @license
 * Copyright Google Inc. All Rights Reserved.
 *
 * Use of this source code is governed by an MIT-style license that can be
 * found in the LICENSE file at https://angular.io/license
 */
import { Pipe } from '@angular/core';
import { ListWrapper } from '../facade/collection';
import { StringWrapper, isArray, isBlank, isString } from '../facade/lang';
import { InvalidPipeArgumentException } from './invalid_pipe_argument_exception';
export class SlicePipe {
    transform(value, start, end = null) {
        if (isBlank(value))
            return value;
        if (!this.supports(value)) {
            throw new InvalidPipeArgumentException(SlicePipe, value);
        }
        if (isString(value)) {
            return StringWrapper.slice(value, start, end);
        }
        return ListWrapper.slice(value, start, end);
    }
    supports(obj) { return isString(obj) || isArray(obj); }
}
/** @nocollapse */
SlicePipe.decorators = [
    { type: Pipe, args: [{ name: 'slice', pure: false },] },
];
//# sourceMappingURL=slice_pipe.js.map