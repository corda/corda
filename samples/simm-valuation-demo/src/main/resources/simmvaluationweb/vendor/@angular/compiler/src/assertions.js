/**
 * @license
 * Copyright Google Inc. All Rights Reserved.
 *
 * Use of this source code is governed by an MIT-style license that can be
 * found in the LICENSE file at https://angular.io/license
 */
"use strict";
var core_1 = require('@angular/core');
var exceptions_1 = require('../src/facade/exceptions');
var lang_1 = require('../src/facade/lang');
function assertArrayOfStrings(identifier, value) {
    if (!core_1.isDevMode() || lang_1.isBlank(value)) {
        return;
    }
    if (!lang_1.isArray(value)) {
        throw new exceptions_1.BaseException("Expected '" + identifier + "' to be an array of strings.");
    }
    for (var i = 0; i < value.length; i += 1) {
        if (!lang_1.isString(value[i])) {
            throw new exceptions_1.BaseException("Expected '" + identifier + "' to be an array of strings.");
        }
    }
}
exports.assertArrayOfStrings = assertArrayOfStrings;
var INTERPOLATION_BLACKLIST_REGEXPS = [
    /^\s*$/,
    /[<>]/,
    /^[{}]$/,
    /&(#|[a-z])/i,
];
function assertInterpolationSymbols(identifier, value) {
    if (core_1.isDevMode() && !lang_1.isBlank(value) && (!lang_1.isArray(value) || value.length != 2)) {
        throw new exceptions_1.BaseException("Expected '" + identifier + "' to be an array, [start, end].");
    }
    else if (core_1.isDevMode() && !lang_1.isBlank(value)) {
        var start_1 = value[0];
        var end_1 = value[1];
        // black list checking
        INTERPOLATION_BLACKLIST_REGEXPS.forEach(function (regexp) {
            if (regexp.test(start_1) || regexp.test(end_1)) {
                throw new exceptions_1.BaseException("['" + start_1 + "', '" + end_1 + "'] contains unusable interpolation symbol.");
            }
        });
    }
}
exports.assertInterpolationSymbols = assertInterpolationSymbols;
//# sourceMappingURL=assertions.js.map