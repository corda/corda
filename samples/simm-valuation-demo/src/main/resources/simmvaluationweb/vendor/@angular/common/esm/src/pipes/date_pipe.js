/**
 * @license
 * Copyright Google Inc. All Rights Reserved.
 *
 * Use of this source code is governed by an MIT-style license that can be
 * found in the LICENSE file at https://angular.io/license
 */
import { Pipe } from '@angular/core';
import { StringMapWrapper } from '../facade/collection';
import { DateFormatter } from '../facade/intl';
import { DateWrapper, NumberWrapper, isBlank, isDate, isString } from '../facade/lang';
import { InvalidPipeArgumentException } from './invalid_pipe_argument_exception';
// TODO: move to a global configurable location along with other i18n components.
var defaultLocale = 'en-US';
export class DatePipe {
    transform(value, pattern = 'mediumDate') {
        if (isBlank(value))
            return null;
        if (!this.supports(value)) {
            throw new InvalidPipeArgumentException(DatePipe, value);
        }
        if (NumberWrapper.isNumeric(value)) {
            value = DateWrapper.fromMillis(NumberWrapper.parseInt(value, 10));
        }
        else if (isString(value)) {
            value = DateWrapper.fromISOString(value);
        }
        if (StringMapWrapper.contains(DatePipe._ALIASES, pattern)) {
            pattern = StringMapWrapper.get(DatePipe._ALIASES, pattern);
        }
        return DateFormatter.format(value, defaultLocale, pattern);
    }
    supports(obj) {
        if (isDate(obj) || NumberWrapper.isNumeric(obj)) {
            return true;
        }
        if (isString(obj) && isDate(DateWrapper.fromISOString(obj))) {
            return true;
        }
        return false;
    }
}
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
    { type: Pipe, args: [{ name: 'date', pure: true },] },
];
//# sourceMappingURL=date_pipe.js.map