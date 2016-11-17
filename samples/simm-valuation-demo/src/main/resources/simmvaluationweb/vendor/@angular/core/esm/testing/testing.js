/**
 * @license
 * Copyright Google Inc. All Rights Reserved.
 *
 * Use of this source code is governed by an MIT-style license that can be
 * found in the LICENSE file at https://angular.io/license
 */
import { getTestInjector } from './test_injector';
var _global = (typeof window === 'undefined' ? global : window);
/**
 * @deprecated you no longer need to import jasmine functions from @angular/core/testing. Simply use
 * the globals.
 *
 * See http://jasmine.github.io/ for more details.
 */
export var expect = _global.expect;
/**
 * @deprecated you no longer need to import jasmine functions from @angular/core/testing. Simply use
 * the globals.
 *
 * See http://jasmine.github.io/ for more details.
 */
export var afterEach = _global.afterEach;
/**
 * @deprecated you no longer need to import jasmine functions from @angular/core/testing. Simply use
 * the globals.
 *
 * See http://jasmine.github.io/ for more details.
 */
export var describe = _global.describe;
/**
 * @deprecated you no longer need to import jasmine functions from @angular/core/testing. Simply use
 * the globals.
 *
 * See http://jasmine.github.io/ for more details.
 */
export var fdescribe = _global.fdescribe;
/**
 * @deprecated you no longer need to import jasmine functions from @angular/core/testing. Simply use
 * the globals.
 *
 * See http://jasmine.github.io/ for more details.
 */
export var ddescribe = _global.ddescribe;
/**
 * @deprecated you no longer need to import jasmine functions from @angular/core/testing. Simply use
 * the globals.
 *
 * See http://jasmine.github.io/ for more details.
 */
export var xdescribe = _global.xdescribe;
/**
 * @deprecated you no longer need to import jasmine functions from @angular/core/testing. Simply use
 * the globals.
 *
 * See http://jasmine.github.io/ for more details.
 */
export var beforeEach = _global.beforeEach;
/**
 * @deprecated you no longer need to import jasmine functions from @angular/core/testing. Simply use
 * the globals.
 *
 * See http://jasmine.github.io/ for more details.
 */
export var it = _global.it;
/**
 * @deprecated you no longer need to import jasmine functions from @angular/core/testing. Simply use
 * the globals.
 *
 * See http://jasmine.github.io/ for more details.
 */
export var fit = _global.fit;
/**
 * @deprecated you no longer need to import jasmine functions from @angular/core/testing. Simply use
 * the globals.
 *
 * See http://jasmine.github.io/ for more details.
 */
export var iit = _global.fit;
/**
 * @deprecated you no longer need to import jasmine functions from @angular/core/testing. Simply use
 * the globals.
 *
 * See http://jasmine.github.io/ for more details.
 */
export var xit = _global.xit;
var testInjector = getTestInjector();
// Reset the test providers before each test.
if (_global.beforeEach) {
    beforeEach(() => { testInjector.reset(); });
}
/**
 * Allows overriding default providers of the test injector,
 * which are defined in test_injector.js
 *
 * @stable
 */
export function addProviders(providers) {
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
/**
 * @deprecated Use beforeEach(() => addProviders())
 */
export function beforeEachProviders(fn) {
    beforeEach(() => { addProviders(fn()); });
}
//# sourceMappingURL=testing.js.map