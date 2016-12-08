/**
 * @license
 * Copyright Google Inc. All Rights Reserved.
 *
 * Use of this source code is governed by an MIT-style license that can be
 * found in the LICENSE file at https://angular.io/license
 */
"use strict";
var common_1 = require('@angular/common');
var compiler_1 = require('@angular/compiler');
var core_1 = require('@angular/core');
var directives_1 = require('./directives');
var radio_control_value_accessor_1 = require('./directives/radio_control_value_accessor');
var collection_1 = require('./facade/collection');
var form_builder_1 = require('./form_builder');
/**
 * Shorthand set of providers used for building Angular forms.
 *
 * ### Example
 *
 * ```typescript
 * bootstrap(MyApp, [FORM_PROVIDERS]);
 * ```
 *
 * @experimental
 */
exports.FORM_PROVIDERS = [form_builder_1.FormBuilder, radio_control_value_accessor_1.RadioControlRegistry];
function flatten(platformDirectives) {
    var flattenedDirectives = [];
    platformDirectives.forEach(function (directives) {
        if (Array.isArray(directives)) {
            flattenedDirectives = flattenedDirectives.concat(directives);
        }
        else {
            flattenedDirectives.push(directives);
        }
    });
    return flattenedDirectives;
}
/**
 * @experimental
 */
function disableDeprecatedForms() {
    return [{
            provide: compiler_1.CompilerConfig,
            useFactory: function (platformDirectives, platformPipes) {
                var flattenedDirectives = flatten(platformDirectives);
                collection_1.ListWrapper.remove(flattenedDirectives, common_1.FORM_DIRECTIVES);
                return new compiler_1.CompilerConfig({ platformDirectives: flattenedDirectives, platformPipes: platformPipes });
            },
            deps: [core_1.PLATFORM_DIRECTIVES, core_1.PLATFORM_PIPES]
        }];
}
exports.disableDeprecatedForms = disableDeprecatedForms;
/**
 * @experimental
 */
function provideForms() {
    return [
        { provide: core_1.PLATFORM_DIRECTIVES, useValue: directives_1.FORM_DIRECTIVES, multi: true }, exports.FORM_PROVIDERS
    ];
}
exports.provideForms = provideForms;
//# sourceMappingURL=form_providers.js.map