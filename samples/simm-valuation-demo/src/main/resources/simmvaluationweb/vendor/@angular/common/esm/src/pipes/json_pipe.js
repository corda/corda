/**
 * @license
 * Copyright Google Inc. All Rights Reserved.
 *
 * Use of this source code is governed by an MIT-style license that can be
 * found in the LICENSE file at https://angular.io/license
 */
import { Pipe } from '@angular/core';
import { Json } from '../facade/lang';
export class JsonPipe {
    transform(value) { return Json.stringify(value); }
}
/** @nocollapse */
JsonPipe.decorators = [
    { type: Pipe, args: [{ name: 'json', pure: false },] },
];
//# sourceMappingURL=json_pipe.js.map