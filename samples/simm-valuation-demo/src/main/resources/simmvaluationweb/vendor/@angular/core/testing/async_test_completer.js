/**
 * @license
 * Copyright Google Inc. All Rights Reserved.
 *
 * Use of this source code is governed by an MIT-style license that can be
 * found in the LICENSE file at https://angular.io/license
 */
"use strict";
var promise_1 = require('../src/facade/promise');
/**
 * Injectable completer that allows signaling completion of an asynchronous test. Used internally.
 */
var AsyncTestCompleter = (function () {
    function AsyncTestCompleter() {
        this._completer = new promise_1.PromiseCompleter();
    }
    AsyncTestCompleter.prototype.done = function (value) { this._completer.resolve(value); };
    AsyncTestCompleter.prototype.fail = function (error, stackTrace) { this._completer.reject(error, stackTrace); };
    Object.defineProperty(AsyncTestCompleter.prototype, "promise", {
        get: function () { return this._completer.promise; },
        enumerable: true,
        configurable: true
    });
    return AsyncTestCompleter;
}());
exports.AsyncTestCompleter = AsyncTestCompleter;
//# sourceMappingURL=async_test_completer.js.map