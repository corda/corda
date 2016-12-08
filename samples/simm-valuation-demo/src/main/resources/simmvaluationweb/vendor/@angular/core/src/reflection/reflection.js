/**
 * @license
 * Copyright Google Inc. All Rights Reserved.
 *
 * Use of this source code is governed by an MIT-style license that can be
 * found in the LICENSE file at https://angular.io/license
 */
"use strict";
var reflection_capabilities_1 = require('./reflection_capabilities');
var reflector_1 = require('./reflector');
var reflector_2 = require('./reflector');
exports.ReflectionInfo = reflector_2.ReflectionInfo;
exports.Reflector = reflector_2.Reflector;
/**
 * The {@link Reflector} used internally in Angular to access metadata
 * about symbols.
 */
exports.reflector = new reflector_1.Reflector(new reflection_capabilities_1.ReflectionCapabilities());
//# sourceMappingURL=reflection.js.map