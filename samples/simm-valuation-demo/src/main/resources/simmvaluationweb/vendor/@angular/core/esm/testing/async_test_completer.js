/**
 * @license
 * Copyright Google Inc. All Rights Reserved.
 *
 * Use of this source code is governed by an MIT-style license that can be
 * found in the LICENSE file at https://angular.io/license
 */
import { PromiseCompleter } from '../src/facade/promise';
/**
 * Injectable completer that allows signaling completion of an asynchronous test. Used internally.
 */
export class AsyncTestCompleter {
    constructor() {
        this._completer = new PromiseCompleter();
    }
    done(value) { this._completer.resolve(value); }
    fail(error, stackTrace) { this._completer.reject(error, stackTrace); }
    get promise() { return this._completer.promise; }
}
//# sourceMappingURL=async_test_completer.js.map