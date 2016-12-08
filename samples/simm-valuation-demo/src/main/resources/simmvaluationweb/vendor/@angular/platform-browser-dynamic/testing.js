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
var compiler_1 = require('@angular/compiler');
var testing_1 = require('@angular/compiler/testing');
var testing_2 = require('@angular/core/testing');
var testing_3 = require('@angular/platform-browser/testing');
var index_1 = require('./index');
var dom_test_component_renderer_1 = require('./testing/dom_test_component_renderer');
__export(require('./private_export_testing'));
/**
 * Default platform providers for testing.
 *
 * @stable
 */
exports.TEST_BROWSER_DYNAMIC_PLATFORM_PROVIDERS = [testing_3.TEST_BROWSER_PLATFORM_PROVIDERS];
/**
 * Default application providers for testing.
 *
 * @stable
 */
exports.TEST_BROWSER_DYNAMIC_APPLICATION_PROVIDERS = [
    testing_3.TEST_BROWSER_APPLICATION_PROVIDERS, index_1.BROWSER_APP_COMPILER_PROVIDERS,
    [
        { provide: testing_2.TestComponentBuilder, useClass: testing_1.OverridingTestComponentBuilder },
        { provide: compiler_1.DirectiveResolver, useClass: testing_1.MockDirectiveResolver },
        { provide: compiler_1.ViewResolver, useClass: testing_1.MockViewResolver },
        { provide: testing_2.TestComponentRenderer, useClass: dom_test_component_renderer_1.DOMTestComponentRenderer },
    ]
];
//# sourceMappingURL=testing.js.map