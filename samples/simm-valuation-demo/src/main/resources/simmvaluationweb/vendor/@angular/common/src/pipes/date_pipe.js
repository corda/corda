/**
 * @license
 * Copyright Google Inc. All Rights Reserved.
 *
 * Use of this source code is governed by an MIT-style license that can be
 * found in the LICENSE file at https://angular.io/license
 */
"use strict";
var core_1 = require('@angular/core');
var collection_1 = require('../facade/collection');
var intl_1 = require('../facade/intl');
var lang_1 = require('../facade/lang');
var invalid_pipe_argument_exception_1 = require('./invalid_pipe_argument_exception');
// TODO: move to a global configurable location along with other i18n components.
var defaultLocale = 'en-US';
var DatePipe = (function () {
    function DatePipe() {
    }
    DatePipe.prototype.transform = function (value, pattern) {
        if (pattern === void 0) { pattern = 'mediumDate'; }
        if (lang_1.isBlank(value))
            return null;
        if (!this.supports(value)) {
            throw new invalid_pipe_argument_exception_1.InvalidPipeArgumentException(DatePipe, value);
        }
        if (lang_1.NumberWrapper.isNumeric(value)) {
            value = lang_1.DateWrapper.fromMillis(lang_1.NumberWrapper.parseInt(value, 10));
        }
        else if (lang_1.isString(value)) {
            value = lang_1.DateWrapper.fromISOString(value);
        }
        if (collection_1.StringMapWrapper.contains(DatePipe._ALIASES, pattern)) {
            pattern = collection_1.StringMapWrapper.get(DatePipe._ALIASES, pattern);
        }
        return intl_1.DateFormatter.format(value, defaultLocale, pattern);
    };
    DatePipe.prototype.supports = function (obj) {
        if (lang_1.isDate(obj) || lang_1.NumberWrapper.isNumeric(obj)) {
            return true;
        }
        if (lang_1.isString(obj) && lang_1.isDate(lang_1.DateWrapper.fromISOString(obj))) {
            return true;
        }
        return false;
    };
    /** @internal */
    DatePipe._ALIASES = {
        'medium': 'yMMMdjms',
        'short': 'yMdjm',
        'fullDate': 'yMMMMEEEEd',
        'longDate': 'yMMMMd',
        'mediumDate': 'yMMMd',
        'shortDate': 'yMd',
        'mediumTime': 'jms',
        'shortTime': 'jm'
    };
    /** @nocollapse */
    DatePipe.decorators = [
        { type: core_1.Pipe, args: [{ name: 'date', pure: true },] },
    ];
    return DatePipe;
}());
exports.DatePipe = DatePipe;
//# sourceMappingURL=date_pipe.js.map