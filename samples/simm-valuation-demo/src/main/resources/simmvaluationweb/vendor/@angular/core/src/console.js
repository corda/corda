/**
 * @license
 * Copyright Google Inc. All Rights Reserved.
 *
 * Use of this source code is governed by an MIT-style license that can be
 * found in the LICENSE file at https://angular.io/license
 */
"use strict";
var decorators_1 = require('./di/decorators');
var lang_1 = require('./facade/lang');
// Note: Need to rename warn as in Dart
// class members and imports can't use the same name.
var _warnImpl = lang_1.warn;
var Console = (function () {
    function Console() {
    }
    Console.prototype.log = function (message) { lang_1.print(message); };
    // Note: for reporting errors use `DOM.logError()` as it is platform specific
    Console.prototype.warn = function (message) { _warnImpl(message); };
    /** @nocollapse */
    Console.decorators = [
        { type: decorators_1.Injectable },
    ];
    return Console;
}());
exports.Console = Console;
//# sourceMappingURL=console.js.map