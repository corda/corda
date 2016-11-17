/**
 * @license
 * Copyright Google Inc. All Rights Reserved.
 *
 * Use of this source code is governed by an MIT-style license that can be
 * found in the LICENSE file at https://angular.io/license
 */
import { BaseException } from '../src/facade/exceptions';
import { Json, isString } from '../src/facade/lang';
import { isJsObject } from './http_utils';
/**
 * Creates `Response` instances from provided values.
 *
 * Though this object isn't
 * usually instantiated by end-users, it is the primary object interacted with when it comes time to
 * add data to a view.
 *
 * ### Example
 *
 * ```
 * http.request('my-friends.txt').subscribe(response => this.friends = response.text());
 * ```
 *
 * The Response's interface is inspired by the Response constructor defined in the [Fetch
 * Spec](https://fetch.spec.whatwg.org/#response-class), but is considered a static value whose body
 * can be accessed many times. There are other differences in the implementation, but this is the
 * most significant.
 *
 * @experimental
 */
export class Response {
    constructor(responseOptions) {
        this._body = responseOptions.body;
        this.status = responseOptions.status;
        this.ok = (this.status >= 200 && this.status <= 299);
        this.statusText = responseOptions.statusText;
        this.headers = responseOptions.headers;
        this.type = responseOptions.type;
        this.url = responseOptions.url;
    }
    /**
     * Not yet implemented
     */
    // TODO: Blob return type
    blob() { throw new BaseException('"blob()" method not implemented on Response superclass'); }
    /**
     * Attempts to return body as parsed `JSON` object, or raises an exception.
     */
    json() {
        var jsonResponse;
        if (isJsObject(this._body)) {
            jsonResponse = this._body;
        }
        else if (isString(this._body)) {
            jsonResponse = Json.parse(this._body);
        }
        return jsonResponse;
    }
    /**
     * Returns the body as a string, presuming `toString()` can be called on the response body.
     */
    text() { return this._body.toString(); }
    /**
     * Not yet implemented
     */
    // TODO: ArrayBuffer return type
    arrayBuffer() {
        throw new BaseException('"arrayBuffer()" method not implemented on Response superclass');
    }
    toString() {
        return `Response with status: ${this.status} ${this.statusText} for URL: ${this.url}`;
    }
}
//# sourceMappingURL=static_response.js.map