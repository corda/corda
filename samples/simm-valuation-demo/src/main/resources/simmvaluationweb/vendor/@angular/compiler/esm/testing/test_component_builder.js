/**
 * @license
 * Copyright Google Inc. All Rights Reserved.
 *
 * Use of this source code is governed by an MIT-style license that can be
 * found in the LICENSE file at https://angular.io/license
 */
import { Injectable, Injector } from '@angular/core';
import { TestComponentBuilder } from '@angular/core/testing';
import { DirectiveResolver, ViewResolver } from '../index';
import { MapWrapper } from '../src/facade/collection';
import { isPresent } from '../src/facade/lang';
/**
 * @deprecated Import TestComponentRenderer from @angular/core/testing
 */
export { TestComponentRenderer } from '@angular/core/testing';
/**
 * @deprecated Import TestComponentBuilder from @angular/core/testing
 */
export { TestComponentBuilder } from '@angular/core/testing';
/**
 * @deprecated Import ComponentFixture from @angular/core/testing
 */
export { ComponentFixture } from '@angular/core/testing';
/**
 * @deprecated Import ComponentFixtureNoNgZone from @angular/core/testing
 */
export { ComponentFixtureNoNgZone } from '@angular/core/testing';
/**
 * @deprecated Import ComponentFixtureAutoDetect from @angular/core/testing
 */
export { ComponentFixtureAutoDetect } from '@angular/core/testing';
export class OverridingTestComponentBuilder extends TestComponentBuilder {
    constructor(injector) {
        super(injector);
        /** @internal */
        this._bindingsOverrides = new Map();
        /** @internal */
        this._directiveOverrides = new Map();
        /** @internal */
        this._templateOverrides = new Map();
        /** @internal */
        this._animationOverrides = new Map();
        /** @internal */
        this._viewBindingsOverrides = new Map();
        /** @internal */
        this._viewOverrides = new Map();
    }
    /** @internal */
    _clone() {
        let clone = new OverridingTestComponentBuilder(this._injector);
        clone._viewOverrides = MapWrapper.clone(this._viewOverrides);
        clone._directiveOverrides = MapWrapper.clone(this._directiveOverrides);
        clone._templateOverrides = MapWrapper.clone(this._templateOverrides);
        clone._bindingsOverrides = MapWrapper.clone(this._bindingsOverrides);
        clone._viewBindingsOverrides = MapWrapper.clone(this._viewBindingsOverrides);
        return clone;
    }
    overrideTemplate(componentType, template) {
        let clone = this._clone();
        clone._templateOverrides.set(componentType, template);
        return clone;
    }
    overrideAnimations(componentType, animations) {
        var clone = this._clone();
        clone._animationOverrides.set(componentType, animations);
        return clone;
    }
    overrideView(componentType, view) {
        let clone = this._clone();
        clone._viewOverrides.set(componentType, view);
        return clone;
    }
    overrideDirective(componentType, from, to) {
        let clone = this._clone();
        let overridesForComponent = clone._directiveOverrides.get(componentType);
        if (!isPresent(overridesForComponent)) {
            clone._directiveOverrides.set(componentType, new Map());
            overridesForComponent = clone._directiveOverrides.get(componentType);
        }
        overridesForComponent.set(from, to);
        return clone;
    }
    overrideProviders(type, providers) {
        let clone = this._clone();
        clone._bindingsOverrides.set(type, providers);
        return clone;
    }
    overrideViewProviders(type, providers) {
        let clone = this._clone();
        clone._viewBindingsOverrides.set(type, providers);
        return clone;
    }
    createAsync(rootComponentType) {
        this._applyMetadataOverrides();
        return super.createAsync(rootComponentType);
    }
    createSync(rootComponentType) {
        this._applyMetadataOverrides();
        return super.createSync(rootComponentType);
    }
    _applyMetadataOverrides() {
        let mockDirectiveResolver = this._injector.get(DirectiveResolver);
        let mockViewResolver = this._injector.get(ViewResolver);
        this._viewOverrides.forEach((view, type) => { mockViewResolver.setView(type, view); });
        this._templateOverrides.forEach((template, type) => mockViewResolver.setInlineTemplate(type, template));
        this._animationOverrides.forEach((animationsEntry, type) => mockViewResolver.setAnimations(type, animationsEntry));
        this._directiveOverrides.forEach((overrides, component) => {
            overrides.forEach((to, from) => { mockViewResolver.overrideViewDirective(component, from, to); });
        });
        this._bindingsOverrides.forEach((bindings, type) => mockDirectiveResolver.setProvidersOverride(type, bindings));
        this._viewBindingsOverrides.forEach((bindings, type) => mockDirectiveResolver.setViewProvidersOverride(type, bindings));
    }
}
/** @nocollapse */
OverridingTestComponentBuilder.decorators = [
    { type: Injectable },
];
/** @nocollapse */
OverridingTestComponentBuilder.ctorParameters = [
    { type: Injector, },
];
//# sourceMappingURL=test_component_builder.js.map