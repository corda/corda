/**
 * @license
 * Copyright Google Inc. All Rights Reserved.
 *
 * Use of this source code is governed by an MIT-style license that can be
 * found in the LICENSE file at https://angular.io/license
 */
"use strict";
/**
 * @module
 * @description
 * This module provides a set of common Pipes.
 */
var async_pipe_1 = require('./async_pipe');
var date_pipe_1 = require('./date_pipe');
var i18n_plural_pipe_1 = require('./i18n_plural_pipe');
var i18n_select_pipe_1 = require('./i18n_select_pipe');
var json_pipe_1 = require('./json_pipe');
var lowercase_pipe_1 = require('./lowercase_pipe');
var number_pipe_1 = require('./number_pipe');
var replace_pipe_1 = require('./replace_pipe');
var slice_pipe_1 = require('./slice_pipe');
var uppercase_pipe_1 = require('./uppercase_pipe');
/**
 * A collection of Angular core pipes that are likely to be used in each and every
 * application.
 *
 * This collection can be used to quickly enumerate all the built-in pipes in the `pipes`
 * property of the `@Component` decorator.
 *
 * @experimental Contains i18n pipes which are experimental
 */
exports.COMMON_PIPES = [
    async_pipe_1.AsyncPipe,
    uppercase_pipe_1.UpperCasePipe,
    lowercase_pipe_1.LowerCasePipe,
    json_pipe_1.JsonPipe,
    slice_pipe_1.SlicePipe,
    number_pipe_1.DecimalPipe,
    number_pipe_1.PercentPipe,
    number_pipe_1.CurrencyPipe,
    date_pipe_1.DatePipe,
    replace_pipe_1.ReplacePipe,
    i18n_plural_pipe_1.I18nPluralPipe,
    i18n_select_pipe_1.I18nSelectPipe,
];
//# sourceMappingURL=common_pipes.js.map