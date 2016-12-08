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
var async_1 = require('../src/facade/async');
var collection_1 = require('../src/facade/collection');
var exceptions_1 = require('../src/facade/exceptions');
var lang_1 = require('../src/facade/lang');
var application_tokens_1 = require('./application_tokens');
var console_1 = require('./console');
var di_1 = require('./di');
var component_resolver_1 = require('./linker/component_resolver');
var profile_1 = require('./profile/profile');
var testability_1 = require('./testability/testability');
var ng_zone_1 = require('./zone/ng_zone');
/**
 * Create an Angular zone.
 * @experimental
 */
function createNgZone() {
    return new ng_zone_1.NgZone({ enableLongStackTrace: isDevMode() });
}
exports.createNgZone = createNgZone;
var _devMode = true;
var _runModeLocked = false;
var _platform;
var _inPlatformCreate = false;
/**
 * Disable Angular's development mode, which turns off assertions and other
 * checks within the framework.
 *
 * One important assertion this disables verifies that a change detection pass
 * does not result in additional changes to any bindings (also known as
 * unidirectional data flow).
 *
 * @experimental APIs related to application bootstrap are currently under review.
 */
function enableProdMode() {
    if (_runModeLocked) {
        // Cannot use BaseException as that ends up importing from facade/lang.
        throw new exceptions_1.BaseException('Cannot enable prod mode after platform setup.');
    }
    _devMode = false;
}
exports.enableProdMode = enableProdMode;
/**
 * Returns whether Angular is in development mode.
 * This can only be read after `lockRunMode` has been called.
 *
 * By default, this is true, unless a user calls `enableProdMode`.
 *
 * @experimental APIs related to application bootstrap are currently under review.
 */
function isDevMode() {
    if (!_runModeLocked) {
        throw new exceptions_1.BaseException("Dev mode can't be read before bootstrap!");
    }
    return _devMode;
}
exports.isDevMode = isDevMode;
/**
 * Locks the run mode of Angular. After this has been called,
 * it can't be changed any more. I.e. `isDevMode()` will always
 * return the same value.
 *
 * @experimental APIs related to application bootstrap are currently under review.
 */
function lockRunMode() {
    _runModeLocked = true;
}
exports.lockRunMode = lockRunMode;
/**
 * Creates a platform.
 * Platforms have to be eagerly created via this function.
 *
 * @experimental APIs related to application bootstrap are currently under review.
 */
function createPlatform(injector) {
    if (_inPlatformCreate) {
        throw new exceptions_1.BaseException('Already creating a platform...');
    }
    if (lang_1.isPresent(_platform) && !_platform.disposed) {
        throw new exceptions_1.BaseException('There can be only one platform. Destroy the previous one to create a new one.');
    }
    lockRunMode();
    _inPlatformCreate = true;
    try {
        _platform = injector.get(PlatformRef);
    }
    finally {
        _inPlatformCreate = false;
    }
    return _platform;
}
exports.createPlatform = createPlatform;
/**
 * Checks that there currently is a platform
 * which contains the given token as a provider.
 *
 * @experimental APIs related to application bootstrap are currently under review.
 */
function assertPlatform(requiredToken) {
    var platform = getPlatform();
    if (lang_1.isBlank(platform)) {
        throw new exceptions_1.BaseException('No platform exists!');
    }
    if (lang_1.isPresent(platform) && lang_1.isBlank(platform.injector.get(requiredToken, null))) {
        throw new exceptions_1.BaseException('A platform with a different configuration has been created. Please destroy it first.');
    }
    return platform;
}
exports.assertPlatform = assertPlatform;
/**
 * Dispose the existing platform.
 *
 * @experimental APIs related to application bootstrap are currently under review.
 */
function disposePlatform() {
    if (lang_1.isPresent(_platform) && !_platform.disposed) {
        _platform.dispose();
    }
}
exports.disposePlatform = disposePlatform;
/**
 * Returns the current platform.
 *
 * @experimental APIs related to application bootstrap are currently under review.
 */
function getPlatform() {
    return lang_1.isPresent(_platform) && !_platform.disposed ? _platform : null;
}
exports.getPlatform = getPlatform;
/**
 * Shortcut for ApplicationRef.bootstrap.
 * Requires a platform to be created first.
 *
 * @experimental APIs related to application bootstrap are currently under review.
 */
function coreBootstrap(componentFactory, injector) {
    var appRef = injector.get(ApplicationRef);
    return appRef.bootstrap(componentFactory);
}
exports.coreBootstrap = coreBootstrap;
/**
 * Resolves the componentFactory for the given component,
 * waits for asynchronous initializers and bootstraps the component.
 * Requires a platform to be created first.
 *
 * @experimental APIs related to application bootstrap are currently under review.
 */
function coreLoadAndBootstrap(componentType, injector) {
    var appRef = injector.get(ApplicationRef);
    return appRef.run(function () {
        var componentResolver = injector.get(component_resolver_1.ComponentResolver);
        return async_1.PromiseWrapper
            .all([componentResolver.resolveComponent(componentType), appRef.waitForAsyncInitializers()])
            .then(function (arr) { return appRef.bootstrap(arr[0]); });
    });
}
exports.coreLoadAndBootstrap = coreLoadAndBootstrap;
/**
 * The Angular platform is the entry point for Angular on a web page. Each page
 * has exactly one platform, and services (such as reflection) which are common
 * to every Angular application running on the page are bound in its scope.
 *
 * A page's platform is initialized implicitly when {@link bootstrap}() is called, or
 * explicitly by calling {@link createPlatform}().
 *
 * @experimental APIs related to application bootstrap are currently under review.
 */
var PlatformRef = (function () {
    function PlatformRef() {
    }
    Object.defineProperty(PlatformRef.prototype, "injector", {
        /**
         * Retrieve the platform {@link Injector}, which is the parent injector for
         * every Angular application on the page and provides singleton providers.
         */
        get: function () { throw exceptions_1.unimplemented(); },
        enumerable: true,
        configurable: true
    });
    ;
    Object.defineProperty(PlatformRef.prototype, "disposed", {
        get: function () { throw exceptions_1.unimplemented(); },
        enumerable: true,
        configurable: true
    });
    return PlatformRef;
}());
exports.PlatformRef = PlatformRef;
var PlatformRef_ = (function (_super) {
    __extends(PlatformRef_, _super);
    function PlatformRef_(_injector) {
        _super.call(this);
        this._injector = _injector;
        /** @internal */
        this._applications = [];
        /** @internal */
        this._disposeListeners = [];
        this._disposed = false;
        if (!_inPlatformCreate) {
            throw new exceptions_1.BaseException('Platforms have to be created via `createPlatform`!');
        }
        var inits = _injector.get(application_tokens_1.PLATFORM_INITIALIZER, null);
        if (lang_1.isPresent(inits))
            inits.forEach(function (init) { return init(); });
    }
    PlatformRef_.prototype.registerDisposeListener = function (dispose) { this._disposeListeners.push(dispose); };
    Object.defineProperty(PlatformRef_.prototype, "injector", {
        get: function () { return this._injector; },
        enumerable: true,
        configurable: true
    });
    Object.defineProperty(PlatformRef_.prototype, "disposed", {
        get: function () { return this._disposed; },
        enumerable: true,
        configurable: true
    });
    PlatformRef_.prototype.addApplication = function (appRef) { this._applications.push(appRef); };
    PlatformRef_.prototype.dispose = function () {
        collection_1.ListWrapper.clone(this._applications).forEach(function (app) { return app.dispose(); });
        this._disposeListeners.forEach(function (dispose) { return dispose(); });
        this._disposed = true;
    };
    /** @internal */
    PlatformRef_.prototype._applicationDisposed = function (app) { collection_1.ListWrapper.remove(this._applications, app); };
    /** @nocollapse */
    PlatformRef_.decorators = [
        { type: di_1.Injectable },
    ];
    /** @nocollapse */
    PlatformRef_.ctorParameters = [
        { type: di_1.Injector, },
    ];
    return PlatformRef_;
}(PlatformRef));
exports.PlatformRef_ = PlatformRef_;
/**
 * A reference to an Angular application running on a page.
 *
 * For more about Angular applications, see the documentation for {@link bootstrap}.
 *
 * @experimental APIs related to application bootstrap are currently under review.
 */
var ApplicationRef = (function () {
    function ApplicationRef() {
    }
    Object.defineProperty(ApplicationRef.prototype, "injector", {
        /**
         * Retrieve the application {@link Injector}.
         */
        get: function () { return exceptions_1.unimplemented(); },
        enumerable: true,
        configurable: true
    });
    ;
    Object.defineProperty(ApplicationRef.prototype, "zone", {
        /**
         * Retrieve the application {@link NgZone}.
         */
        get: function () { return exceptions_1.unimplemented(); },
        enumerable: true,
        configurable: true
    });
    ;
    Object.defineProperty(ApplicationRef.prototype, "componentTypes", {
        /**
         * Get a list of component types registered to this application.
         */
        get: function () { return exceptions_1.unimplemented(); },
        enumerable: true,
        configurable: true
    });
    ;
    return ApplicationRef;
}());
exports.ApplicationRef = ApplicationRef;
var ApplicationRef_ = (function (_super) {
    __extends(ApplicationRef_, _super);
    function ApplicationRef_(_platform, _zone, _injector) {
        var _this = this;
        _super.call(this);
        this._platform = _platform;
        this._zone = _zone;
        this._injector = _injector;
        /** @internal */
        this._bootstrapListeners = [];
        /** @internal */
        this._disposeListeners = [];
        /** @internal */
        this._rootComponents = [];
        /** @internal */
        this._rootComponentTypes = [];
        /** @internal */
        this._changeDetectorRefs = [];
        /** @internal */
        this._runningTick = false;
        /** @internal */
        this._enforceNoNewChanges = false;
        var zone = _injector.get(ng_zone_1.NgZone);
        this._enforceNoNewChanges = isDevMode();
        zone.run(function () { _this._exceptionHandler = _injector.get(exceptions_1.ExceptionHandler); });
        this._asyncInitDonePromise = this.run(function () {
            var inits = _injector.get(application_tokens_1.APP_INITIALIZER, null);
            var asyncInitResults = [];
            var asyncInitDonePromise;
            if (lang_1.isPresent(inits)) {
                for (var i = 0; i < inits.length; i++) {
                    var initResult = inits[i]();
                    if (lang_1.isPromise(initResult)) {
                        asyncInitResults.push(initResult);
                    }
                }
            }
            if (asyncInitResults.length > 0) {
                asyncInitDonePromise =
                    async_1.PromiseWrapper.all(asyncInitResults).then(function (_) { return _this._asyncInitDone = true; });
                _this._asyncInitDone = false;
            }
            else {
                _this._asyncInitDone = true;
                asyncInitDonePromise = async_1.PromiseWrapper.resolve(true);
            }
            return asyncInitDonePromise;
        });
        async_1.ObservableWrapper.subscribe(zone.onError, function (error) {
            _this._exceptionHandler.call(error.error, error.stackTrace);
        });
        async_1.ObservableWrapper.subscribe(this._zone.onMicrotaskEmpty, function (_) { _this._zone.run(function () { _this.tick(); }); });
    }
    ApplicationRef_.prototype.registerBootstrapListener = function (listener) {
        this._bootstrapListeners.push(listener);
    };
    ApplicationRef_.prototype.registerDisposeListener = function (dispose) { this._disposeListeners.push(dispose); };
    ApplicationRef_.prototype.registerChangeDetector = function (changeDetector) {
        this._changeDetectorRefs.push(changeDetector);
    };
    ApplicationRef_.prototype.unregisterChangeDetector = function (changeDetector) {
        collection_1.ListWrapper.remove(this._changeDetectorRefs, changeDetector);
    };
    ApplicationRef_.prototype.waitForAsyncInitializers = function () { return this._asyncInitDonePromise; };
    ApplicationRef_.prototype.run = function (callback) {
        var _this = this;
        var zone = this.injector.get(ng_zone_1.NgZone);
        var result;
        // Note: Don't use zone.runGuarded as we want to know about
        // the thrown exception!
        // Note: the completer needs to be created outside
        // of `zone.run` as Dart swallows rejected promises
        // via the onError callback of the promise.
        var completer = async_1.PromiseWrapper.completer();
        zone.run(function () {
            try {
                result = callback();
                if (lang_1.isPromise(result)) {
                    async_1.PromiseWrapper.then(result, function (ref) { completer.resolve(ref); }, function (err, stackTrace) {
                        completer.reject(err, stackTrace);
                        _this._exceptionHandler.call(err, stackTrace);
                    });
                }
            }
            catch (e) {
                _this._exceptionHandler.call(e, e.stack);
                throw e;
            }
        });
        return lang_1.isPromise(result) ? completer.promise : result;
    };
    ApplicationRef_.prototype.bootstrap = function (componentFactory) {
        var _this = this;
        if (!this._asyncInitDone) {
            throw new exceptions_1.BaseException('Cannot bootstrap as there are still asynchronous initializers running. Wait for them using waitForAsyncInitializers().');
        }
        return this.run(function () {
            _this._rootComponentTypes.push(componentFactory.componentType);
            var compRef = componentFactory.create(_this._injector, [], componentFactory.selector);
            compRef.onDestroy(function () { _this._unloadComponent(compRef); });
            var testability = compRef.injector.get(testability_1.Testability, null);
            if (lang_1.isPresent(testability)) {
                compRef.injector.get(testability_1.TestabilityRegistry)
                    .registerApplication(compRef.location.nativeElement, testability);
            }
            _this._loadComponent(compRef);
            var c = _this._injector.get(console_1.Console);
            if (isDevMode()) {
                var prodDescription = lang_1.IS_DART ? 'Production mode is disabled in Dart.' :
                    'Call enableProdMode() to enable the production mode.';
                c.log("Angular 2 is running in the development mode. " + prodDescription);
            }
            return compRef;
        });
    };
    /** @internal */
    ApplicationRef_.prototype._loadComponent = function (componentRef) {
        this._changeDetectorRefs.push(componentRef.changeDetectorRef);
        this.tick();
        this._rootComponents.push(componentRef);
        this._bootstrapListeners.forEach(function (listener) { return listener(componentRef); });
    };
    /** @internal */
    ApplicationRef_.prototype._unloadComponent = function (componentRef) {
        if (!collection_1.ListWrapper.contains(this._rootComponents, componentRef)) {
            return;
        }
        this.unregisterChangeDetector(componentRef.changeDetectorRef);
        collection_1.ListWrapper.remove(this._rootComponents, componentRef);
    };
    Object.defineProperty(ApplicationRef_.prototype, "injector", {
        get: function () { return this._injector; },
        enumerable: true,
        configurable: true
    });
    Object.defineProperty(ApplicationRef_.prototype, "zone", {
        get: function () { return this._zone; },
        enumerable: true,
        configurable: true
    });
    ApplicationRef_.prototype.tick = function () {
        if (this._runningTick) {
            throw new exceptions_1.BaseException('ApplicationRef.tick is called recursively');
        }
        var s = ApplicationRef_._tickScope();
        try {
            this._runningTick = true;
            this._changeDetectorRefs.forEach(function (detector) { return detector.detectChanges(); });
            if (this._enforceNoNewChanges) {
                this._changeDetectorRefs.forEach(function (detector) { return detector.checkNoChanges(); });
            }
        }
        finally {
            this._runningTick = false;
            profile_1.wtfLeave(s);
        }
    };
    ApplicationRef_.prototype.dispose = function () {
        // TODO(alxhub): Dispose of the NgZone.
        collection_1.ListWrapper.clone(this._rootComponents).forEach(function (ref) { return ref.destroy(); });
        this._disposeListeners.forEach(function (dispose) { return dispose(); });
        this._platform._applicationDisposed(this);
    };
    Object.defineProperty(ApplicationRef_.prototype, "componentTypes", {
        get: function () { return this._rootComponentTypes; },
        enumerable: true,
        configurable: true
    });
    /** @internal */
    ApplicationRef_._tickScope = profile_1.wtfCreateScope('ApplicationRef#tick()');
    /** @nocollapse */
    ApplicationRef_.decorators = [
        { type: di_1.Injectable },
    ];
    /** @nocollapse */
    ApplicationRef_.ctorParameters = [
        { type: PlatformRef_, },
        { type: ng_zone_1.NgZone, },
        { type: di_1.Injector, },
    ];
    return ApplicationRef_;
}(ApplicationRef));
exports.ApplicationRef_ = ApplicationRef_;
exports.PLATFORM_CORE_PROVIDERS = 
/*@ts2dart_const*/ [
    PlatformRef_,
    /*@ts2dart_const*/ (
    /* @ts2dart_Provider */ { provide: PlatformRef, useExisting: PlatformRef_ })
];
exports.APPLICATION_CORE_PROVIDERS = [
    /* @ts2dart_Provider */ { provide: ng_zone_1.NgZone, useFactory: createNgZone, deps: [] },
    ApplicationRef_,
    /* @ts2dart_Provider */ { provide: ApplicationRef, useExisting: ApplicationRef_ },
];
//# sourceMappingURL=application_ref.js.map