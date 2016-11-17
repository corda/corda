/**
 * @license
 * Copyright Google Inc. All Rights Reserved.
 *
 * Use of this source code is governed by an MIT-style license that can be
 * found in the LICENSE file at https://angular.io/license
 */
import { makeTypeError } from '../src/facade/exceptions';
import { isString } from '../src/facade/lang';
import { RequestMethod } from './enums';
export function normalizeMethodName(method) {
    if (isString(method)) {
        var originalMethod = method;
        method = method
            .replace(/(\w)(\w*)/g, (g0, g1, g2) => g1.toUpperCase() + g2.toLowerCase());
        method = RequestMethod[method];
        if (typeof method !== 'number')
            throw makeTypeError(`Invalid request method. The method "${originalMethod}" is not supported.`);
    }
    return method;
}
export const isSuccess = (status) => (status >= 200 && status < 300);
export function getResponseURL(xhr) {
    if ('responseURL' in xhr) {
        return xhr.responseURL;
    }
    if (/^X-Request-URL:/m.test(xhr.getAllResponseHeaders())) {
        return xhr.getResponseHeader('X-Request-URL');
    }
    return;
}
export { isJsObject } from '../src/facade/lang';
//# sourceMappingURL=http_utils.js.map