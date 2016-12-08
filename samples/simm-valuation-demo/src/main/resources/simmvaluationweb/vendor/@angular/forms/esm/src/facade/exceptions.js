/**
 * @license
 * Copyright Google Inc. All Rights Reserved.
 *
 * Use of this source code is governed by an MIT-style license that can be
 * found in the LICENSE file at https://angular.io/license
 */
import { BaseWrappedException } from './base_wrapped_exception';
import { ExceptionHandler } from './exception_handler';
export { ExceptionHandler } from './exception_handler';
/**
 * @stable
 */
export class BaseException extends Error {
    constructor(message = '--') {
        super(message);
        this.message = message;
        this.stack = (new Error(message)).stack;
    }
    toString() { return this.message; }
}
/**
 * Wraps an exception and provides additional context or information.
 * @stable
 */
export class WrappedException extends BaseWrappedException {
    constructor(_wrapperMessage, _originalException /** TODO #9100 */, _originalStack /** TODO #9100 */, _context /** TODO #9100 */) {
        super(_wrapperMessage);
        this._wrapperMessage = _wrapperMessage;
        this._originalException = _originalException;
        this._originalStack = _originalStack;
        this._context = _context;
        this._wrapperStack = (new Error(_wrapperMessage)).stack;
    }
    get wrapperMessage() { return this._wrapperMessage; }
    get wrapperStack() { return this._wrapperStack; }
    get originalException() { return this._originalException; }
    get originalStack() { return this._originalStack; }
    get context() { return this._context; }
    get message() { return ExceptionHandler.exceptionToString(this); }
    toString() { return this.message; }
}
export function makeTypeError(message) {
    return new TypeError(message);
}
export function unimplemented() {
    throw new BaseException('unimplemented');
}
//# sourceMappingURL=exceptions.js.map