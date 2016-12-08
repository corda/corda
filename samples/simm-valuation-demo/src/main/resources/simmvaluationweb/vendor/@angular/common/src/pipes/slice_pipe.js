/**
 * @license
 * Copyright Google Inc. All Rights Reserved.
 *
 * Use of this source code is governed by an MIT-style license that can be
 * found in the LICENSE file at https://angular.io/license
 */
"use strict";
var core_1 = require('@angular/core');
var collection_1 = require('../facade/collection');
var lang_1 = require('../facade/lang');
var invalid_pipe_argument_exception_1 = require('./invalid_pipe_argument_exception');
var SlicePipe = (function () {
    function SlicePipe() {
    }
    SlicePipe.prototype.transform = function (value, start, end) {
        if (end === void 0) { end = null; }
        if (lang_1.isBlank(value))
            return value;
        if (!this.supports(value)) {
            throw new invalid_pipe_argument_exception_1.InvalidPipeArgumentException(SlicePipe, value);
        }
        if (lang_1.isString(value)) {
            return lang_1.StringWrapper.slice(value, start, end);
        }
        return collection_1.ListWrapper.slice(value, start, end);
    };
    SlicePipe.prototype.supports = function (obj) { return lang_1.isString(obj) || lang_1.isArray(obj); };
    /** @nocollapse */
    SlicePipe.decorators = [
        { type: core_1.Pipe, args: [{ name: 'slice', pure: false },] },
    ];
    return SlicePipe;
}());
exports.SlicePipe = SlicePipe;
//# sourceMappingURL=slice_pipe.js.map