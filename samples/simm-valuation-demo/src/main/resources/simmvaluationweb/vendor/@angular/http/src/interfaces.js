/**
 * @license
 * Copyright Google Inc. All Rights Reserved.
 *
 * Use of this source code is governed by an MIT-style license that can be
 * found in the LICENSE file at https://angular.io/license
 */
"use strict";
/**
 * Abstract class from which real backends are derived.
 *
 * The primary purpose of a `ConnectionBackend` is to create new connections to fulfill a given
 * {@link Request}.
 *
 * @experimental
 */
var ConnectionBackend = (function () {
    function ConnectionBackend() {
    }
    return ConnectionBackend;
}());
exports.ConnectionBackend = ConnectionBackend;
/**
 * Abstract class from which real connections are derived.
 *
 * @experimental
 */
var Connection = (function () {
    function Connection() {
    }
    return Connection;
}());
exports.Connection = Connection;
/**
 * An XSRFStrategy configures XSRF protection (e.g. via headers) on an HTTP request.
 *
 * @experimental
 */
var XSRFStrategy = (function () {
    function XSRFStrategy() {
    }
    return XSRFStrategy;
}());
exports.XSRFStrategy = XSRFStrategy;
//# sourceMappingURL=interfaces.js.map