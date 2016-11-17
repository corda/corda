/**
 * @license
 * Copyright Google Inc. All Rights Reserved.
 *
 * Use of this source code is governed by an MIT-style license that can be
 * found in the LICENSE file at https://angular.io/license
 */
import { Compiler, Injectable, Injector, NgZone, OpaqueToken } from '../index';
import { PromiseWrapper } from '../src/facade/async';
import { IS_DART, isPresent } from '../src/facade/lang';
import { ComponentFixture } from './component_fixture';
import { tick } from './fake_async';
/**
 * An abstract class for inserting the root test component element in a platform independent way.
 *
 * @experimental
 */
export class TestComponentRenderer {
    insertRootElement(rootElementId) { }
}
/**
 * @experimental
 */
export var ComponentFixtureAutoDetect = new OpaqueToken('ComponentFixtureAutoDetect');
/**
 * @experimental
 */
export var ComponentFixtureNoNgZone = new OpaqueToken('ComponentFixtureNoNgZone');
var _nextRootElementId = 0;
export class TestComponentBuilder {
    constructor(_injector) {
        this._injector = _injector;
    }
    /**
     * Overrides only the html of a {@link ComponentMetadata}.
     * All the other properties of the component's {@link ViewMetadata} are preserved.
     */
    overrideTemplate(componentType, template) {
        throw new Error('overrideTemplate is not supported in this implementation of TestComponentBuilder.');
    }
    /**
     * Overrides a component's {@link ViewMetadata}.
     */
    overrideView(componentType, view) {
        throw new Error('overrideView is not supported in this implementation of TestComponentBuilder.');
    }
    /**
     * Overrides the directives from the component {@link ViewMetadata}.
     */
    overrideDirective(componentType, from, to) {
        throw new Error('overrideDirective is not supported in this implementation of TestComponentBuilder.');
    }
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
    overrideProviders(type, providers) {
        throw new Error('overrideProviders is not supported in this implementation of TestComponentBuilder.');
    }
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
    overrideViewProviders(type, providers) {
        throw new Error('overrideViewProviders is not supported in this implementation of TestComponentBuilder.');
    }
    overrideAnimations(componentType, animations) {
        throw new Error('overrideAnimations is not supported in this implementation of TestComponentBuilder.');
    }
    createFromFactory(ngZone, componentFactory) {
        let rootElId = `root${_nextRootElementId++}`;
        var testComponentRenderer = this._injector.get(TestComponentRenderer);
        testComponentRenderer.insertRootElement(rootElId);
        var componentRef = componentFactory.create(this._injector, [], `#${rootElId}`);
        let autoDetect = this._injector.get(ComponentFixtureAutoDetect, false);
        return new ComponentFixture(componentRef, ngZone, autoDetect);
    }
    /**
     * Builds and returns a ComponentFixture.
     */
    createAsync(rootComponentType) {
        let noNgZone = IS_DART || this._injector.get(ComponentFixtureNoNgZone, false);
        let ngZone = noNgZone ? null : this._injector.get(NgZone, null);
        let compiler = this._injector.get(Compiler);
        let initComponent = () => {
            let promise = compiler.compileComponentAsync(rootComponentType);
            return promise.then(componentFactory => this.createFromFactory(ngZone, componentFactory));
        };
        return ngZone == null ? initComponent() : ngZone.run(initComponent);
    }
    createFakeAsync(rootComponentType) {
        let result;
        let error;
        PromiseWrapper.then(this.createAsync(rootComponentType), (_result) => { result = _result; }, (_error) => { error = _error; });
        tick();
        if (isPresent(error)) {
            throw error;
        }
        return result;
    }
    createSync(rootComponentType) {
        let noNgZone = IS_DART || this._injector.get(ComponentFixtureNoNgZone, false);
        let ngZone = noNgZone ? null : this._injector.get(NgZone, null);
        let compiler = this._injector.get(Compiler);
        let initComponent = () => {
            return this.createFromFactory(ngZone, this._injector.get(Compiler).compileComponentSync(rootComponentType));
        };
        return ngZone == null ? initComponent() : ngZone.run(initComponent);
    }
}
/** @nocollapse */
TestComponentBuilder.decorators = [
    { type: Injectable },
];
/** @nocollapse */
TestComponentBuilder.ctorParameters = [
    { type: Injector, },
];
//# sourceMappingURL=test_component_builder.js.map