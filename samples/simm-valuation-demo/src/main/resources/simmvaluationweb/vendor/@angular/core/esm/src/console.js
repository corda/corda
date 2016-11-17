/**
 * @license
 * Copyright Google Inc. All Rights Reserved.
 *
 * Use of this source code is governed by an MIT-style license that can be
 * found in the LICENSE file at https://angular.io/license
 */
import { Injectable } from './di/decorators';
import { print, warn } from './facade/lang';
// Note: Need to rename warn as in Dart
// class members and imports can't use the same name.
let _warnImpl = warn;
export class Console {
    log(message) { print(message); }
    // Note: for reporting errors use `DOM.logError()` as it is platform specific
    warn(message) { _warnImpl(message); }
}
/** @nocollapse */
Console.decorators = [
    { type: Injectable },
];
//# sourceMappingURL=console.js.map