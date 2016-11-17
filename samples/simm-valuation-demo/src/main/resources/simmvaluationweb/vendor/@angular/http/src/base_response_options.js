/**
 * @license
 * Copyright Google Inc. All Rights Reserved.
 *
 * Use of this source code is governed by an MIT-style license that can be
 * found in the LICENSE file at https://angular.io/license
 */
"use strict";
var __extends = (this && this.__extends) || function (d, b) {
    for (var p in b) if (b.hasOwnProperty(p)) d[p] = b[p];
    function __() { this.constructor = d; }
    d.prototype = b === null ? Object.create(b) : (__.prototype = b.prototype, new __());
};
var core_1 = require('@angular/core');
var lang_1 = require('../src/facade/lang');
var enums_1 = require('./enums');
var headers_1 = require('./headers');
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
var ResponseOptions = (function () {
    function ResponseOptions(_a) {
        var _b = _a === void 0 ? {} : _a, body = _b.body, status = _b.status, headers = _b.headers, statusText = _b.statusText, type = _b.type, url = _b.url;
        this.body = lang_1.isPresent(body) ? body : null;
        this.status = lang_1.isPresent(status) ? status : null;
        this.headers = lang_1.isPresent(headers) ? headers : null;
        this.statusText = lang_1.isPresent(statusText) ? statusText : null;
        this.type = lang_1.isPresent(type) ? type : null;
        this.url = lang_1.isPresent(url) ? url : null;
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
    ResponseOptions.prototype.merge = function (options) {
        return new ResponseOptions({
            body: lang_1.isPresent(options) && lang_1.isPresent(options.body) ? options.body : this.body,
            status: lang_1.isPresent(options) && lang_1.isPresent(options.status) ? options.status : this.status,
            headers: lang_1.isPresent(options) && lang_1.isPresent(options.headers) ? options.headers : this.headers,
            statusText: lang_1.isPresent(options) && lang_1.isPresent(options.statusText) ? options.statusText :
                this.statusText,
            type: lang_1.isPresent(options) && lang_1.isPresent(options.type) ? options.type : this.type,
            url: lang_1.isPresent(options) && lang_1.isPresent(options.url) ? options.url : this.url,
        });
    };
    return ResponseOptions;
}());
exports.ResponseOptions = ResponseOptions;
var BaseResponseOptions = (function (_super) {
    __extends(BaseResponseOptions, _super);
    function BaseResponseOptions() {
        _super.call(this, { status: 200, statusText: 'Ok', type: enums_1.ResponseType.Default, headers: new headers_1.Headers() });
    }
    /** @nocollapse */
    BaseResponseOptions.decorators = [
        { type: core_1.Injectable },
    ];
    /** @nocollapse */
    BaseResponseOptions.ctorParameters = [];
    return BaseResponseOptions;
}(ResponseOptions));
exports.BaseResponseOptions = BaseResponseOptions;
//# sourceMappingURL=base_response_options.js.map