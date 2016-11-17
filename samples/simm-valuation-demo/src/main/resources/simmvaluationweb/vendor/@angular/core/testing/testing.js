/**
 * @license
 * Copyright Google Inc. All Rights Reserved.
 *
 * Use of this source code is governed by an MIT-style license that can be
 * found in the LICENSE file at https://angular.io/license
 */
"use strict";
/**
 * Public Test Library for unit testing Angular2 Applications. Assumes that you are running
 * with Jasmine, Mocha, or a similar framework which exports a beforeEach function and
 * allows tests to be asynchronous by either returning a promise or using a 'done' parameter.
 */
var test_injector_1 = require('./test_injector');
var _global = (typeof window === 'undefined' ? global : window);
/**
 * @deprecated you no longer need to import jasmine functions from @angular/core/testing. Simply use
 * the globals.
 *
 * See http://jasmine.github.io/ for more details.
 */
exports.expect = _global.expect;
/**
 * @deprecated you no longer need to import jasmine functions from @angular/core/testing. Simply use
 * the globals.
 *
 * See http://jasmine.github.io/ for more details.
 */
exports.afterEach = _global.afterEach;
/**
 * @deprecated you no longer need to import jasmine functions from @angular/core/testing. Simply use
 * the globals.
 *
 * See http://jasmine.github.io/ for more details.
 */
exports.describe = _global.describe;
/**
 * @deprecated you no longer need to import jasmine functions from @angular/core/testing. Simply use
 * the globals.
 *
 * See http://jasmine.github.io/ for more details.
 */
exports.fdescribe = _global.fdescribe;
/**
 * @deprecated you no longer need to import jasmine functions from @angular/core/testing. Simply use
 * the globals.
 *
 * See http://jasmine.github.io/ for more details.
 */
exports.ddescribe = _global.ddescribe;
/**
 * @deprecated you no longer need to import jasmine functions from @angular/core/testing. Simply use
 * the globals.
 *
 * See http://jasmine.github.io/ for more details.
 */
exports.xdescribe = _global.xdescribe;
/**
 * @deprecated you no longer need to import jasmine functions from @angular/core/testing. Simply use
 * the globals.
 *
 * See http://jasmine.github.io/ for more details.
 */
exports.beforeEach = _global.beforeEach;
/**
 * @deprecated you no longer need to import jasmine functions from @angular/core/testing. Simply use
 * the globals.
 *
 * See http://jasmine.github.io/ for more details.
 */
exports.it = _global.it;
/**
 * @deprecated you no longer need to import jasmine functions from @angular/core/testing. Simply use
 * the globals.
 *
 * See http://jasmine.github.io/ for more details.
 */
exports.fit = _global.fit;
/**
 * @deprecated you no longer need to import jasmine functions from @angular/core/testing. Simply use
 * the globals.
 *
 * See http://jasmine.github.io/ for more details.
 */
exports.iit = _global.fit;
/**
 * @deprecated you no longer need to import jasmine functions from @angular/core/testing. Simply use
 * the globals.
 *
 * See http://jasmine.github.io/ for more details.
 */
exports.xit = _global.xit;
var testInjector = test_injector_1.getTestInjector();
// Reset the test providers before each test.
if (_global.beforeEach) {
    exports.beforeEach(function () { testInjector.reset(); });
}
/**
 * Allows overriding default providers of the test injector,
 * which are defined in test_injector.js
 *
 * @stable
 */
function addProviders(providers) {
    if (!providers)
        return;
    try {
        testInjector.addProviders(providers);
    }
    catch (e) {
        throw new Error('addProviders can\'t be called after the injector has been already created for this test. ' +
            'This is most likely because you\'ve already used the injector to inject a beforeEach or the ' +
            'current `it` function.');
    }
}
exports.addProviders = addProviders;
/**
 * @deprecated Use beforeEach(() => addProviders())
 */
function beforeEachProviders(fn) {
    exports.beforeEach(function () { addProviders(fn()); });
}
exports.beforeEachProviders = beforeEachProviders;
//# sourceMappingURL=testing.js.map