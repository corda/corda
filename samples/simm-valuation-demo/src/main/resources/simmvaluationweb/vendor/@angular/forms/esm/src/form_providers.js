/**
 * @license
 * Copyright Google Inc. All Rights Reserved.
 *
 * Use of this source code is governed by an MIT-style license that can be
 * found in the LICENSE file at https://angular.io/license
 */
import { FORM_DIRECTIVES as OLD_FORM_DIRECTIVES } from '@angular/common';
import { CompilerConfig } from '@angular/compiler';
import { PLATFORM_DIRECTIVES, PLATFORM_PIPES } from '@angular/core';
import { FORM_DIRECTIVES as NEW_FORM_DIRECTIVES } from './directives';
import { RadioControlRegistry as NewRadioControlRegistry } from './directives/radio_control_value_accessor';
import { ListWrapper } from './facade/collection';
import { FormBuilder as NewFormBuilder } from './form_builder';
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
export const FORM_PROVIDERS = [NewFormBuilder, NewRadioControlRegistry];
function flatten(platformDirectives) {
    let flattenedDirectives = [];
    platformDirectives.forEach((directives) => {
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
export function disableDeprecatedForms() {
    return [{
            provide: CompilerConfig,
            useFactory: (platformDirectives, platformPipes) => {
                const flattenedDirectives = flatten(platformDirectives);
                ListWrapper.remove(flattenedDirectives, OLD_FORM_DIRECTIVES);
                return new CompilerConfig({ platformDirectives: flattenedDirectives, platformPipes });
            },
            deps: [PLATFORM_DIRECTIVES, PLATFORM_PIPES]
        }];
}
/**
 * @experimental
 */
export function provideForms() {
    return [
        { provide: PLATFORM_DIRECTIVES, useValue: NEW_FORM_DIRECTIVES, multi: true }, FORM_PROVIDERS
    ];
}
//# sourceMappingURL=form_providers.js.map