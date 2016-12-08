/**
 * @license
 * Copyright Google Inc. All Rights Reserved.
 *
 * Use of this source code is governed by an MIT-style license that can be
 * found in the LICENSE file at https://angular.io/license
 */
"use strict";
var application_ref_1 = require('./application_ref');
var console_1 = require('./console');
var reflection_1 = require('./reflection/reflection');
var reflector_reader_1 = require('./reflection/reflector_reader');
var testability_1 = require('./testability/testability');
function _reflector() {
    return reflection_1.reflector;
}
var __unused; // prevent missing use Dart warning.
/**
 * A default set of providers which should be included in any Angular platform.
 * @experimental
 */
exports.PLATFORM_COMMON_PROVIDERS = [
    application_ref_1.PLATFORM_CORE_PROVIDERS,
    /*@ts2dart_Provider*/ { provide: reflection_1.Reflector, useFactory: _reflector, deps: [] },
    /*@ts2dart_Provider*/ { provide: reflector_reader_1.ReflectorReader, useExisting: reflection_1.Reflector }, testability_1.TestabilityRegistry,
    console_1.Console
];
//# sourceMappingURL=platform_common_providers.js.map