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
var index_1 = require('../index');
var collection_1 = require('../src/facade/collection');
var lang_1 = require('../src/facade/lang');
var MockViewResolver = (function (_super) {
    __extends(MockViewResolver, _super);
    function MockViewResolver(_injector) {
        _super.call(this);
        this._injector = _injector;
        /** @internal */
        this._views = new collection_1.Map();
        /** @internal */
        this._inlineTemplates = new collection_1.Map();
        /** @internal */
        this._animations = new collection_1.Map();
        /** @internal */
        this._directiveOverrides = new collection_1.Map();
    }
    Object.defineProperty(MockViewResolver.prototype, "_compiler", {
        get: function () { return this._injector.get(core_1.Compiler); },
        enumerable: true,
        configurable: true
    });
    MockViewResolver.prototype._clearCacheFor = function (component) { this._compiler.clearCacheFor(component); };
    /**
     * Overrides the {@link ViewMetadata} for a component.
     */
    MockViewResolver.prototype.setView = function (component, view) {
        this._views.set(component, view);
        this._clearCacheFor(component);
    };
    /**
     * Overrides the inline template for a component - other configuration remains unchanged.
     */
    MockViewResolver.prototype.setInlineTemplate = function (component, template) {
        this._inlineTemplates.set(component, template);
        this._clearCacheFor(component);
    };
    MockViewResolver.prototype.setAnimations = function (component, animations) {
        this._animations.set(component, animations);
        this._clearCacheFor(component);
    };
    /**
     * Overrides a directive from the component {@link ViewMetadata}.
     */
    MockViewResolver.prototype.overrideViewDirective = function (component, from, to) {
        var overrides = this._directiveOverrides.get(component);
        if (lang_1.isBlank(overrides)) {
            overrides = new collection_1.Map();
            this._directiveOverrides.set(component, overrides);
        }
        overrides.set(from, to);
        this._clearCacheFor(component);
    };
    /**
     * Returns the {@link ViewMetadata} for a component:
     * - Set the {@link ViewMetadata} to the overridden view when it exists or fallback to the default
     * `ViewResolver`,
     *   see `setView`.
     * - Override the directives, see `overrideViewDirective`.
     * - Override the @View definition, see `setInlineTemplate`.
     */
    MockViewResolver.prototype.resolve = function (component) {
        var view = this._views.get(component);
        if (lang_1.isBlank(view)) {
            view = _super.prototype.resolve.call(this, component);
        }
        var directives = [];
        if (lang_1.isPresent(view.directives)) {
            flattenArray(view.directives, directives);
        }
        var animations = view.animations;
        var templateUrl = view.templateUrl;
        var overrides = this._directiveOverrides.get(component);
        var inlineAnimations = this._animations.get(component);
        if (lang_1.isPresent(inlineAnimations)) {
            animations = inlineAnimations;
        }
        var inlineTemplate = this._inlineTemplates.get(component);
        if (lang_1.isPresent(inlineTemplate)) {
            templateUrl = null;
        }
        else {
            inlineTemplate = view.template;
        }
        if (lang_1.isPresent(overrides) && lang_1.isPresent(view.directives)) {
            overrides.forEach(function (to, from) {
                var srcIndex = directives.indexOf(from);
                if (srcIndex == -1) {
                    throw new core_1.BaseException("Overriden directive " + lang_1.stringify(from) + " not found in the template of " + lang_1.stringify(component));
                }
                directives[srcIndex] = to;
            });
        }
        view = new core_1.ViewMetadata({
            template: inlineTemplate,
            templateUrl: templateUrl,
            directives: directives.length > 0 ? directives : null,
            animations: animations,
            styles: view.styles,
            styleUrls: view.styleUrls,
            pipes: view.pipes,
            encapsulation: view.encapsulation,
            interpolation: view.interpolation
        });
        return view;
    };
    /** @nocollapse */
    MockViewResolver.decorators = [
        { type: core_1.Injectable },
    ];
    /** @nocollapse */
    MockViewResolver.ctorParameters = [
        { type: core_1.Injector, },
    ];
    return MockViewResolver;
}(index_1.ViewResolver));
exports.MockViewResolver = MockViewResolver;
function flattenArray(tree, out) {
    if (!lang_1.isPresent(tree))
        return;
    for (var i = 0; i < tree.length; i++) {
        var item = core_1.resolveForwardRef(tree[i]);
        if (lang_1.isArray(item)) {
            flattenArray(item, out);
        }
        else {
            out.push(item);
        }
    }
}
//# sourceMappingURL=view_resolver_mock.js.map