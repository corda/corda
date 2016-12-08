/**
 * @license
 * Copyright Google Inc. All Rights Reserved.
 *
 * Use of this source code is governed by an MIT-style license that can be
 * found in the LICENSE file at https://angular.io/license
 */
"use strict";
function __export(m) {
    for (var p in m) if (!exports.hasOwnProperty(p)) exports[p] = m[p];
}
/**
 * @module
 * @description
 * The `di` module provides dependency injection container services.
 */
var metadata_1 = require('./di/metadata');
exports.HostMetadata = metadata_1.HostMetadata;
exports.InjectMetadata = metadata_1.InjectMetadata;
exports.InjectableMetadata = metadata_1.InjectableMetadata;
exports.OptionalMetadata = metadata_1.OptionalMetadata;
exports.SelfMetadata = metadata_1.SelfMetadata;
exports.SkipSelfMetadata = metadata_1.SkipSelfMetadata;
// we have to reexport * because Dart and TS export two different sets of types
__export(require('./di/decorators'));
var forward_ref_1 = require('./di/forward_ref');
exports.forwardRef = forward_ref_1.forwardRef;
exports.resolveForwardRef = forward_ref_1.resolveForwardRef;
var injector_1 = require('./di/injector');
exports.Injector = injector_1.Injector;
var reflective_injector_1 = require('./di/reflective_injector');
exports.ReflectiveInjector = reflective_injector_1.ReflectiveInjector;
var provider_1 = require('./di/provider');
exports.Binding = provider_1.Binding;
exports.ProviderBuilder = provider_1.ProviderBuilder;
exports.bind = provider_1.bind;
exports.Provider = provider_1.Provider;
exports.provide = provider_1.provide;
var reflective_provider_1 = require('./di/reflective_provider');
exports.ResolvedReflectiveFactory = reflective_provider_1.ResolvedReflectiveFactory;
var reflective_key_1 = require('./di/reflective_key');
exports.ReflectiveKey = reflective_key_1.ReflectiveKey;
var reflective_exceptions_1 = require('./di/reflective_exceptions');
exports.NoProviderError = reflective_exceptions_1.NoProviderError;
exports.AbstractProviderError = reflective_exceptions_1.AbstractProviderError;
exports.CyclicDependencyError = reflective_exceptions_1.CyclicDependencyError;
exports.InstantiationError = reflective_exceptions_1.InstantiationError;
exports.InvalidProviderError = reflective_exceptions_1.InvalidProviderError;
exports.NoAnnotationError = reflective_exceptions_1.NoAnnotationError;
exports.OutOfBoundsError = reflective_exceptions_1.OutOfBoundsError;
var opaque_token_1 = require('./di/opaque_token');
exports.OpaqueToken = opaque_token_1.OpaqueToken;
//# sourceMappingURL=di.js.map