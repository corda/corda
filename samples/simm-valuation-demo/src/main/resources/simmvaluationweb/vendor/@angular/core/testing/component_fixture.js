/**
 * @license
 * Copyright Google Inc. All Rights Reserved.
 *
 * Use of this source code is governed by an MIT-style license that can be
 * found in the LICENSE file at https://angular.io/license
 */
"use strict";
var index_1 = require('../index');
var async_1 = require('../src/facade/async');
var exceptions_1 = require('../src/facade/exceptions');
var lang_1 = require('../src/facade/lang');
/**
 * Fixture for debugging and testing a component.
 *
 * @stable
 */
var ComponentFixture = (function () {
    function ComponentFixture(componentRef, ngZone, autoDetect) {
        var _this = this;
        this._isStable = true;
        this._completer = null;
        this._onUnstableSubscription = null;
        this._onStableSubscription = null;
        this._onMicrotaskEmptySubscription = null;
        this._onErrorSubscription = null;
        this.changeDetectorRef = componentRef.changeDetectorRef;
        this.elementRef = componentRef.location;
        this.debugElement = index_1.getDebugNode(this.elementRef.nativeElement);
        this.componentInstance = componentRef.instance;
        this.nativeElement = this.elementRef.nativeElement;
        this.componentRef = componentRef;
        this.ngZone = ngZone;
        this._autoDetect = autoDetect;
        if (ngZone != null) {
            this._onUnstableSubscription =
                async_1.ObservableWrapper.subscribe(ngZone.onUnstable, function (_) { _this._isStable = false; });
            this._onMicrotaskEmptySubscription =
                async_1.ObservableWrapper.subscribe(ngZone.onMicrotaskEmpty, function (_) {
                    if (_this._autoDetect) {
                        // Do a change detection run with checkNoChanges set to true to check
                        // there are no changes on the second run.
                        _this.detectChanges(true);
                    }
                });
            this._onStableSubscription = async_1.ObservableWrapper.subscribe(ngZone.onStable, function (_) {
                _this._isStable = true;
                // Check whether there are no pending macrotasks in a microtask so that ngZone gets a chance
                // to update the state of pending macrotasks.
                lang_1.scheduleMicroTask(function () {
                    if (!_this.ngZone.hasPendingMacrotasks) {
                        if (_this._completer != null) {
                            _this._completer.resolve(true);
                            _this._completer = null;
                        }
                    }
                });
            });
            this._onErrorSubscription = async_1.ObservableWrapper.subscribe(ngZone.onError, function (error) { throw error.error; });
        }
    }
    ComponentFixture.prototype._tick = function (checkNoChanges) {
        this.changeDetectorRef.detectChanges();
        if (checkNoChanges) {
            this.checkNoChanges();
        }
    };
    /**
     * Trigger a change detection cycle for the component.
     */
    ComponentFixture.prototype.detectChanges = function (checkNoChanges) {
        var _this = this;
        if (checkNoChanges === void 0) { checkNoChanges = true; }
        if (this.ngZone != null) {
            // Run the change detection inside the NgZone so that any async tasks as part of the change
            // detection are captured by the zone and can be waited for in isStable.
            this.ngZone.run(function () { _this._tick(checkNoChanges); });
        }
        else {
            // Running without zone. Just do the change detection.
            this._tick(checkNoChanges);
        }
    };
    /**
     * Do a change detection run to make sure there were no changes.
     */
    ComponentFixture.prototype.checkNoChanges = function () { this.changeDetectorRef.checkNoChanges(); };
    /**
     * Set whether the fixture should autodetect changes.
     *
     * Also runs detectChanges once so that any existing change is detected.
     */
    ComponentFixture.prototype.autoDetectChanges = function (autoDetect) {
        if (autoDetect === void 0) { autoDetect = true; }
        if (this.ngZone == null) {
            throw new exceptions_1.BaseException('Cannot call autoDetectChanges when ComponentFixtureNoNgZone is set');
        }
        this._autoDetect = autoDetect;
        this.detectChanges();
    };
    /**
     * Return whether the fixture is currently stable or has async tasks that have not been completed
     * yet.
     */
    ComponentFixture.prototype.isStable = function () { return this._isStable && !this.ngZone.hasPendingMacrotasks; };
    /**
     * Get a promise that resolves when the fixture is stable.
     *
     * This can be used to resume testing after events have triggered asynchronous activity or
     * asynchronous change detection.
     */
    ComponentFixture.prototype.whenStable = function () {
        if (this.isStable()) {
            return async_1.PromiseWrapper.resolve(false);
        }
        else if (this._completer !== null) {
            return this._completer.promise;
        }
        else {
            this._completer = new async_1.PromiseCompleter();
            return this._completer.promise;
        }
    };
    /**
     * Trigger component destruction.
     */
    ComponentFixture.prototype.destroy = function () {
        this.componentRef.destroy();
        if (this._onUnstableSubscription != null) {
            async_1.ObservableWrapper.dispose(this._onUnstableSubscription);
            this._onUnstableSubscription = null;
        }
        if (this._onStableSubscription != null) {
            async_1.ObservableWrapper.dispose(this._onStableSubscription);
            this._onStableSubscription = null;
        }
        if (this._onMicrotaskEmptySubscription != null) {
            async_1.ObservableWrapper.dispose(this._onMicrotaskEmptySubscription);
            this._onMicrotaskEmptySubscription = null;
        }
        if (this._onErrorSubscription != null) {
            async_1.ObservableWrapper.dispose(this._onErrorSubscription);
            this._onErrorSubscription = null;
        }
    };
    return ComponentFixture;
}());
exports.ComponentFixture = ComponentFixture;
//# sourceMappingURL=component_fixture.js.map