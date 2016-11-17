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
var core_1 = require('@angular/core');
var testing_1 = require('@angular/core/testing');
var index_1 = require('../index');
var collection_1 = require('../src/facade/collection');
var lang_1 = require('../src/facade/lang');
/**
 * @deprecated Import TestComponentRenderer from @angular/core/testing
 */
var testing_2 = require('@angular/core/testing');
exports.TestComponentRenderer = testing_2.TestComponentRenderer;
/**
 * @deprecated Import TestComponentBuilder from @angular/core/testing
 */
var testing_3 = require('@angular/core/testing');
exports.TestComponentBuilder = testing_3.TestComponentBuilder;
/**
 * @deprecated Import ComponentFixture from @angular/core/testing
 */
var testing_4 = require('@angular/core/testing');
exports.ComponentFixture = testing_4.ComponentFixture;
/**
 * @deprecated Import ComponentFixtureNoNgZone from @angular/core/testing
 */
var testing_5 = require('@angular/core/testing');
exports.ComponentFixtureNoNgZone = testing_5.ComponentFixtureNoNgZone;
/**
 * @deprecated Import ComponentFixtureAutoDetect from @angular/core/testing
 */
var testing_6 = require('@angular/core/testing');
exports.ComponentFixtureAutoDetect = testing_6.ComponentFixtureAutoDetect;
var OverridingTestComponentBuilder = (function (_super) {
    __extends(OverridingTestComponentBuilder, _super);
    function OverridingTestComponentBuilder(injector) {
        _super.call(this, injector);
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
    OverridingTestComponentBuilder.prototype._clone = function () {
        var clone = new OverridingTestComponentBuilder(this._injector);
        clone._viewOverrides = collection_1.MapWrapper.clone(this._viewOverrides);
        clone._directiveOverrides = collection_1.MapWrapper.clone(this._directiveOverrides);
        clone._templateOverrides = collection_1.MapWrapper.clone(this._templateOverrides);
        clone._bindingsOverrides = collection_1.MapWrapper.clone(this._bindingsOverrides);
        clone._viewBindingsOverrides = collection_1.MapWrapper.clone(this._viewBindingsOverrides);
        return clone;
    };
    OverridingTestComponentBuilder.prototype.overrideTemplate = function (componentType, template) {
        var clone = this._clone();
        clone._templateOverrides.set(componentType, template);
        return clone;
    };
    OverridingTestComponentBuilder.prototype.overrideAnimations = function (componentType, animations) {
        var clone = this._clone();
        clone._animationOverrides.set(componentType, animations);
        return clone;
    };
    OverridingTestComponentBuilder.prototype.overrideView = function (componentType, view) {
        var clone = this._clone();
        clone._viewOverrides.set(componentType, view);
        return clone;
    };
    OverridingTestComponentBuilder.prototype.overrideDirective = function (componentType, from, to) {
        var clone = this._clone();
        var overridesForComponent = clone._directiveOverrides.get(componentType);
        if (!lang_1.isPresent(overridesForComponent)) {
            clone._directiveOverrides.set(componentType, new Map());
            overridesForComponent = clone._directiveOverrides.get(componentType);
        }
        overridesForComponent.set(from, to);
        return clone;
    };
    OverridingTestComponentBuilder.prototype.overrideProviders = function (type, providers) {
        var clone = this._clone();
        clone._bindingsOverrides.set(type, providers);
        return clone;
    };
    OverridingTestComponentBuilder.prototype.overrideViewProviders = function (type, providers) {
        var clone = this._clone();
        clone._viewBindingsOverrides.set(type, providers);
        return clone;
    };
    OverridingTestComponentBuilder.prototype.createAsync = function (rootComponentType) {
        this._applyMetadataOverrides();
        return _super.prototype.createAsync.call(this, rootComponentType);
    };
    OverridingTestComponentBuilder.prototype.createSync = function (rootComponentType) {
        this._applyMetadataOverrides();
        return _super.prototype.createSync.call(this, rootComponentType);
    };
    OverridingTestComponentBuilder.prototype._applyMetadataOverrides = function () {
        var mockDirectiveResolver = this._injector.get(index_1.DirectiveResolver);
        var mockViewResolver = this._injector.get(index_1.ViewResolver);
        this._viewOverrides.forEach(function (view, type) { mockViewResolver.setView(type, view); });
        this._templateOverrides.forEach(function (template, type) { return mockViewResolver.setInlineTemplate(type, template); });
        this._animationOverrides.forEach(function (animationsEntry, type) { return mockViewResolver.setAnimations(type, animationsEntry); });
        this._directiveOverrides.forEach(function (overrides, component) {
            overrides.forEach(function (to, from) { mockViewResolver.overrideViewDirective(component, from, to); });
        });
        this._bindingsOverrides.forEach(function (bindings, type) { return mockDirectiveResolver.setProvidersOverride(type, bindings); });
        this._viewBindingsOverrides.forEach(function (bindings, type) { return mockDirectiveResolver.setViewProvidersOverride(type, bindings); });
    };
    /** @nocollapse */
    OverridingTestComponentBuilder.decorators = [
        { type: core_1.Injectable },
    ];
    /** @nocollapse */
    OverridingTestComponentBuilder.ctorParameters = [
        { type: core_1.Injector, },
    ];
    return OverridingTestComponentBuilder;
}(testing_1.TestComponentBuilder));
exports.OverridingTestComponentBuilder = OverridingTestComponentBuilder;
//# sourceMappingURL=test_component_builder.js.map