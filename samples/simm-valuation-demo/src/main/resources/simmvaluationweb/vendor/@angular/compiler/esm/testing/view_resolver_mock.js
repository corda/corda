/**
 * @license
 * Copyright Google Inc. All Rights Reserved.
 *
 * Use of this source code is governed by an MIT-style license that can be
 * found in the LICENSE file at https://angular.io/license
 */
import { BaseException, Compiler, Injectable, Injector, ViewMetadata, resolveForwardRef } from '@angular/core';
import { ViewResolver } from '../index';
import { Map } from '../src/facade/collection';
import { isArray, isBlank, isPresent, stringify } from '../src/facade/lang';
export class MockViewResolver extends ViewResolver {
    constructor(_injector) {
        super();
        this._injector = _injector;
        /** @internal */
        this._views = new Map();
        /** @internal */
        this._inlineTemplates = new Map();
        /** @internal */
        this._animations = new Map();
        /** @internal */
        this._directiveOverrides = new Map();
    }
    get _compiler() { return this._injector.get(Compiler); }
    _clearCacheFor(component) { this._compiler.clearCacheFor(component); }
    /**
     * Overrides the {@link ViewMetadata} for a component.
     */
    setView(component, view) {
        this._views.set(component, view);
        this._clearCacheFor(component);
    }
    /**
     * Overrides the inline template for a component - other configuration remains unchanged.
     */
    setInlineTemplate(component, template) {
        this._inlineTemplates.set(component, template);
        this._clearCacheFor(component);
    }
    setAnimations(component, animations) {
        this._animations.set(component, animations);
        this._clearCacheFor(component);
    }
    /**
     * Overrides a directive from the component {@link ViewMetadata}.
     */
    overrideViewDirective(component, from, to) {
        var overrides = this._directiveOverrides.get(component);
        if (isBlank(overrides)) {
            overrides = new Map();
            this._directiveOverrides.set(component, overrides);
        }
        overrides.set(from, to);
        this._clearCacheFor(component);
    }
    /**
     * Returns the {@link ViewMetadata} for a component:
     * - Set the {@link ViewMetadata} to the overridden view when it exists or fallback to the default
     * `ViewResolver`,
     *   see `setView`.
     * - Override the directives, see `overrideViewDirective`.
     * - Override the @View definition, see `setInlineTemplate`.
     */
    resolve(component) {
        var view = this._views.get(component);
        if (isBlank(view)) {
            view = super.resolve(component);
        }
        var directives = [];
        if (isPresent(view.directives)) {
            flattenArray(view.directives, directives);
        }
        var animations = view.animations;
        var templateUrl = view.templateUrl;
        var overrides = this._directiveOverrides.get(component);
        var inlineAnimations = this._animations.get(component);
        if (isPresent(inlineAnimations)) {
            animations = inlineAnimations;
        }
        var inlineTemplate = this._inlineTemplates.get(component);
        if (isPresent(inlineTemplate)) {
            templateUrl = null;
        }
        else {
            inlineTemplate = view.template;
        }
        if (isPresent(overrides) && isPresent(view.directives)) {
            overrides.forEach((to, from) => {
                var srcIndex = directives.indexOf(from);
                if (srcIndex == -1) {
                    throw new BaseException(`Overriden directive ${stringify(from)} not found in the template of ${stringify(component)}`);
                }
                directives[srcIndex] = to;
            });
        }
        view = new ViewMetadata({
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
    }
}
/** @nocollapse */
MockViewResolver.decorators = [
    { type: Injectable },
];
/** @nocollapse */
MockViewResolver.ctorParameters = [
    { type: Injector, },
];
function flattenArray(tree, out) {
    if (!isPresent(tree))
        return;
    for (var i = 0; i < tree.length; i++) {
        var item = resolveForwardRef(tree[i]);
        if (isArray(item)) {
            flattenArray(item, out);
        }
        else {
            out.push(item);
        }
    }
}
//# sourceMappingURL=view_resolver_mock.js.map