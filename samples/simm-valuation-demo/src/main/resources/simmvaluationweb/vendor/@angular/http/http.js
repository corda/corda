/**
 * @license
 * Copyright Google Inc. All Rights Reserved.
 *
 * Use of this source code is governed by an MIT-style license that can be
 * found in the LICENSE file at https://angular.io/license
 */
"use strict";
var browser_jsonp_1 = require('./src/backends/browser_jsonp');
var browser_xhr_1 = require('./src/backends/browser_xhr');
var jsonp_backend_1 = require('./src/backends/jsonp_backend');
var xhr_backend_1 = require('./src/backends/xhr_backend');
var base_request_options_1 = require('./src/base_request_options');
var base_response_options_1 = require('./src/base_response_options');
var http_1 = require('./src/http');
var interfaces_1 = require('./src/interfaces');
var browser_xhr_2 = require('./src/backends/browser_xhr');
exports.BrowserXhr = browser_xhr_2.BrowserXhr;
var jsonp_backend_2 = require('./src/backends/jsonp_backend');
exports.JSONPBackend = jsonp_backend_2.JSONPBackend;
exports.JSONPConnection = jsonp_backend_2.JSONPConnection;
var xhr_backend_2 = require('./src/backends/xhr_backend');
exports.CookieXSRFStrategy = xhr_backend_2.CookieXSRFStrategy;
exports.XHRBackend = xhr_backend_2.XHRBackend;
exports.XHRConnection = xhr_backend_2.XHRConnection;
var base_request_options_2 = require('./src/base_request_options');
exports.BaseRequestOptions = base_request_options_2.BaseRequestOptions;
exports.RequestOptions = base_request_options_2.RequestOptions;
var base_response_options_2 = require('./src/base_response_options');
exports.BaseResponseOptions = base_response_options_2.BaseResponseOptions;
exports.ResponseOptions = base_response_options_2.ResponseOptions;
var enums_1 = require('./src/enums');
exports.ReadyState = enums_1.ReadyState;
exports.RequestMethod = enums_1.RequestMethod;
exports.ResponseType = enums_1.ResponseType;
var headers_1 = require('./src/headers');
exports.Headers = headers_1.Headers;
var http_2 = require('./src/http');
exports.Http = http_2.Http;
exports.Jsonp = http_2.Jsonp;
var interfaces_2 = require('./src/interfaces');
exports.Connection = interfaces_2.Connection;
exports.ConnectionBackend = interfaces_2.ConnectionBackend;
exports.XSRFStrategy = interfaces_2.XSRFStrategy;
var static_request_1 = require('./src/static_request');
exports.Request = static_request_1.Request;
var static_response_1 = require('./src/static_response');
exports.Response = static_response_1.Response;
var url_search_params_1 = require('./src/url_search_params');
exports.QueryEncoder = url_search_params_1.QueryEncoder;
exports.URLSearchParams = url_search_params_1.URLSearchParams;
/**
 * Provides a basic set of injectables to use the {@link Http} service in any application.
 *
 * The `HTTP_PROVIDERS` should be included either in a component's injector,
 * or in the root injector when bootstrapping an application.
 *
 * ### Example ([live demo](http://plnkr.co/edit/snj7Nv?p=preview))
 *
 * ```
 * import {Component} from '@angular/core';
 * import {bootstrap} from '@angular/platform-browser/browser';
 * import {NgFor} from '@angular/common';
 * import {HTTP_PROVIDERS, Http} from '@angular/http';
 *
 * @Component({
 *   selector: 'app',
 *   providers: [HTTP_PROVIDERS],
 *   template: `
 *     <div>
 *       <h1>People</h1>
 *       <ul>
 *         <li *ngFor="let person of people">
 *           {{person.name}}
 *         </li>
 *       </ul>
 *     </div>
 *   `,
 *   directives: [NgFor]
 * })
 * export class App {
 *   people: Object[];
 *   constructor(http:Http) {
 *     http.get('people.json').subscribe(res => {
 *       this.people = res.json();
 *     });
 *   }
 *   active:boolean = false;
 *   toggleActiveState() {
 *     this.active = !this.active;
 *   }
 * }
 *
 * bootstrap(App)
 *   .catch(err => console.error(err));
 * ```
 *
 * The primary public API included in `HTTP_PROVIDERS` is the {@link Http} class.
 * However, other providers required by `Http` are included,
 * which may be beneficial to override in certain cases.
 *
 * The providers included in `HTTP_PROVIDERS` include:
 *  * {@link Http}
 *  * {@link XHRBackend}
 *  * {@link XSRFStrategy} - Bound to {@link CookieXSRFStrategy} class (see below)
 *  * `BrowserXHR` - Private factory to create `XMLHttpRequest` instances
 *  * {@link RequestOptions} - Bound to {@link BaseRequestOptions} class
 *  * {@link ResponseOptions} - Bound to {@link BaseResponseOptions} class
 *
 * There may be cases where it makes sense to extend the base request options,
 * such as to add a search string to be appended to all URLs.
 * To accomplish this, a new provider for {@link RequestOptions} should
 * be added in the same injector as `HTTP_PROVIDERS`.
 *
 * ### Example ([live demo](http://plnkr.co/edit/aCMEXi?p=preview))
 *
 * ```
 * import {provide} from '@angular/core';
 * import {bootstrap} from '@angular/platform-browser/browser';
 * import {HTTP_PROVIDERS, BaseRequestOptions, RequestOptions} from '@angular/http';
 *
 * class MyOptions extends BaseRequestOptions {
 *   search: string = 'coreTeam=true';
 * }
 *
 * bootstrap(App, [HTTP_PROVIDERS, {provide: RequestOptions, useClass: MyOptions}])
 *   .catch(err => console.error(err));
 * ```
 *
 * Likewise, to use a mock backend for unit tests, the {@link XHRBackend}
 * provider should be bound to {@link MockBackend}.
 *
 * ### Example ([live demo](http://plnkr.co/edit/7LWALD?p=preview))
 *
 * ```
 * import {provide} from '@angular/core';
 * import {bootstrap} from '@angular/platform-browser/browser';
 * import {HTTP_PROVIDERS, Http, Response, XHRBackend} from '@angular/http';
 * import {MockBackend} from '@angular/http/testing';
 *
 * var people = [{name: 'Jeff'}, {name: 'Tobias'}];
 *
 * var injector = Injector.resolveAndCreate([
 *   HTTP_PROVIDERS,
 *   MockBackend,
 *   {provide: XHRBackend, useExisting: MockBackend}
 * ]);
 * var http = injector.get(Http);
 * var backend = injector.get(MockBackend);
 *
 * // Listen for any new requests
 * backend.connections.observer({
 *   next: connection => {
 *     var response = new Response({body: people});
 *     setTimeout(() => {
 *       // Send a response to the request
 *       connection.mockRespond(response);
 *     });
 *   }
 * });
 *
 * http.get('people.json').observer({
 *   next: res => {
 *     // Response came from mock backend
 *     console.log('first person', res.json()[0].name);
 *   }
 * });
 * ```
 *
 * `XSRFStrategy` allows customizing how the application protects itself against Cross Site Request
 * Forgery (XSRF) attacks. By default, Angular will look for a cookie called `'XSRF-TOKEN'`, and set
 * an HTTP request header called `'X-XSRF-TOKEN'` with the value of the cookie on each request,
 * allowing the server side to validate that the request comes from its own front end.
 *
 * Applications can override the names used by configuring a different `XSRFStrategy` instance. Most
 * commonly, applications will configure a `CookieXSRFStrategy` with different cookie or header
 * names, but if needed, they can supply a completely custom implementation.
 *
 * See the security documentation for more information.
 *
 * ### Example
 *
 * ```
 * import {provide} from '@angular/core';
 * import {bootstrap} from '@angular/platform-browser/browser';
 * import {HTTP_PROVIDERS, XSRFStrategy, CookieXSRFStrategy} from '@angular/http';
 *
 * bootstrap(
 *     App,
 *     [HTTP_PROVIDERS, {provide: XSRFStrategy,
 *         useValue: new CookieXSRFStrategy('MY-XSRF-COOKIE-NAME', 'X-MY-XSRF-HEADER-NAME')}])
 *   .catch(err => console.error(err));
 * ```
 *
 * @experimental
 */
exports.HTTP_PROVIDERS = [
    // TODO(pascal): use factory type annotations once supported in DI
    // issue: https://github.com/angular/angular/issues/3183
    { provide: http_1.Http, useFactory: httpFactory, deps: [xhr_backend_1.XHRBackend, base_request_options_1.RequestOptions] },
    browser_xhr_1.BrowserXhr,
    { provide: base_request_options_1.RequestOptions, useClass: base_request_options_1.BaseRequestOptions },
    { provide: base_response_options_1.ResponseOptions, useClass: base_response_options_1.BaseResponseOptions },
    xhr_backend_1.XHRBackend,
    { provide: interfaces_1.XSRFStrategy, useValue: new xhr_backend_1.CookieXSRFStrategy() },
];
/**
 * @experimental
 */
function httpFactory(xhrBackend, requestOptions) {
    return new http_1.Http(xhrBackend, requestOptions);
}
exports.httpFactory = httpFactory;
/**
 * See {@link HTTP_PROVIDERS} instead.
 *
 * @deprecated
 */
exports.HTTP_BINDINGS = exports.HTTP_PROVIDERS;
/**
 * Provides a basic set of providers to use the {@link Jsonp} service in any application.
 *
 * The `JSONP_PROVIDERS` should be included either in a component's injector,
 * or in the root injector when bootstrapping an application.
 *
 * ### Example ([live demo](http://plnkr.co/edit/vmeN4F?p=preview))
 *
 * ```
 * import {Component} from '@angular/core';
 * import {NgFor} from '@angular/common';
 * import {JSONP_PROVIDERS, Jsonp} from '@angular/http';
 *
 * @Component({
 *   selector: 'app',
 *   providers: [JSONP_PROVIDERS],
 *   template: `
 *     <div>
 *       <h1>People</h1>
 *       <ul>
 *         <li *ngFor="let person of people">
 *           {{person.name}}
 *         </li>
 *       </ul>
 *     </div>
 *   `,
 *   directives: [NgFor]
 * })
 * export class App {
 *   people: Array<Object>;
 *   constructor(jsonp:Jsonp) {
 *     jsonp.request('people.json').subscribe(res => {
 *       this.people = res.json();
 *     })
 *   }
 * }
 * ```
 *
 * The primary public API included in `JSONP_PROVIDERS` is the {@link Jsonp} class.
 * However, other providers required by `Jsonp` are included,
 * which may be beneficial to override in certain cases.
 *
 * The providers included in `JSONP_PROVIDERS` include:
 *  * {@link Jsonp}
 *  * {@link JSONPBackend}
 *  * `BrowserJsonp` - Private factory
 *  * {@link RequestOptions} - Bound to {@link BaseRequestOptions} class
 *  * {@link ResponseOptions} - Bound to {@link BaseResponseOptions} class
 *
 * There may be cases where it makes sense to extend the base request options,
 * such as to add a search string to be appended to all URLs.
 * To accomplish this, a new provider for {@link RequestOptions} should
 * be added in the same injector as `JSONP_PROVIDERS`.
 *
 * ### Example ([live demo](http://plnkr.co/edit/TFug7x?p=preview))
 *
 * ```
 * import {provide} from '@angular/core';
 * import {bootstrap} from '@angular/platform-browser/browser';
 * import {JSONP_PROVIDERS, BaseRequestOptions, RequestOptions} from '@angular/http';
 *
 * class MyOptions extends BaseRequestOptions {
 *   search: string = 'coreTeam=true';
 * }
 *
 * bootstrap(App, [JSONP_PROVIDERS, {provide: RequestOptions, useClass: MyOptions}])
 *   .catch(err => console.error(err));
 * ```
 *
 * Likewise, to use a mock backend for unit tests, the {@link JSONPBackend}
 * provider should be bound to {@link MockBackend}.
 *
 * ### Example ([live demo](http://plnkr.co/edit/HDqZWL?p=preview))
 *
 * ```
 * import {provide, Injector} from '@angular/core';
 * import {JSONP_PROVIDERS, Jsonp, Response, JSONPBackend} from '@angular/http';
 * import {MockBackend} from '@angular/http/testing';
 *
 * var people = [{name: 'Jeff'}, {name: 'Tobias'}];
 * var injector = Injector.resolveAndCreate([
 *   JSONP_PROVIDERS,
 *   MockBackend,
 *   {provide: JSONPBackend, useExisting: MockBackend}
 * ]);
 * var jsonp = injector.get(Jsonp);
 * var backend = injector.get(MockBackend);
 *
 * // Listen for any new requests
 * backend.connections.observer({
 *   next: connection => {
 *     var response = new Response({body: people});
 *     setTimeout(() => {
 *       // Send a response to the request
 *       connection.mockRespond(response);
 *     });
 *   }
 * });

 * jsonp.get('people.json').observer({
 *   next: res => {
 *     // Response came from mock backend
 *     console.log('first person', res.json()[0].name);
 *   }
 * });
 * ```
 *
 * @experimental
 */
exports.JSONP_PROVIDERS = [
    // TODO(pascal): use factory type annotations once supported in DI
    // issue: https://github.com/angular/angular/issues/3183
    { provide: http_1.Jsonp, useFactory: jsonpFactory, deps: [jsonp_backend_1.JSONPBackend, base_request_options_1.RequestOptions] },
    browser_jsonp_1.BrowserJsonp,
    { provide: base_request_options_1.RequestOptions, useClass: base_request_options_1.BaseRequestOptions },
    { provide: base_response_options_1.ResponseOptions, useClass: base_response_options_1.BaseResponseOptions },
    { provide: jsonp_backend_1.JSONPBackend, useClass: jsonp_backend_1.JSONPBackend_ },
];
function jsonpFactory(jsonpBackend, requestOptions) {
    return new http_1.Jsonp(jsonpBackend, requestOptions);
}
/**
 * See {@link JSONP_PROVIDERS} instead.
 *
 * @deprecated
 */
exports.JSON_BINDINGS = exports.JSONP_PROVIDERS;
//# sourceMappingURL=http.js.map