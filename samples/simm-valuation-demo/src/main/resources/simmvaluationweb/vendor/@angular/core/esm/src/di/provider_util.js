/**
 * @license
 * Copyright Google Inc. All Rights Reserved.
 *
 * Use of this source code is governed by an MIT-style license that can be
 * found in the LICENSE file at https://angular.io/license
 */
import { Provider } from './provider';
export function isProviderLiteral(obj) {
    return obj && typeof obj == 'object' && obj.provide;
}
export function createProvider(obj) {
    return new Provider(obj.provide, obj);
}
//# sourceMappingURL=provider_util.js.map