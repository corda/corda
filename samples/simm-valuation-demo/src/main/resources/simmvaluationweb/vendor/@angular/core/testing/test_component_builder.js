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
var lang_1 = require('../src/facade/lang');
var component_fixture_1 = require('./component_fixture');
var fake_async_1 = require('./fake_async');
/**
 * An abstract class for inserting the root test component element in a platform independent way.
 *
 * @experimental
 */
var TestComponentRenderer = (function () {
    function TestComponentRenderer() {
    }
    TestComponentRenderer.prototype.insertRootElement = function (rootElementId) { };
    return TestComponentRenderer;
}());
exports.TestComponentRenderer = TestComponentRenderer;
/**
 * @experimental
 */
exports.ComponentFixtureAutoDetect = new index_1.OpaqueToken('ComponentFixtureAutoDetect');
/**
 * @experimental
 */
exports.ComponentFixtureNoNgZone = new index_1.OpaqueToken('ComponentFixtureNoNgZone');
var _nextRootElementId = 0;
var TestComponentBuilder = (function () {
    function TestComponentBuilder(_injector) {
        this._injector = _injector;
    }
    /**
     * Overrides only the html of a {@link ComponentMetadata}.
     * All the other properties of the component's {@link ViewMetadata} are preserved.
     */
    TestComponentBuilder.prototype.overrideTemplate = function (componentType, template) {
        throw new Error('overrideTemplate is not supported in this implementation of TestComponentBuilder.');
    };
    /**
     * Overrides a component's {@link ViewMetadata}.
     */
    TestComponentBuilder.prototype.overrideView = function (componentType, view) {
        throw new Error('overrideView is not supported in this implementation of TestComponentBuilder.');
    };
    /**
     * Overrides the directives from the component {@link ViewMetadata}.
     */
    TestComponentBuilder.prototype.overrideDirective = function (componentType, from, to) {
        throw new Error('overrideDirective is not supported in this implementation of TestComponentBuilder.');
    };
    /**
     * Overrides one or more injectables configured via `providers` metadata property of a directive
     * or
     * component.
     * Very useful when certain providers need to be mocked out.
     *
     * The providers specified via this method are appended to the existing `providers` causing the
     * duplicated providers to
     * be overridden.
     */
    TestComponentBuilder.prototype.overrideProviders = function (type, providers) {
        throw new Error('overrideProviders is not supported in this implementation of TestComponentBuilder.');
    };
    /**
     * Overrides one or more injectables configured via `providers` metadata property of a directive
     * or
     * component.
     * Very useful when certain providers need to be mocked out.
     *
     * The providers specified via this method are appended to the existing `providers` causing the
     * duplicated providers to
     * be overridden.
     */
    TestComponentBuilder.prototype.overrideViewProviders = function (type, providers) {
        throw new Error('overrideViewProviders is not supported in this implementation of TestComponentBuilder.');
    };
    TestComponentBuilder.prototype.overrideAnimations = function (componentType, animations) {
        throw new Error('overrideAnimations is not supported in this implementation of TestComponentBuilder.');
    };
    TestComponentBuilder.prototype.createFromFactory = function (ngZone, componentFactory) {
        var rootElId = "root" + _nextRootElementId++;
        var testComponentRenderer = this._injector.get(TestComponentRenderer);
        testComponentRenderer.insertRootElement(rootElId);
        var componentRef = componentFactory.create(this._injector, [], "#" + rootElId);
        var autoDetect = this._injector.get(exports.ComponentFixtureAutoDetect, false);
        return new component_fixture_1.ComponentFixture(componentRef, ngZone, autoDetect);
    };
    /**
     * Builds and returns a ComponentFixture.
     */
    TestComponentBuilder.prototype.createAsync = function (rootComponentType) {
        var _this = this;
        var noNgZone = lang_1.IS_DART || this._injector.get(exports.ComponentFixtureNoNgZone, false);
        var ngZone = noNgZone ? null : this._injector.get(index_1.NgZone, null);
        var compiler = this._injector.get(index_1.Compiler);
        var initComponent = function () {
            var promise = compiler.compileComponentAsync(rootComponentType);
            return promise.then(function (componentFactory) { return _this.createFromFactory(ngZone, componentFactory); });
        };
        return ngZone == null ? initComponent() : ngZone.run(initComponent);
    };
    TestComponentBuilder.prototype.createFakeAsync = function (rootComponentType) {
        var result;
        var error;
        async_1.PromiseWrapper.then(this.createAsync(rootComponentType), function (_result) { result = _result; }, function (_error) { error = _error; });
        fake_async_1.tick();
        if (lang_1.isPresent(error)) {
            throw error;
        }
        return result;
    };
    TestComponentBuilder.prototype.createSync = function (rootComponentType) {
        var _this = this;
        var noNgZone = lang_1.IS_DART || this._injector.get(exports.ComponentFixtureNoNgZone, false);
        var ngZone = noNgZone ? null : this._injector.get(index_1.NgZone, null);
        var compiler = this._injector.get(index_1.Compiler);
        var initComponent = function () {
            return _this.createFromFactory(ngZone, _this._injector.get(index_1.Compiler).compileComponentSync(rootComponentType));
        };
        return ngZone == null ? initComponent() : ngZone.run(initComponent);
    };
    /** @nocollapse */
    TestComponentBuilder.decorators = [
        { type: index_1.Injectable },
    ];
    /** @nocollapse */
    TestComponentBuilder.ctorParameters = [
        { type: index_1.Injector, },
    ];
    return TestComponentBuilder;
}());
exports.TestComponentBuilder = TestComponentBuilder;
//# sourceMappingURL=test_component_builder.js.map