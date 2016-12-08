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
var base_wrapped_exception_1 = require('./base_wrapped_exception');
var exception_handler_1 = require('./exception_handler');
var exception_handler_2 = require('./exception_handler');
exports.ExceptionHandler = exception_handler_2.ExceptionHandler;
/**
 * @stable
 */
var BaseException = (function (_super) {
    __extends(BaseException, _super);
    function BaseException(message) {
        if (message === void 0) { message = '--'; }
        _super.call(this, message);
        this.message = message;
        this.stack = (new Error(message)).stack;
    }
    BaseException.prototype.toString = function () { return this.message; };
    return BaseException;
}(Error));
exports.BaseException = BaseException;
/**
 * Wraps an exception and provides additional context or information.
 * @stable
 */
var WrappedException = (function (_super) {
    __extends(WrappedException, _super);
    function WrappedException(_wrapperMessage, _originalException /** TODO #9100 */, _originalStack /** TODO #9100 */, _context /** TODO #9100 */) {
        _super.call(this, _wrapperMessage);
        this._wrapperMessage = _wrapperMessage;
        this._originalException = _originalException;
        this._originalStack = _originalStack;
        this._context = _context;
        this._wrapperStack = (new Error(_wrapperMessage)).stack;
    }
    Object.defineProperty(WrappedException.prototype, "wrapperMessage", {
        get: function () { return this._wrapperMessage; },
        enumerable: true,
        configurable: true
    });
    Object.defineProperty(WrappedException.prototype, "wrapperStack", {
        get: function () { return this._wrapperStack; },
        enumerable: true,
        configurable: true
    });
    Object.defineProperty(WrappedException.prototype, "originalException", {
        get: function () { return this._originalException; },
        enumerable: true,
        configurable: true
    });
    Object.defineProperty(WrappedException.prototype, "originalStack", {
        get: function () { return this._originalStack; },
        enumerable: true,
        configurable: true
    });
    Object.defineProperty(WrappedException.prototype, "context", {
        get: function () { return this._context; },
        enumerable: true,
        configurable: true
    });
    Object.defineProperty(WrappedException.prototype, "message", {
        get: function () { return exception_handler_1.ExceptionHandler.exceptionToString(this); },
        enumerable: true,
        configurable: true
    });
    WrappedException.prototype.toString = function () { return this.message; };
    return WrappedException;
}(base_wrapped_exception_1.BaseWrappedException));
exports.WrappedException = WrappedException;
function makeTypeError(message) {
    return new TypeError(message);
}
exports.makeTypeError = makeTypeError;
function unimplemented() {
    throw new BaseException('unimplemented');
}
exports.unimplemented = unimplemented;
//# sourceMappingURL=exceptions.js.map