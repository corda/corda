/**
 * @license
 * Copyright Google Inc. All Rights Reserved.
 *
 * Use of this source code is governed by an MIT-style license that can be
 * found in the LICENSE file at https://angular.io/license
 */
"use strict";
var lang_1 = require('../facade/lang');
var collection_1 = require('../facade/collection');
var reflection_1 = require('../reflection/reflection');
var reflective_key_1 = require('./reflective_key');
var metadata_1 = require('./metadata');
var reflective_exceptions_1 = require('./reflective_exceptions');
var forward_ref_1 = require('./forward_ref');
var provider_1 = require('./provider');
var provider_util_1 = require('./provider_util');
/**
 * `Dependency` is used by the framework to extend DI.
 * This is internal to Angular and should not be used directly.
 */
var ReflectiveDependency = (function () {
    function ReflectiveDependency(key, optional, lowerBoundVisibility, upperBoundVisibility, properties) {
        this.key = key;
        this.optional = optional;
        this.lowerBoundVisibility = lowerBoundVisibility;
        this.upperBoundVisibility = upperBoundVisibility;
        this.properties = properties;
    }
    ReflectiveDependency.fromKey = function (key) {
        return new ReflectiveDependency(key, false, null, null, []);
    };
    return ReflectiveDependency;
}());
exports.ReflectiveDependency = ReflectiveDependency;
var _EMPTY_LIST = [];
var ResolvedReflectiveProvider_ = (function () {
    function ResolvedReflectiveProvider_(key, resolvedFactories, multiProvider) {
        this.key = key;
        this.resolvedFactories = resolvedFactories;
        this.multiProvider = multiProvider;
    }
    Object.defineProperty(ResolvedReflectiveProvider_.prototype, "resolvedFactory", {
        get: function () { return this.resolvedFactories[0]; },
        enumerable: true,
        configurable: true
    });
    return ResolvedReflectiveProvider_;
}());
exports.ResolvedReflectiveProvider_ = ResolvedReflectiveProvider_;
/**
 * An internal resolved representation of a factory function created by resolving {@link Provider}.
 * @experimental
 */
var ResolvedReflectiveFactory = (function () {
    function ResolvedReflectiveFactory(
        /**
         * Factory function which can return an instance of an object represented by a key.
         */
        factory, 
        /**
         * Arguments (dependencies) to the `factory` function.
         */
        dependencies) {
        this.factory = factory;
        this.dependencies = dependencies;
    }
    return ResolvedReflectiveFactory;
}());
exports.ResolvedReflectiveFactory = ResolvedReflectiveFactory;
/**
 * Resolve a single provider.
 */
function resolveReflectiveFactory(provider) {
    var factoryFn;
    var resolvedDeps;
    if (lang_1.isPresent(provider.useClass)) {
        var useClass = forward_ref_1.resolveForwardRef(provider.useClass);
        factoryFn = reflection_1.reflector.factory(useClass);
        resolvedDeps = _dependenciesFor(useClass);
    }
    else if (lang_1.isPresent(provider.useExisting)) {
        factoryFn = function (aliasInstance) { return aliasInstance; };
        resolvedDeps = [ReflectiveDependency.fromKey(reflective_key_1.ReflectiveKey.get(provider.useExisting))];
    }
    else if (lang_1.isPresent(provider.useFactory)) {
        factoryFn = provider.useFactory;
        resolvedDeps = constructDependencies(provider.useFactory, provider.dependencies);
    }
    else {
        factoryFn = function () { return provider.useValue; };
        resolvedDeps = _EMPTY_LIST;
    }
    return new ResolvedReflectiveFactory(factoryFn, resolvedDeps);
}
exports.resolveReflectiveFactory = resolveReflectiveFactory;
/**
 * Converts the {@link Provider} into {@link ResolvedProvider}.
 *
 * {@link Injector} internally only uses {@link ResolvedProvider}, {@link Provider} contains
 * convenience provider syntax.
 */
function resolveReflectiveProvider(provider) {
    return new ResolvedReflectiveProvider_(reflective_key_1.ReflectiveKey.get(provider.token), [resolveReflectiveFactory(provider)], provider.multi);
}
exports.resolveReflectiveProvider = resolveReflectiveProvider;
/**
 * Resolve a list of Providers.
 */
function resolveReflectiveProviders(providers) {
    var normalized = _normalizeProviders(providers, []);
    var resolved = normalized.map(resolveReflectiveProvider);
    return collection_1.MapWrapper.values(mergeResolvedReflectiveProviders(resolved, new Map()));
}
exports.resolveReflectiveProviders = resolveReflectiveProviders;
/**
 * Merges a list of ResolvedProviders into a list where
 * each key is contained exactly once and multi providers
 * have been merged.
 */
function mergeResolvedReflectiveProviders(providers, normalizedProvidersMap) {
    for (var i = 0; i < providers.length; i++) {
        var provider = providers[i];
        var existing = normalizedProvidersMap.get(provider.key.id);
        if (lang_1.isPresent(existing)) {
            if (provider.multiProvider !== existing.multiProvider) {
                throw new reflective_exceptions_1.MixingMultiProvidersWithRegularProvidersError(existing, provider);
            }
            if (provider.multiProvider) {
                for (var j = 0; j < provider.resolvedFactories.length; j++) {
                    existing.resolvedFactories.push(provider.resolvedFactories[j]);
                }
            }
            else {
                normalizedProvidersMap.set(provider.key.id, provider);
            }
        }
        else {
            var resolvedProvider;
            if (provider.multiProvider) {
                resolvedProvider = new ResolvedReflectiveProvider_(provider.key, collection_1.ListWrapper.clone(provider.resolvedFactories), provider.multiProvider);
            }
            else {
                resolvedProvider = provider;
            }
            normalizedProvidersMap.set(provider.key.id, resolvedProvider);
        }
    }
    return normalizedProvidersMap;
}
exports.mergeResolvedReflectiveProviders = mergeResolvedReflectiveProviders;
function _normalizeProviders(providers, res) {
    providers.forEach(function (b) {
        if (b instanceof lang_1.Type) {
            res.push(provider_1.provide(b, { useClass: b }));
        }
        else if (b instanceof provider_1.Provider) {
            res.push(b);
        }
        else if (provider_util_1.isProviderLiteral(b)) {
            res.push(provider_util_1.createProvider(b));
        }
        else if (b instanceof Array) {
            _normalizeProviders(b, res);
        }
        else if (b instanceof provider_1.ProviderBuilder) {
            throw new reflective_exceptions_1.InvalidProviderError(b.token);
        }
        else {
            throw new reflective_exceptions_1.InvalidProviderError(b);
        }
    });
    return res;
}
function constructDependencies(typeOrFunc, dependencies) {
    if (lang_1.isBlank(dependencies)) {
        return _dependenciesFor(typeOrFunc);
    }
    else {
        var params = dependencies.map(function (t) { return [t]; });
        return dependencies.map(function (t) { return _extractToken(typeOrFunc, t, params); });
    }
}
exports.constructDependencies = constructDependencies;
function _dependenciesFor(typeOrFunc) {
    var params = reflection_1.reflector.parameters(typeOrFunc);
    if (lang_1.isBlank(params))
        return [];
    if (params.some(lang_1.isBlank)) {
        throw new reflective_exceptions_1.NoAnnotationError(typeOrFunc, params);
    }
    return params.map(function (p) { return _extractToken(typeOrFunc, p, params); });
}
function _extractToken(typeOrFunc /** TODO #9100 */, metadata /** TODO #9100 */ /*any[] | any*/, params) {
    var depProps = [];
    var token = null;
    var optional = false;
    if (!lang_1.isArray(metadata)) {
        if (metadata instanceof metadata_1.InjectMetadata) {
            return _createDependency(metadata.token, optional, null, null, depProps);
        }
        else {
            return _createDependency(metadata, optional, null, null, depProps);
        }
    }
    var lowerBoundVisibility = null;
    var upperBoundVisibility = null;
    for (var i = 0; i < metadata.length; ++i) {
        var paramMetadata = metadata[i];
        if (paramMetadata instanceof lang_1.Type) {
            token = paramMetadata;
        }
        else if (paramMetadata instanceof metadata_1.InjectMetadata) {
            token = paramMetadata.token;
        }
        else if (paramMetadata instanceof metadata_1.OptionalMetadata) {
            optional = true;
        }
        else if (paramMetadata instanceof metadata_1.SelfMetadata) {
            upperBoundVisibility = paramMetadata;
        }
        else if (paramMetadata instanceof metadata_1.HostMetadata) {
            upperBoundVisibility = paramMetadata;
        }
        else if (paramMetadata instanceof metadata_1.SkipSelfMetadata) {
            lowerBoundVisibility = paramMetadata;
        }
        else if (paramMetadata instanceof metadata_1.DependencyMetadata) {
            if (lang_1.isPresent(paramMetadata.token)) {
                token = paramMetadata.token;
            }
            depProps.push(paramMetadata);
        }
    }
    token = forward_ref_1.resolveForwardRef(token);
    if (lang_1.isPresent(token)) {
        return _createDependency(token, optional, lowerBoundVisibility, upperBoundVisibility, depProps);
    }
    else {
        throw new reflective_exceptions_1.NoAnnotationError(typeOrFunc, params);
    }
}
function _createDependency(token /** TODO #9100 */, optional /** TODO #9100 */, lowerBoundVisibility /** TODO #9100 */, upperBoundVisibility /** TODO #9100 */, depProps /** TODO #9100 */) {
    return new ReflectiveDependency(reflective_key_1.ReflectiveKey.get(token), optional, lowerBoundVisibility, upperBoundVisibility, depProps);
}
//# sourceMappingURL=reflective_provider.js.map