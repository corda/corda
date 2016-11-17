/**
 * @license
 * Copyright Google Inc. All Rights Reserved.
 *
 * Use of this source code is governed by an MIT-style license that can be
 * found in the LICENSE file at https://angular.io/license
 */
"use strict";
var ng_class_1 = require('./ng_class');
var ng_for_1 = require('./ng_for');
var ng_if_1 = require('./ng_if');
var ng_plural_1 = require('./ng_plural');
var ng_style_1 = require('./ng_style');
var ng_switch_1 = require('./ng_switch');
var ng_template_outlet_1 = require('./ng_template_outlet');
/**
 * A collection of Angular core directives that are likely to be used in each and every Angular
 * application.
 *
 * This collection can be used to quickly enumerate all the built-in directives in the `directives`
 * property of the `@Component` annotation.
 *
 * ### Example ([live demo](http://plnkr.co/edit/yakGwpCdUkg0qfzX5m8g?p=preview))
 *
 * Instead of writing:
 *
 * ```typescript
 * import {NgClass, NgIf, NgFor, NgSwitch, NgSwitchWhen, NgSwitchDefault} from '@angular/common';
 * import {OtherDirective} from './myDirectives';
 *
 * @Component({
 *   selector: 'my-component',
 *   templateUrl: 'myComponent.html',
 *   directives: [NgClass, NgIf, NgFor, NgSwitch, NgSwitchWhen, NgSwitchDefault, OtherDirective]
 * })
 * export class MyComponent {
 *   ...
 * }
 * ```
 * one could import all the core directives at once:
 *
 * ```typescript
 * import {CORE_DIRECTIVES} from '@angular/common';
 * import {OtherDirective} from './myDirectives';
 *
 * @Component({
 *   selector: 'my-component',
 *   templateUrl: 'myComponent.html',
 *   directives: [CORE_DIRECTIVES, OtherDirective]
 * })
 * export class MyComponent {
 *   ...
 * }
 * ```
 *
 * @stable
 */
exports.CORE_DIRECTIVES = [
    ng_class_1.NgClass,
    ng_for_1.NgFor,
    ng_if_1.NgIf,
    ng_template_outlet_1.NgTemplateOutlet,
    ng_style_1.NgStyle,
    ng_switch_1.NgSwitch,
    ng_switch_1.NgSwitchCase,
    ng_switch_1.NgSwitchDefault,
    ng_plural_1.NgPlural,
    ng_plural_1.NgPluralCase,
];
//# sourceMappingURL=core_directives.js.map