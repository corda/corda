/**
 * @license
 * Copyright Google Inc. All Rights Reserved.
 *
 * Use of this source code is governed by an MIT-style license that can be
 * found in the LICENSE file at https://angular.io/license
 */
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs/Observable';
import { ResponseOptions } from '../base_response_options';
import { ReadyState, RequestMethod, ResponseType } from '../enums';
import { makeTypeError } from '../facade/exceptions';
import { StringWrapper, isPresent } from '../facade/lang';
import { ConnectionBackend } from '../interfaces';
import { Response } from '../static_response';
import { BrowserJsonp } from './browser_jsonp';
const JSONP_ERR_NO_CALLBACK = 'JSONP injected script did not invoke callback.';
const JSONP_ERR_WRONG_METHOD = 'JSONP requests must use GET request method.';
/**
 * Abstract base class for an in-flight JSONP request.
 *
 * @experimental
 */
export class JSONPConnection {
}
export class JSONPConnection_ extends JSONPConnection {
    constructor(req, _dom, baseResponseOptions) {
        super();
        this._dom = _dom;
        this.baseResponseOptions = baseResponseOptions;
        this._finished = false;
        if (req.method !== RequestMethod.Get) {
            throw makeTypeError(JSONP_ERR_WRONG_METHOD);
        }
        this.request = req;
        this.response = new Observable((responseObserver) => {
            this.readyState = ReadyState.Loading;
            let id = this._id = _dom.nextRequestID();
            _dom.exposeConnection(id, this);
            // Workaround Dart
            // url = url.replace(/=JSONP_CALLBACK(&|$)/, `generated method`);
            let callback = _dom.requestCallback(this._id);
            let url = req.url;
            if (url.indexOf('=JSONP_CALLBACK&') > -1) {
                url = StringWrapper.replace(url, '=JSONP_CALLBACK&', `=${callback}&`);
            }
            else if (url.lastIndexOf('=JSONP_CALLBACK') === url.length - '=JSONP_CALLBACK'.length) {
                url = url.substring(0, url.length - '=JSONP_CALLBACK'.length) + `=${callback}`;
            }
            let script = this._script = _dom.build(url);
            let onLoad = (event) => {
                if (this.readyState === ReadyState.Cancelled)
                    return;
                this.readyState = ReadyState.Done;
                _dom.cleanup(script);
                if (!this._finished) {
                    let responseOptions = new ResponseOptions({ body: JSONP_ERR_NO_CALLBACK, type: ResponseType.Error, url });
                    if (isPresent(baseResponseOptions)) {
                        responseOptions = baseResponseOptions.merge(responseOptions);
                    }
                    responseObserver.error(new Response(responseOptions));
                    return;
                }
                let responseOptions = new ResponseOptions({ body: this._responseData, url });
                if (isPresent(this.baseResponseOptions)) {
                    responseOptions = this.baseResponseOptions.merge(responseOptions);
                }
                responseObserver.next(new Response(responseOptions));
                responseObserver.complete();
            };
            let onError = (error) => {
                if (this.readyState === ReadyState.Cancelled)
                    return;
                this.readyState = ReadyState.Done;
                _dom.cleanup(script);
                let responseOptions = new ResponseOptions({ body: error.message, type: ResponseType.Error });
                if (isPresent(baseResponseOptions)) {
                    responseOptions = baseResponseOptions.merge(responseOptions);
                }
                responseObserver.error(new Response(responseOptions));
            };
            script.addEventListener('load', onLoad);
            script.addEventListener('error', onError);
            _dom.send(script);
            return () => {
                this.readyState = ReadyState.Cancelled;
                script.removeEventListener('load', onLoad);
                script.removeEventListener('error', onError);
                if (isPresent(script)) {
                    this._dom.cleanup(script);
                }
            };
        });
    }
    finished(data) {
        // Don't leak connections
        this._finished = true;
        this._dom.removeConnection(this._id);
        if (this.readyState === ReadyState.Cancelled)
            return;
        this._responseData = data;
    }
}
/**
 * A {@link ConnectionBackend} that uses the JSONP strategy of making requests.
 *
 * @experimental
 */
export class JSONPBackend extends ConnectionBackend {
}
export class JSONPBackend_ extends JSONPBackend {
    constructor(_browserJSONP, _baseResponseOptions) {
        super();
        this._browserJSONP = _browserJSONP;
        this._baseResponseOptions = _baseResponseOptions;
    }
    createConnection(request) {
        return new JSONPConnection_(request, this._browserJSONP, this._baseResponseOptions);
    }
}
/** @nocollapse */
JSONPBackend_.decorators = [
    { type: Injectable },
];
/** @nocollapse */
JSONPBackend_.ctorParameters = [
    { type: BrowserJsonp, },
    { type: ResponseOptions, },
];
//# sourceMappingURL=jsonp_backend.js.map