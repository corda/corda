/**
 * @license
 * Copyright Google Inc. All Rights Reserved.
 *
 * Use of this source code is governed by an MIT-style license that can be
 * found in the LICENSE file at https://angular.io/license
 */
"use strict";
var index_1 = require('../index');
var application_ref_1 = require('../src/application_ref');
var collection_1 = require('../src/facade/collection');
var exceptions_1 = require('../src/facade/exceptions');
var lang_1 = require('../src/facade/lang');
var async_test_completer_1 = require('./async_test_completer');
/**
 * @experimental
 */
var TestInjector = (function () {
    function TestInjector() {
        this._instantiated = false;
        this._injector = null;
        this._providers = [];
        this.platformProviders = [];
        this.applicationProviders = [];
    }
    TestInjector.prototype.reset = function () {
        this._injector = null;
        this._providers = [];
        this._instantiated = false;
    };
    TestInjector.prototype.addProviders = function (providers) {
        if (this._instantiated) {
            throw new exceptions_1.BaseException('Cannot add providers after test injector is instantiated');
        }
        this._providers = collection_1.ListWrapper.concat(this._providers, providers);
    };
    TestInjector.prototype.createInjector = function () {
        application_ref_1.lockRunMode();
        var rootInjector = index_1.ReflectiveInjector.resolveAndCreate(this.platformProviders);
        this._injector = rootInjector.resolveAndCreateChild(collection_1.ListWrapper.concat(this.applicationProviders, this._providers));
        this._instantiated = true;
        return this._injector;
    };
    TestInjector.prototype.get = function (token) {
        if (!this._instantiated) {
            this.createInjector();
        }
        return this._injector.get(token);
    };
    TestInjector.prototype.execute = function (tokens, fn) {
        var _this = this;
        if (!this._instantiated) {
            this.createInjector();
        }
        var params = tokens.map(function (t) { return _this._injector.get(t); });
        return lang_1.FunctionWrapper.apply(fn, params);
    };
    return TestInjector;
}());
exports.TestInjector = TestInjector;
var _testInjector = null;
/**
 * @experimental
 */
function getTestInjector() {
    if (_testInjector == null) {
        _testInjector = new TestInjector();
    }
    return _testInjector;
}
exports.getTestInjector = getTestInjector;
/**
 * Set the providers that the test injector should use. These should be providers
 * common to every test in the suite.
 *
 * This may only be called once, to set up the common providers for the current test
 * suite on the current platform. If you absolutely need to change the providers,
 * first use `resetBaseTestProviders`.
 *
 * Test Providers for individual platforms are available from
 * 'angular2/platform/testing/<platform_name>'.
 *
 * @experimental
 */
function setBaseTestProviders(platformProviders, applicationProviders) {
    var testInjector = getTestInjector();
    if (testInjector.platformProviders.length > 0 || testInjector.applicationProviders.length > 0) {
        throw new exceptions_1.BaseException('Cannot set base providers because it has already been called');
    }
    testInjector.platformProviders = platformProviders;
    testInjector.applicationProviders = applicationProviders;
    var injector = testInjector.createInjector();
    var inits = injector.get(index_1.PLATFORM_INITIALIZER, null);
    if (lang_1.isPresent(inits)) {
        inits.forEach(function (init) { return init(); });
    }
    testInjector.reset();
}
exports.setBaseTestProviders = setBaseTestProviders;
/**
 * Reset the providers for the test injector.
 *
 * @experimental
 */
function resetBaseTestProviders() {
    var testInjector = getTestInjector();
    testInjector.platformProviders = [];
    testInjector.applicationProviders = [];
    testInjector.reset();
}
exports.resetBaseTestProviders = resetBaseTestProviders;
/**
 * Allows injecting dependencies in `beforeEach()` and `it()`.
 *
 * Example:
 *
 * ```
 * beforeEach(inject([Dependency, AClass], (dep, object) => {
 *   // some code that uses `dep` and `object`
 *   // ...
 * }));
 *
 * it('...', inject([AClass], (object) => {
 *   object.doSomething();
 *   expect(...);
 * })
 * ```
 *
 * Notes:
 * - inject is currently a function because of some Traceur limitation the syntax should
 * eventually
 *   becomes `it('...', @Inject (object: AClass, async: AsyncTestCompleter) => { ... });`
 *
 * @stable
 */
function inject(tokens, fn) {
    var testInjector = getTestInjector();
    if (tokens.indexOf(async_test_completer_1.AsyncTestCompleter) >= 0) {
        // Return an async test method that returns a Promise if AsyncTestCompleter is one of the
        // injected tokens.
        return function () {
            var completer = testInjector.get(async_test_completer_1.AsyncTestCompleter);
            testInjector.execute(tokens, fn);
            return completer.promise;
        };
    }
    else {
        // Return a synchronous test method with the injected tokens.
        return function () { return getTestInjector().execute(tokens, fn); };
    }
}
exports.inject = inject;
/**
 * @experimental
 */
var InjectSetupWrapper = (function () {
    function InjectSetupWrapper(_providers) {
        this._providers = _providers;
    }
    InjectSetupWrapper.prototype._addProviders = function () {
        var additionalProviders = this._providers();
        if (additionalProviders.length > 0) {
            getTestInjector().addProviders(additionalProviders);
        }
    };
    InjectSetupWrapper.prototype.inject = function (tokens, fn) {
        var _this = this;
        return function () {
            _this._addProviders();
            return inject_impl(tokens, fn)();
        };
    };
    return InjectSetupWrapper;
}());
exports.InjectSetupWrapper = InjectSetupWrapper;
/**
 * @experimental
 */
function withProviders(providers) {
    return new InjectSetupWrapper(providers);
}
exports.withProviders = withProviders;
// This is to ensure inject(Async) within InjectSetupWrapper doesn't call itself
// when transpiled to Dart.
var inject_impl = inject;
//# sourceMappingURL=test_injector.js.map