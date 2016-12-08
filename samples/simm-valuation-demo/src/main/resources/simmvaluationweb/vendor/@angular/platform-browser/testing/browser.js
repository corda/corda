/**
 * @license
 * Copyright Google Inc. All Rights Reserved.
 *
 * Use of this source code is governed by an MIT-style license that can be
 * found in the LICENSE file at https://angular.io/license
 */
"use strict";
var core_1 = require('@angular/core');
var core_private_1 = require('../core_private');
var browser_1 = require('../src/browser');
var browser_adapter_1 = require('../src/browser/browser_adapter');
var ng_probe_1 = require('../src/dom/debug/ng_probe');
var browser_util_1 = require('./browser_util');
/**
 * Default platform providers for testing without a compiler.
 */
var TEST_BROWSER_STATIC_PLATFORM_PROVIDERS = [
    core_1.PLATFORM_COMMON_PROVIDERS,
    { provide: core_1.PLATFORM_INITIALIZER, useValue: initBrowserTests, multi: true }
];
var ADDITIONAL_TEST_BROWSER_STATIC_PROVIDERS = [
    { provide: core_1.APP_ID, useValue: 'a' }, ng_probe_1.ELEMENT_PROBE_PROVIDERS,
    { provide: core_1.NgZone, useFactory: createNgZone },
    { provide: core_private_1.AnimationDriver, useClass: core_private_1.NoOpAnimationDriver }
];
function initBrowserTests() {
    browser_adapter_1.BrowserDomAdapter.makeCurrent();
    browser_util_1.BrowserDetection.setup();
}
function createNgZone() {
    return new core_1.NgZone({ enableLongStackTrace: true });
}
/**
 * Default platform providers for testing.
 *
 * @stable
 */
exports.TEST_BROWSER_PLATFORM_PROVIDERS = TEST_BROWSER_STATIC_PLATFORM_PROVIDERS;
/**
 * Default application providers for testing without a compiler.
 *
 * @stable
 */
exports.TEST_BROWSER_APPLICATION_PROVIDERS = [browser_1.BROWSER_APP_PROVIDERS, ADDITIONAL_TEST_BROWSER_STATIC_PROVIDERS];
//# sourceMappingURL=browser.js.map