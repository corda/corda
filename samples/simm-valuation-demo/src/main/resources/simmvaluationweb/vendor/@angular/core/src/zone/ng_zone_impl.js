/**
 * @license
 * Copyright Google Inc. All Rights Reserved.
 *
 * Use of this source code is governed by an MIT-style license that can be
 * found in the LICENSE file at https://angular.io/license
 */
"use strict";
/**
 * Stores error information; delivered via [NgZone.onError] stream.
 * @deprecated
 */
var NgZoneError = (function () {
    function NgZoneError(error, stackTrace) {
        this.error = error;
        this.stackTrace = stackTrace;
    }
    return NgZoneError;
}());
exports.NgZoneError = NgZoneError;
var NgZoneImpl = (function () {
    function NgZoneImpl(_a) {
        var _this = this;
        var trace = _a.trace, onEnter = _a.onEnter, onLeave = _a.onLeave, setMicrotask = _a.setMicrotask, setMacrotask = _a.setMacrotask, onError = _a.onError;
        this.onEnter = onEnter;
        this.onLeave = onLeave;
        this.setMicrotask = setMicrotask;
        this.setMacrotask = setMacrotask;
        this.onError = onError;
        if (Zone) {
            this.outer = this.inner = Zone.current;
            if (Zone['wtfZoneSpec']) {
                this.inner = this.inner.fork(Zone['wtfZoneSpec']);
            }
            if (trace && Zone['longStackTraceZoneSpec']) {
                this.inner = this.inner.fork(Zone['longStackTraceZoneSpec']);
            }
            this.inner = this.inner.fork({
                name: 'angular',
                properties: { 'isAngularZone': true },
                onInvokeTask: function (delegate, current, target, task, applyThis, applyArgs) {
                    try {
                        _this.onEnter();
                        return delegate.invokeTask(target, task, applyThis, applyArgs);
                    }
                    finally {
                        _this.onLeave();
                    }
                },
                onInvoke: function (delegate, current, target, callback, applyThis, applyArgs, source) {
                    try {
                        _this.onEnter();
                        return delegate.invoke(target, callback, applyThis, applyArgs, source);
                    }
                    finally {
                        _this.onLeave();
                    }
                },
                onHasTask: function (delegate, current, target, hasTaskState) {
                    delegate.hasTask(target, hasTaskState);
                    if (current == target) {
                        // We are only interested in hasTask events which originate from our zone
                        // (A child hasTask event is not interesting to us)
                        if (hasTaskState.change == 'microTask') {
                            _this.setMicrotask(hasTaskState.microTask);
                        }
                        else if (hasTaskState.change == 'macroTask') {
                            _this.setMacrotask(hasTaskState.macroTask);
                        }
                    }
                },
                onHandleError: function (delegate, current, target, error) {
                    delegate.handleError(target, error);
                    _this.onError(new NgZoneError(error, error.stack));
                    return false;
                }
            });
        }
        else {
            throw new Error('Angular requires Zone.js polyfill.');
        }
    }
    NgZoneImpl.isInAngularZone = function () { return Zone.current.get('isAngularZone') === true; };
    NgZoneImpl.prototype.runInner = function (fn) { return this.inner.run(fn); };
    ;
    NgZoneImpl.prototype.runInnerGuarded = function (fn) { return this.inner.runGuarded(fn); };
    ;
    NgZoneImpl.prototype.runOuter = function (fn) { return this.outer.run(fn); };
    ;
    return NgZoneImpl;
}());
exports.NgZoneImpl = NgZoneImpl;
//# sourceMappingURL=ng_zone_impl.js.map