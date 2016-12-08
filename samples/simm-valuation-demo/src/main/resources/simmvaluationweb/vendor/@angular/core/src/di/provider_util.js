/**
 * @license
 * Copyright Google Inc. All Rights Reserved.
 *
 * Use of this source code is governed by an MIT-style license that can be
 * found in the LICENSE file at https://angular.io/license
 */
"use strict";
var provider_1 = require('./provider');
function isProviderLiteral(obj) {
    return obj && typeof obj == 'object' && obj.provide;
}
exports.isProviderLiteral = isProviderLiteral;
function createProvider(obj) {
    return new provider_1.Provider(obj.provide, obj);
}
exports.createProvider = createProvider;
//# sourceMappingURL=provider_util.js.map