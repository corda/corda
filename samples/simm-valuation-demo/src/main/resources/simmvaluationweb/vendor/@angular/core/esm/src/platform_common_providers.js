/**
 * @license
 * Copyright Google Inc. All Rights Reserved.
 *
 * Use of this source code is governed by an MIT-style license that can be
 * found in the LICENSE file at https://angular.io/license
 */
import { PLATFORM_CORE_PROVIDERS } from './application_ref';
import { Console } from './console';
import { Reflector, reflector } from './reflection/reflection';
import { ReflectorReader } from './reflection/reflector_reader';
import { TestabilityRegistry } from './testability/testability';
function _reflector() {
    return reflector;
}
var __unused; // prevent missing use Dart warning.
/**
 * A default set of providers which should be included in any Angular platform.
 * @experimental
 */
export const PLATFORM_COMMON_PROVIDERS = [
    PLATFORM_CORE_PROVIDERS,
    /*@ts2dart_Provider*/ { provide: Reflector, useFactory: _reflector, deps: [] },
    /*@ts2dart_Provider*/ { provide: ReflectorReader, useExisting: Reflector }, TestabilityRegistry,
    Console
];
//# sourceMappingURL=platform_common_providers.js.map