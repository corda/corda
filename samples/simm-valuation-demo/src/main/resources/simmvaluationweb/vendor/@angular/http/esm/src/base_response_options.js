/**
 * @license
 * Copyright Google Inc. All Rights Reserved.
 *
 * Use of this source code is governed by an MIT-style license that can be
 * found in the LICENSE file at https://angular.io/license
 */
import { Injectable } from '@angular/core';
import { isPresent } from '../src/facade/lang';
import { ResponseType } from './enums';
import { Headers } from './headers';
/**
 * Creates a response options object to be optionally provided when instantiating a
 * {@link Response}.
 *
 * This class is based on the `ResponseInit` description in the [Fetch
 * Spec](https://fetch.spec.whatwg.org/#responseinit).
 *
 * All values are null by default. Typical defaults can be found in the
 * {@link BaseResponseOptions} class, which sub-classes `ResponseOptions`.
 *
 * This class may be used in tests to build {@link Response Responses} for
 * mock responses (see {@link MockBackend}).
 *
 * ### Example ([live demo](http://plnkr.co/edit/P9Jkk8e8cz6NVzbcxEsD?p=preview))
 *
 * ```typescript
 * import {ResponseOptions, Response} from '@angular/http';
 *
 * var options = new ResponseOptions({
 *   body: '{"name":"Jeff"}'
 * });
 * var res = new Response(options);
 *
 * console.log('res.json():', res.json()); // Object {name: "Jeff"}
 * ```
 *
 * @experimental
 */
export class ResponseOptions {
    constructor({ body, status, headers, statusText, type, url } = {}) {
        this.body = isPresent(body) ? body : null;
        this.status = isPresent(status) ? status : null;
        this.headers = isPresent(headers) ? headers : null;
        this.statusText = isPresent(statusText) ? statusText : null;
        this.type = isPresent(type) ? type : null;
        this.url = isPresent(url) ? url : null;
    }
    /**
     * Creates a copy of the `ResponseOptions` instance, using the optional input as values to
     * override
     * existing values. This method will not change the values of the instance on which it is being
     * called.
     *
     * This may be useful when sharing a base `ResponseOptions` object inside tests,
     * where certain properties may change from test to test.
     *
     * ### Example ([live demo](http://plnkr.co/edit/1lXquqFfgduTFBWjNoRE?p=preview))
     *
     * ```typescript
     * import {ResponseOptions, Response} from '@angular/http';
     *
     * var options = new ResponseOptions({
     *   body: {name: 'Jeff'}
     * });
     * var res = new Response(options.merge({
     *   url: 'https://google.com'
     * }));
     * console.log('options.url:', options.url); // null
     * console.log('res.json():', res.json()); // Object {name: "Jeff"}
     * console.log('res.url:', res.url); // https://google.com
     * ```
     */
    merge(options) {
        return new ResponseOptions({
            body: isPresent(options) && isPresent(options.body) ? options.body : this.body,
            status: isPresent(options) && isPresent(options.status) ? options.status : this.status,
            headers: isPresent(options) && isPresent(options.headers) ? options.headers : this.headers,
            statusText: isPresent(options) && isPresent(options.statusText) ? options.statusText :
                this.statusText,
            type: isPresent(options) && isPresent(options.type) ? options.type : this.type,
            url: isPresent(options) && isPresent(options.url) ? options.url : this.url,
        });
    }
}
export class BaseResponseOptions extends ResponseOptions {
    constructor() {
        super({ status: 200, statusText: 'Ok', type: ResponseType.Default, headers: new Headers() });
    }
}
/** @nocollapse */
BaseResponseOptions.decorators = [
    { type: Injectable },
];
/** @nocollapse */
BaseResponseOptions.ctorParameters = [];
//# sourceMappingURL=base_response_options.js.map