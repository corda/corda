/**
 * @license
 * Copyright Google Inc. All Rights Reserved.
 *
 * Use of this source code is governed by an MIT-style license that can be
 * found in the LICENSE file at https://angular.io/license
 */
import { Compiler, ComponentMetadata, DirectiveMetadata, Injectable, Injector } from '@angular/core';
import { DirectiveResolver } from '../src/directive_resolver';
import { Map } from '../src/facade/collection';
import { isPresent } from '../src/facade/lang';
export class MockDirectiveResolver extends DirectiveResolver {
    constructor(_injector) {
        super();
        this._injector = _injector;
        this._providerOverrides = new Map();
        this.viewProviderOverrides = new Map();
    }
    get _compiler() { return this._injector.get(Compiler); }
    resolve(type) {
        var dm = super.resolve(type);
        var providerOverrides = this._providerOverrides.get(type);
        var viewProviderOverrides = this.viewProviderOverrides.get(type);
        var providers = dm.providers;
        if (isPresent(providerOverrides)) {
            var originalViewProviders = isPresent(dm.providers) ? dm.providers : [];
            providers = originalViewProviders.concat(providerOverrides);
        }
        if (dm instanceof ComponentMetadata) {
            var viewProviders = dm.viewProviders;
            if (isPresent(viewProviderOverrides)) {
                var originalViewProviders = isPresent(dm.viewProviders) ? dm.viewProviders : [];
                viewProviders = originalViewProviders.concat(viewProviderOverrides);
            }
            return new ComponentMetadata({
                selector: dm.selector,
                inputs: dm.inputs,
                outputs: dm.outputs,
                host: dm.host,
                exportAs: dm.exportAs,
                moduleId: dm.moduleId,
                queries: dm.queries,
                changeDetection: dm.changeDetection,
                providers: providers,
                viewProviders: viewProviders,
                precompile: dm.precompile
            });
        }
        return new DirectiveMetadata({
            selector: dm.selector,
            inputs: dm.inputs,
            outputs: dm.outputs,
            host: dm.host,
            providers: providers,
            exportAs: dm.exportAs,
            queries: dm.queries
        });
    }
    setProvidersOverride(type, providers) {
        this._providerOverrides.set(type, providers);
        this._compiler.clearCacheFor(type);
    }
    setViewProvidersOverride(type, viewProviders) {
        this.viewProviderOverrides.set(type, viewProviders);
        this._compiler.clearCacheFor(type);
    }
}
/** @nocollapse */
MockDirectiveResolver.decorators = [
    { type: Injectable },
];
/** @nocollapse */
MockDirectiveResolver.ctorParameters = [
    { type: Injector, },
];
//# sourceMappingURL=directive_resolver_mock.js.map