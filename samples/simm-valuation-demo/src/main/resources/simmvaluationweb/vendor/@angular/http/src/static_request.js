/**
 * @license
 * Copyright Google Inc. All Rights Reserved.
 *
 * Use of this source code is governed by an MIT-style license that can be
 * found in the LICENSE file at https://angular.io/license
 */
"use strict";
var lang_1 = require('../src/facade/lang');
var enums_1 = require('./enums');
var headers_1 = require('./headers');
var http_utils_1 = require('./http_utils');
var url_search_params_1 = require('./url_search_params');
// TODO(jeffbcross): properly implement body accessors
/**
 * Creates `Request` instances from provided values.
 *
 * The Request's interface is inspired by the Request constructor defined in the [Fetch
 * Spec](https://fetch.spec.whatwg.org/#request-class),
 * but is considered a static value whose body can be accessed many times. There are other
 * differences in the implementation, but this is the most significant.
 *
 * `Request` instances are typically created by higher-level classes, like {@link Http} and
 * {@link Jsonp}, but it may occasionally be useful to explicitly create `Request` instances.
 * One such example is when creating services that wrap higher-level services, like {@link Http},
 * where it may be useful to generate a `Request` with arbitrary headers and search params.
 *
 * ```typescript
 * import {Injectable, Injector} from '@angular/core';
 * import {HTTP_PROVIDERS, Http, Request, RequestMethod} from '@angular/http';
 *
 * @Injectable()
 * class AutoAuthenticator {
 *   constructor(public http:Http) {}
 *   request(url:string) {
 *     return this.http.request(new Request({
 *       method: RequestMethod.Get,
 *       url: url,
 *       search: 'password=123'
 *     }));
 *   }
 * }
 *
 * var injector = Injector.resolveAndCreate([HTTP_PROVIDERS, AutoAuthenticator]);
 * var authenticator = injector.get(AutoAuthenticator);
 * authenticator.request('people.json').subscribe(res => {
 *   //URL should have included '?password=123'
 *   console.log('people', res.json());
 * });
 * ```
 *
 * @experimental
 */
var Request = (function () {
    function Request(requestOptions) {
        // TODO: assert that url is present
        var url = requestOptions.url;
        this.url = requestOptions.url;
        if (lang_1.isPresent(requestOptions.search)) {
            var search = requestOptions.search.toString();
            if (search.length > 0) {
                var prefix = '?';
                if (lang_1.StringWrapper.contains(this.url, '?')) {
                    prefix = (this.url[this.url.length - 1] == '&') ? '' : '&';
                }
                // TODO: just delete search-query-looking string in url?
                this.url = url + prefix + search;
            }
        }
        this._body = requestOptions.body;
        this.contentType = this.detectContentType();
        this.method = http_utils_1.normalizeMethodName(requestOptions.method);
        // TODO(jeffbcross): implement behavior
        // Defaults to 'omit', consistent with browser
        // TODO(jeffbcross): implement behavior
        this.headers = new headers_1.Headers(requestOptions.headers);
        this.withCredentials = requestOptions.withCredentials;
    }
    /**
     * Returns the request's body as string, assuming that body exists. If body is undefined, return
     * empty
     * string.
     */
    Request.prototype.text = function () { return lang_1.isPresent(this._body) ? this._body.toString() : ''; };
    /**
     * Returns the request's body as JSON string, assuming that body exists. If body is undefined,
     * return
     * empty
     * string.
     */
    Request.prototype.json = function () { return lang_1.isPresent(this._body) ? JSON.stringify(this._body) : ''; };
    /**
     * Returns the request's body as array buffer, assuming that body exists. If body is undefined,
     * return
     * null.
     */
    Request.prototype.arrayBuffer = function () {
        if (this._body instanceof ArrayBuffer)
            return this._body;
        throw 'The request body isn\'t an array buffer';
    };
    /**
     * Returns the request's body as blob, assuming that body exists. If body is undefined, return
     * null.
     */
    Request.prototype.blob = function () {
        if (this._body instanceof Blob)
            return this._body;
        if (this._body instanceof ArrayBuffer)
            return new Blob([this._body]);
        throw 'The request body isn\'t either a blob or an array buffer';
    };
    /**
     * Returns the content type of request's body based on its type.
     */
    Request.prototype.detectContentType = function () {
        if (this._body == null) {
            return enums_1.ContentType.NONE;
        }
        else if (this._body instanceof url_search_params_1.URLSearchParams) {
            return enums_1.ContentType.FORM;
        }
        else if (this._body instanceof FormData) {
            return enums_1.ContentType.FORM_DATA;
        }
        else if (this._body instanceof Blob) {
            return enums_1.ContentType.BLOB;
        }
        else if (this._body instanceof ArrayBuffer) {
            return enums_1.ContentType.ARRAY_BUFFER;
        }
        else if (this._body && typeof this._body == 'object') {
            return enums_1.ContentType.JSON;
        }
        else {
            return enums_1.ContentType.TEXT;
        }
    };
    /**
     * Returns the request's body according to its type. If body is undefined, return
     * null.
     */
    Request.prototype.getBody = function () {
        switch (this.contentType) {
            case enums_1.ContentType.JSON:
                return this.json();
            case enums_1.ContentType.FORM:
                return this.text();
            case enums_1.ContentType.FORM_DATA:
                return this._body;
            case enums_1.ContentType.TEXT:
                return this.text();
            case enums_1.ContentType.BLOB:
                return this.blob();
            case enums_1.ContentType.ARRAY_BUFFER:
                return this.arrayBuffer();
            default:
                return null;
        }
    };
    return Request;
}());
exports.Request = Request;
var noop = function () { };
var w = typeof window == 'object' ? window : noop;
var FormData = w['FormData'] || noop;
var Blob = w['Blob'] || noop;
var ArrayBuffer = w['ArrayBuffer'] || noop;
//# sourceMappingURL=static_request.js.map