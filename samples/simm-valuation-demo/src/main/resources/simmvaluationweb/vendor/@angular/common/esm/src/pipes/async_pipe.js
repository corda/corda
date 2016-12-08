/**
 * @license
 * Copyright Google Inc. All Rights Reserved.
 *
 * Use of this source code is governed by an MIT-style license that can be
 * found in the LICENSE file at https://angular.io/license
 */
import { ChangeDetectorRef, Pipe, WrappedValue } from '@angular/core';
import { ObservableWrapper } from '../facade/async';
import { isBlank, isPresent, isPromise } from '../facade/lang';
import { InvalidPipeArgumentException } from './invalid_pipe_argument_exception';
class ObservableStrategy {
    createSubscription(async, updateLatestValue) {
        return ObservableWrapper.subscribe(async, updateLatestValue, e => { throw e; });
    }
    dispose(subscription) { ObservableWrapper.dispose(subscription); }
    onDestroy(subscription) { ObservableWrapper.dispose(subscription); }
}
class PromiseStrategy {
    createSubscription(async, updateLatestValue) {
        return async.then(updateLatestValue, e => { throw e; });
    }
    dispose(subscription) { }
    onDestroy(subscription) { }
}
var _promiseStrategy = new PromiseStrategy();
var _observableStrategy = new ObservableStrategy();
var __unused;
export class AsyncPipe {
    constructor(_ref) {
        /** @internal */
        this._latestValue = null;
        /** @internal */
        this._latestReturnedValue = null;
        /** @internal */
        this._subscription = null;
        /** @internal */
        this._obj = null;
        this._strategy = null;
        this._ref = _ref;
    }
    ngOnDestroy() {
        if (isPresent(this._subscription)) {
            this._dispose();
        }
    }
    transform(obj) {
        if (isBlank(this._obj)) {
            if (isPresent(obj)) {
                this._subscribe(obj);
            }
            this._latestReturnedValue = this._latestValue;
            return this._latestValue;
        }
        if (obj !== this._obj) {
            this._dispose();
            return this.transform(obj);
        }
        if (this._latestValue === this._latestReturnedValue) {
            return this._latestReturnedValue;
        }
        else {
            this._latestReturnedValue = this._latestValue;
            return WrappedValue.wrap(this._latestValue);
        }
    }
    /** @internal */
    _subscribe(obj) {
        this._obj = obj;
        this._strategy = this._selectStrategy(obj);
        this._subscription = this._strategy.createSubscription(obj, (value) => this._updateLatestValue(obj, value));
    }
    /** @internal */
    _selectStrategy(obj) {
        if (isPromise(obj)) {
            return _promiseStrategy;
        }
        else if (ObservableWrapper.isObservable(obj)) {
            return _observableStrategy;
        }
        else {
            throw new InvalidPipeArgumentException(AsyncPipe, obj);
        }
    }
    /** @internal */
    _dispose() {
        this._strategy.dispose(this._subscription);
        this._latestValue = null;
        this._latestReturnedValue = null;
        this._subscription = null;
        this._obj = null;
    }
    /** @internal */
    _updateLatestValue(async, value) {
        if (async === this._obj) {
            this._latestValue = value;
            this._ref.markForCheck();
        }
    }
}
/** @nocollapse */
AsyncPipe.decorators = [
    { type: Pipe, args: [{ name: 'async', pure: false },] },
];
/** @nocollapse */
AsyncPipe.ctorParameters = [
    { type: ChangeDetectorRef, },
];
//# sourceMappingURL=async_pipe.js.map