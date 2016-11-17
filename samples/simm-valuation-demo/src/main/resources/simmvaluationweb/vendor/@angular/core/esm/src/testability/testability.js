/**
 * @license
 * Copyright Google Inc. All Rights Reserved.
 *
 * Use of this source code is governed by an MIT-style license that can be
 * found in the LICENSE file at https://angular.io/license
 */
import { Injectable } from '../di/decorators';
import { ObservableWrapper } from '../facade/async';
import { Map, MapWrapper } from '../facade/collection';
import { BaseException } from '../facade/exceptions';
import { scheduleMicroTask } from '../facade/lang';
import { NgZone } from '../zone/ng_zone';
export class Testability {
    constructor(_ngZone) {
        this._ngZone = _ngZone;
        /** @internal */
        this._pendingCount = 0;
        /** @internal */
        this._isZoneStable = true;
        /**
         * Whether any work was done since the last 'whenStable' callback. This is
         * useful to detect if this could have potentially destabilized another
         * component while it is stabilizing.
         * @internal
         */
        this._didWork = false;
        /** @internal */
        this._callbacks = [];
        this._watchAngularEvents();
    }
    /** @internal */
    _watchAngularEvents() {
        ObservableWrapper.subscribe(this._ngZone.onUnstable, (_) => {
            this._didWork = true;
            this._isZoneStable = false;
        });
        this._ngZone.runOutsideAngular(() => {
            ObservableWrapper.subscribe(this._ngZone.onStable, (_) => {
                NgZone.assertNotInAngularZone();
                scheduleMicroTask(() => {
                    this._isZoneStable = true;
                    this._runCallbacksIfReady();
                });
            });
        });
    }
    increasePendingRequestCount() {
        this._pendingCount += 1;
        this._didWork = true;
        return this._pendingCount;
    }
    decreasePendingRequestCount() {
        this._pendingCount -= 1;
        if (this._pendingCount < 0) {
            throw new BaseException('pending async requests below zero');
        }
        this._runCallbacksIfReady();
        return this._pendingCount;
    }
    isStable() {
        return this._isZoneStable && this._pendingCount == 0 && !this._ngZone.hasPendingMacrotasks;
    }
    /** @internal */
    _runCallbacksIfReady() {
        if (this.isStable()) {
            // Schedules the call backs in a new frame so that it is always async.
            scheduleMicroTask(() => {
                while (this._callbacks.length !== 0) {
                    (this._callbacks.pop())(this._didWork);
                }
                this._didWork = false;
            });
        }
        else {
            // Not Ready
            this._didWork = true;
        }
    }
    whenStable(callback) {
        this._callbacks.push(callback);
        this._runCallbacksIfReady();
    }
    getPendingRequestCount() { return this._pendingCount; }
    findBindings(using, provider, exactMatch) {
        // TODO(juliemr): implement.
        return [];
    }
    findProviders(using, provider, exactMatch) {
        // TODO(juliemr): implement.
        return [];
    }
}
/** @nocollapse */
Testability.decorators = [
    { type: Injectable },
];
/** @nocollapse */
Testability.ctorParameters = [
    { type: NgZone, },
];
export class TestabilityRegistry {
    constructor() {
        /** @internal */
        this._applications = new Map();
        _testabilityGetter.addToWindow(this);
    }
    registerApplication(token, testability) {
        this._applications.set(token, testability);
    }
    getTestability(elem) { return this._applications.get(elem); }
    getAllTestabilities() { return MapWrapper.values(this._applications); }
    getAllRootElements() { return MapWrapper.keys(this._applications); }
    findTestabilityInTree(elem, findInAncestors = true) {
        return _testabilityGetter.findTestabilityInTree(this, elem, findInAncestors);
    }
}
/** @nocollapse */
TestabilityRegistry.decorators = [
    { type: Injectable },
];
/** @nocollapse */
TestabilityRegistry.ctorParameters = [];
/* @ts2dart_const */
class _NoopGetTestability {
    addToWindow(registry) { }
    findTestabilityInTree(registry, elem, findInAncestors) {
        return null;
    }
}
/**
 * Set the {@link GetTestability} implementation used by the Angular testing framework.
 * @experimental
 */
export function setTestabilityGetter(getter) {
    _testabilityGetter = getter;
}
var _testabilityGetter = new _NoopGetTestability();
//# sourceMappingURL=testability.js.map