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
var LowerCasePipe = (function () {
    function LowerCasePipe() {
    }
    LowerCasePipe.prototype.transform = function (value) {
        if (lang_1.isBlank(value))
            return value;
        if (!lang_1.isString(value)) {
            throw new invalid_pipe_argument_exception_1.InvalidPipeArgumentException(LowerCasePipe, value);
        }
        return value.toLowerCase();
    };
    /** @nocollapse */
    LowerCasePipe.decorators = [
        { type: core_1.Pipe, args: [{ name: 'lowercase' },] },
    ];
    return LowerCasePipe;
}());
exports.LowerCasePipe = LowerCasePipe;
//# sourceMappingURL=lowercase_pipe.js.map