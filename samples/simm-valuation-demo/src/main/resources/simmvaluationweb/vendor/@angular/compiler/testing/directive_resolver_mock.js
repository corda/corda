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
var directive_resolver_1 = require('../src/directive_resolver');
var collection_1 = require('../src/facade/collection');
var lang_1 = require('../src/facade/lang');
var MockDirectiveResolver = (function (_super) {
    __extends(MockDirectiveResolver, _super);
    function MockDirectiveResolver(_injector) {
        _super.call(this);
        this._injector = _injector;
        this._providerOverrides = new collection_1.Map();
        this.viewProviderOverrides = new collection_1.Map();
    }
    Object.defineProperty(MockDirectiveResolver.prototype, "_compiler", {
        get: function () { return this._injector.get(core_1.Compiler); },
        enumerable: true,
        configurable: true
    });
    MockDirectiveResolver.prototype.resolve = function (type) {
        var dm = _super.prototype.resolve.call(this, type);
        var providerOverrides = this._providerOverrides.get(type);
        var viewProviderOverrides = this.viewProviderOverrides.get(type);
        var providers = dm.providers;
        if (lang_1.isPresent(providerOverrides)) {
            var originalViewProviders = lang_1.isPresent(dm.providers) ? dm.providers : [];
            providers = originalViewProviders.concat(providerOverrides);
        }
        if (dm instanceof core_1.ComponentMetadata) {
            var viewProviders = dm.viewProviders;
            if (lang_1.isPresent(viewProviderOverrides)) {
                var originalViewProviders = lang_1.isPresent(dm.viewProviders) ? dm.viewProviders : [];
                viewProviders = originalViewProviders.concat(viewProviderOverrides);
            }
            return new core_1.ComponentMetadata({
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
        return new core_1.DirectiveMetadata({
            selector: dm.selector,
            inputs: dm.inputs,
            outputs: dm.outputs,
            host: dm.host,
            providers: providers,
            exportAs: dm.exportAs,
            queries: dm.queries
        });
    };
    MockDirectiveResolver.prototype.setProvidersOverride = function (type, providers) {
        this._providerOverrides.set(type, providers);
        this._compiler.clearCacheFor(type);
    };
    MockDirectiveResolver.prototype.setViewProvidersOverride = function (type, viewProviders) {
        this.viewProviderOverrides.set(type, viewProviders);
        this._compiler.clearCacheFor(type);
    };
    /** @nocollapse */
    MockDirectiveResolver.decorators = [
        { type: core_1.Injectable },
    ];
    /** @nocollapse */
    MockDirectiveResolver.ctorParameters = [
        { type: core_1.Injector, },
    ];
    return MockDirectiveResolver;
}(directive_resolver_1.DirectiveResolver));
exports.MockDirectiveResolver = MockDirectiveResolver;
//# sourceMappingURL=directive_resolver_mock.js.map