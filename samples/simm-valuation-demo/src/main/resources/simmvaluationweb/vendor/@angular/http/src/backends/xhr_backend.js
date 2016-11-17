/**
 * @license
 * Copyright Google Inc. All Rights Reserved.
 *
 * Use of this source code is governed by an MIT-style license that can be
 * found in the LICENSE file at https://angular.io/license
 */
"use strict";
var core_1 = require('@angular/core');
var platform_browser_1 = require('@angular/platform-browser');
var Observable_1 = require('rxjs/Observable');
var base_response_options_1 = require('../base_response_options');
var enums_1 = require('../enums');
var lang_1 = require('../facade/lang');
var headers_1 = require('../headers');
var http_utils_1 = require('../http_utils');
var interfaces_1 = require('../interfaces');
var static_response_1 = require('../static_response');
var browser_xhr_1 = require('./browser_xhr');
var XSSI_PREFIX = /^\)\]\}',?\n/;
/**
 * Creates connections using `XMLHttpRequest`. Given a fully-qualified
 * request, an `XHRConnection` will immediately create an `XMLHttpRequest` object and send the
 * request.
 *
 * This class would typically not be created or interacted with directly inside applications, though
 * the {@link MockConnection} may be interacted with in tests.
 *
 * @experimental
 */
var XHRConnection = (function () {
    function XHRConnection(req, browserXHR, baseResponseOptions) {
        var _this = this;
        this.request = req;
        this.response = new Observable_1.Observable(function (responseObserver) {
            var _xhr = browserXHR.build();
            _xhr.open(enums_1.RequestMethod[req.method].toUpperCase(), req.url);
            if (lang_1.isPresent(req.withCredentials)) {
                _xhr.withCredentials = req.withCredentials;
            }
            // load event handler
            var onLoad = function () {
                // responseText is the old-school way of retrieving response (supported by IE8 & 9)
                // response/responseType properties were introduced in XHR Level2 spec (supported by
                // IE10)
                var body = lang_1.isPresent(_xhr.response) ? _xhr.response : _xhr.responseText;
                // Implicitly strip a potential XSSI prefix.
                if (lang_1.isString(body))
                    body = body.replace(XSSI_PREFIX, '');
                var headers = headers_1.Headers.fromResponseHeaderString(_xhr.getAllResponseHeaders());
                var url = http_utils_1.getResponseURL(_xhr);
                // normalize IE9 bug (http://bugs.jquery.com/ticket/1450)
                var status = _xhr.status === 1223 ? 204 : _xhr.status;
                // fix status code when it is 0 (0 status is undocumented).
                // Occurs when accessing file resources or on Android 4.1 stock browser
                // while retrieving files from application cache.
                if (status === 0) {
                    status = body ? 200 : 0;
                }
                var statusText = _xhr.statusText || 'OK';
                var responseOptions = new base_response_options_1.ResponseOptions({ body: body, status: status, headers: headers, statusText: statusText, url: url });
                if (lang_1.isPresent(baseResponseOptions)) {
                    responseOptions = baseResponseOptions.merge(responseOptions);
                }
                var response = new static_response_1.Response(responseOptions);
                response.ok = http_utils_1.isSuccess(status);
                if (response.ok) {
                    responseObserver.next(response);
                    // TODO(gdi2290): defer complete if array buffer until done
                    responseObserver.complete();
                    return;
                }
                responseObserver.error(response);
            };
            // error event handler
            var onError = function (err) {
                var responseOptions = new base_response_options_1.ResponseOptions({
                    body: err,
                    type: enums_1.ResponseType.Error,
                    status: _xhr.status,
                    statusText: _xhr.statusText,
                });
                if (lang_1.isPresent(baseResponseOptions)) {
                    responseOptions = baseResponseOptions.merge(responseOptions);
                }
                responseObserver.error(new static_response_1.Response(responseOptions));
            };
            _this.setDetectedContentType(req, _xhr);
            if (lang_1.isPresent(req.headers)) {
                req.headers.forEach(function (values, name) { return _xhr.setRequestHeader(name, values.join(',')); });
            }
            _xhr.addEventListener('load', onLoad);
            _xhr.addEventListener('error', onError);
            _xhr.send(_this.request.getBody());
            return function () {
                _xhr.removeEventListener('load', onLoad);
                _xhr.removeEventListener('error', onError);
                _xhr.abort();
            };
        });
    }
    XHRConnection.prototype.setDetectedContentType = function (req /** TODO #9100 */, _xhr /** TODO #9100 */) {
        // Skip if a custom Content-Type header is provided
        if (lang_1.isPresent(req.headers) && lang_1.isPresent(req.headers.get('Content-Type'))) {
            return;
        }
        // Set the detected content type
        switch (req.contentType) {
            case enums_1.ContentType.NONE:
                break;
            case enums_1.ContentType.JSON:
                _xhr.setRequestHeader('Content-Type', 'application/json');
                break;
            case enums_1.ContentType.FORM:
                _xhr.setRequestHeader('Content-Type', 'application/x-www-form-urlencoded;charset=UTF-8');
                break;
            case enums_1.ContentType.TEXT:
                _xhr.setRequestHeader('Content-Type', 'text/plain');
                break;
            case enums_1.ContentType.BLOB:
                var blob = req.blob();
                if (blob.type) {
                    _xhr.setRequestHeader('Content-Type', blob.type);
                }
                break;
        }
    };
    return XHRConnection;
}());
exports.XHRConnection = XHRConnection;
/**
 * `XSRFConfiguration` sets up Cross Site Request Forgery (XSRF) protection for the application
 * using a cookie. See https://www.owasp.org/index.php/Cross-Site_Request_Forgery_(CSRF) for more
 * information on XSRF.
 *
 * Applications can configure custom cookie and header names by binding an instance of this class
 * with different `cookieName` and `headerName` values. See the main HTTP documentation for more
 * details.
 *
 * @experimental
 */
var CookieXSRFStrategy = (function () {
    function CookieXSRFStrategy(_cookieName, _headerName) {
        if (_cookieName === void 0) { _cookieName = 'XSRF-TOKEN'; }
        if (_headerName === void 0) { _headerName = 'X-XSRF-TOKEN'; }
        this._cookieName = _cookieName;
        this._headerName = _headerName;
    }
    CookieXSRFStrategy.prototype.configureRequest = function (req) {
        var xsrfToken = platform_browser_1.__platform_browser_private__.getDOM().getCookie(this._cookieName);
        if (xsrfToken && !req.headers.has(this._headerName)) {
            req.headers.set(this._headerName, xsrfToken);
        }
    };
    return CookieXSRFStrategy;
}());
exports.CookieXSRFStrategy = CookieXSRFStrategy;
var XHRBackend = (function () {
    function XHRBackend(_browserXHR, _baseResponseOptions, _xsrfStrategy) {
        this._browserXHR = _browserXHR;
        this._baseResponseOptions = _baseResponseOptions;
        this._xsrfStrategy = _xsrfStrategy;
    }
    XHRBackend.prototype.createConnection = function (request) {
        this._xsrfStrategy.configureRequest(request);
        return new XHRConnection(request, this._browserXHR, this._baseResponseOptions);
    };
    /** @nocollapse */
    XHRBackend.decorators = [
        { type: core_1.Injectable },
    ];
    /** @nocollapse */
    XHRBackend.ctorParameters = [
        { type: browser_xhr_1.BrowserXhr, },
        { type: base_response_options_1.ResponseOptions, },
        { type: interfaces_1.XSRFStrategy, },
    ];
    return XHRBackend;
}());
exports.XHRBackend = XHRBackend;
//# sourceMappingURL=xhr_backend.js.map