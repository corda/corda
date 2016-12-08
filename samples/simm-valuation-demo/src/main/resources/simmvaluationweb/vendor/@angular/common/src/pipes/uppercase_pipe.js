/**
 * @license
 * Copyright Google Inc. All Rights Reserved.
 *
 * Use of this source code is governed by an MIT-style license that can be
 * found in the LICENSE file at https://angular.io/license
 */
"use strict";
var core_1 = require('@angular/core');
var lang_1 = require('../facade/lang');
var invalid_pipe_argument_exception_1 = require('./invalid_pipe_argument_exception');
var UpperCasePipe = (function () {
    function UpperCasePipe() {
    }
    UpperCasePipe.prototype.transform = function (value) {
        if (lang_1.isBlank(value))
            return value;
        if (!lang_1.isString(value)) {
            throw new invalid_pipe_argument_exception_1.InvalidPipeArgumentException(UpperCasePipe, value);
        }
        return value.toUpperCase();
    };
    /** @nocollapse */
    UpperCasePipe.decorators = [
        { type: core_1.Pipe, args: [{ name: 'uppercase' },] },
    ];
    return UpperCasePipe;
}());
exports.UpperCasePipe = UpperCasePipe;
//# sourceMappingURL=uppercase_pipe.js.map