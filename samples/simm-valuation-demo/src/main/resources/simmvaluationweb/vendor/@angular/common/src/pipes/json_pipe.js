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
var JsonPipe = (function () {
    function JsonPipe() {
    }
    JsonPipe.prototype.transform = function (value) { return lang_1.Json.stringify(value); };
    /** @nocollapse */
    JsonPipe.decorators = [
        { type: core_1.Pipe, args: [{ name: 'json', pure: false },] },
    ];
    return JsonPipe;
}());
exports.JsonPipe = JsonPipe;
//# sourceMappingURL=json_pipe.js.map