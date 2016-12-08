/**
 * @license
 * Copyright Google Inc. All Rights Reserved.
 *
 * Use of this source code is governed by an MIT-style license that can be
 * found in the LICENSE file at https://angular.io/license
 */
"use strict";
var __extends = (this && this.__extends) || function (d, b) {
    for (var p in b) if (b.hasOwnProperty(p)) d[p] = b[p];
    function __() { this.constructor = d; }
    d.prototype = b === null ? Object.create(b) : (__.prototype = b.prototype, new __());
};
/**
 * A base class for the WrappedException that can be used to identify
 * a WrappedException from ExceptionHandler without adding circular
 * dependency.
 */
var BaseWrappedException = (function (_super) {
    __extends(BaseWrappedException, _super);
    function BaseWrappedException(message) {
        _super.call(this, message);
    }
    Object.defineProperty(BaseWrappedException.prototype, "wrapperMessage", {
        get: function () { return ''; },
        enumerable: true,
        configurable: true
    });
    Object.defineProperty(BaseWrappedException.prototype, "wrapperStack", {
        get: function () { return null; },
        enumerable: true,
        configurable: true
    });
    Object.defineProperty(BaseWrappedException.prototype, "originalException", {
        get: function () { return null; },
        enumerable: true,
        configurable: true
    });
    Object.defineProperty(BaseWrappedException.prototype, "originalStack", {
        get: function () { return null; },
        enumerable: true,
        configurable: true
    });
    Object.defineProperty(BaseWrappedException.prototype, "context", {
        get: function () { return null; },
        enumerable: true,
        configurable: true
    });
    Object.defineProperty(BaseWrappedException.prototype, "message", {
        get: function () { return ''; },
        enumerable: true,
        configurable: true
    });
    return BaseWrappedException;
}(Error));
exports.BaseWrappedException = BaseWrappedException;
//# sourceMappingURL=base_wrapped_exception.js.map