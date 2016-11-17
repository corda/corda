/**
 * @license
 * Copyright Google Inc. All Rights Reserved.
 *
 * Use of this source code is governed by an MIT-style license that can be
 * found in the LICENSE file at https://angular.io/license
 */
"use strict";
var exceptions_1 = require('../facade/exceptions');
var lang_1 = require('../facade/lang');
/**
 * Low-level service for running the angular compiler duirng runtime
 * to create {@link ComponentFactory}s, which
 * can later be used to create and render a Component instance.
 * @stable
 */
var Compiler = (function () {
    function Compiler() {
    }
    /**
     * Loads the template and styles of a component and returns the associated `ComponentFactory`.
     */
    Compiler.prototype.compileComponentAsync = function (component) {
        throw new exceptions_1.BaseException("Runtime compiler is not loaded. Tried to compile " + lang_1.stringify(component));
    };
    /**
     * Compiles the given component. All templates have to be either inline or compiled via
     * `compileComponentAsync` before.
     */
    Compiler.prototype.compileComponentSync = function (component) {
        throw new exceptions_1.BaseException("Runtime compiler is not loaded. Tried to compile " + lang_1.stringify(component));
    };
    /**
     * Clears all caches
     */
    Compiler.prototype.clearCache = function () { };
    /**
     * Clears the cache for the given component.
     */
    Compiler.prototype.clearCacheFor = function (compType) { };
    return Compiler;
}());
exports.Compiler = Compiler;
//# sourceMappingURL=compiler.js.map