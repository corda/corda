/**
 * @license Angular 2.0.0-rc.4
 * (c) 2010-2016 Google, Inc. https://angular.io/
 * License: MIT
 */
var __extends = (this && this.__extends) || function (d, b) {
    for (var p in b) if (b.hasOwnProperty(p)) d[p] = b[p];
    function __() { this.constructor = d; }
    d.prototype = b === null ? Object.create(b) : (__.prototype = b.prototype, new __());
};
(function (global, factory) {
    typeof exports === 'object' && typeof module !== 'undefined' ? factory(exports, require('@angular/core'), require('rxjs/Observable'), require('@angular/platform-browser')) :
        typeof define === 'function' && define.amd ? define(['exports', '@angular/core', 'rxjs/Observable', '@angular/platform-browser'], factory) :
            (factory((global.ng = global.ng || {}, global.ng.http = global.ng.http || {}), global.ng.core, global.Rx, global.ng.platformBrowser));
}(this, function (exports, _angular_core, rxjs_Observable, _angular_platformBrowser) {
    'use strict';
    /**
     * @license
     * Copyright Google Inc. All Rights Reserved.
     *
     * Use of this source code is governed by an MIT-style license that can be
     * found in the LICENSE file at https://angular.io/license
     */
    var globalScope;
    if (typeof window === 'undefined') {
        if (typeof WorkerGlobalScope !== 'undefined' && self instanceof WorkerGlobalScope) {
            // TODO: Replace any with WorkerGlobalScope from lib.webworker.d.ts #3492
            globalScope = self;
        }
        else {
            globalScope = global;
        }
    }
    else {
        globalScope = window;
    }
    // Need to declare a new variable for global here since TypeScript
    // exports the original value of the symbol.
    var global$1 = globalScope;
    // TODO: remove calls to assert in production environment
    // Note: Can't just export this and import in in other files
    // as `assert` is a reserved keyword in Dart
    global$1.assert = function assert(condition) {
        // TODO: to be fixed properly via #2830, noop for now
    };
    function isPresent(obj) {
        return obj !== undefined && obj !== null;
    }
    function isBlank(obj) {
        return obj === undefined || obj === null;
    }
    function isString(obj) {
        return typeof obj === 'string';
    }
    function isArray(obj) {
        return Array.isArray(obj);
    }
    var StringWrapper = (function () {
        function StringWrapper() {
        }
        StringWrapper.fromCharCode = function (code) { return String.fromCharCode(code); };
        StringWrapper.charCodeAt = function (s, index) { return s.charCodeAt(index); };
        StringWrapper.split = function (s, regExp) { return s.split(regExp); };
        StringWrapper.equals = function (s, s2) { return s === s2; };
        StringWrapper.stripLeft = function (s, charVal) {
            if (s && s.length) {
                var pos = 0;
                for (var i = 0; i < s.length; i++) {
                    if (s[i] != charVal)
                        break;
                    pos++;
                }
                s = s.substring(pos);
            }
            return s;
        };
        StringWrapper.stripRight = function (s, charVal) {
            if (s && s.length) {
                var pos = s.length;
                for (var i = s.length - 1; i >= 0; i--) {
                    if (s[i] != charVal)
                        break;
                    pos--;
                }
                s = s.substring(0, pos);
            }
            return s;
        };
        StringWrapper.replace = function (s, from, replace) {
            return s.replace(from, replace);
        };
        StringWrapper.replaceAll = function (s, from, replace) {
            return s.replace(from, replace);
        };
        StringWrapper.slice = function (s, from, to) {
            if (from === void 0) { from = 0; }
            if (to === void 0) { to = null; }
            return s.slice(from, to === null ? undefined : to);
        };
        StringWrapper.replaceAllMapped = function (s, from, cb) {
            return s.replace(from, function () {
                var matches = [];
                for (var _i = 0; _i < arguments.length; _i++) {
                    matches[_i - 0] = arguments[_i];
                }
                // Remove offset & string from the result array
                matches.splice(-2, 2);
                // The callback receives match, p1, ..., pn
                return cb(matches);
            });
        };
        StringWrapper.contains = function (s, substr) { return s.indexOf(substr) != -1; };
        StringWrapper.compare = function (a, b) {
            if (a < b) {
                return -1;
            }
            else if (a > b) {
                return 1;
            }
            else {
                return 0;
            }
        };
        return StringWrapper;
    }());
    function isJsObject(o) {
        return o !== null && (typeof o === 'function' || typeof o === 'object');
    }
    // Can't be all uppercase as our transpiler would think it is a special directive...
    var Json = (function () {
        function Json() {
        }
        Json.parse = function (s) { return global$1.JSON.parse(s); };
        Json.stringify = function (data) {
            // Dart doesn't take 3 arguments
            return global$1.JSON.stringify(data, null, 2);
        };
        return Json;
    }());
    var _symbolIterator = null;
    function getSymbolIterator() {
        if (isBlank(_symbolIterator)) {
            if (isPresent(globalScope.Symbol) && isPresent(Symbol.iterator)) {
                _symbolIterator = Symbol.iterator;
            }
            else {
                // es6-shim specific logic
                var keys = Object.getOwnPropertyNames(Map.prototype);
                for (var i = 0; i < keys.length; ++i) {
                    var key = keys[i];
                    if (key !== 'entries' && key !== 'size' &&
                        Map.prototype[key] === Map.prototype['entries']) {
                        _symbolIterator = key;
                    }
                }
            }
        }
        return _symbolIterator;
    }
    var _nextRequestId = 0;
    var JSONP_HOME = '__ng_jsonp__';
    var _jsonpConnections = null;
    function _getJsonpConnections() {
        if (_jsonpConnections === null) {
            _jsonpConnections = global$1[JSONP_HOME] = {};
        }
        return _jsonpConnections;
    }
    var BrowserJsonp = (function () {
        function BrowserJsonp() {
        }
        // Construct a <script> element with the specified URL
        BrowserJsonp.prototype.build = function (url) {
            var node = document.createElement('script');
            node.src = url;
            return node;
        };
        BrowserJsonp.prototype.nextRequestID = function () { return "__req" + _nextRequestId++; };
        BrowserJsonp.prototype.requestCallback = function (id) { return JSONP_HOME + "." + id + ".finished"; };
        BrowserJsonp.prototype.exposeConnection = function (id, connection) {
            var connections = _getJsonpConnections();
            connections[id] = connection;
        };
        BrowserJsonp.prototype.removeConnection = function (id) {
            var connections = _getJsonpConnections();
            connections[id] = null;
        };
        // Attach the <script> element to the DOM
        BrowserJsonp.prototype.send = function (node) { document.body.appendChild((node)); };
        // Remove <script> element from the DOM
        BrowserJsonp.prototype.cleanup = function (node) {
            if (node.parentNode) {
                node.parentNode.removeChild((node));
            }
        };
        return BrowserJsonp;
    }());
    /** @nocollapse */
    BrowserJsonp.decorators = [
        { type: _angular_core.Injectable },
    ];
    var BrowserXhr = (function () {
        function BrowserXhr() {
        }
        BrowserXhr.prototype.build = function () { return (new XMLHttpRequest()); };
        return BrowserXhr;
    }());
    /** @nocollapse */
    BrowserXhr.decorators = [
        { type: _angular_core.Injectable },
    ];
    /** @nocollapse */
    BrowserXhr.ctorParameters = [];
    /**
     * @license
     * Copyright Google Inc. All Rights Reserved.
     *
     * Use of this source code is governed by an MIT-style license that can be
     * found in the LICENSE file at https://angular.io/license
     */
    /**
     * Supported http methods.
     * @experimental
     */
    exports.RequestMethod;
    (function (RequestMethod) {
        RequestMethod[RequestMethod["Get"] = 0] = "Get";
        RequestMethod[RequestMethod["Post"] = 1] = "Post";
        RequestMethod[RequestMethod["Put"] = 2] = "Put";
        RequestMethod[RequestMethod["Delete"] = 3] = "Delete";
        RequestMethod[RequestMethod["Options"] = 4] = "Options";
        RequestMethod[RequestMethod["Head"] = 5] = "Head";
        RequestMethod[RequestMethod["Patch"] = 6] = "Patch";
    })(exports.RequestMethod || (exports.RequestMethod = {}));
    /**
     * All possible states in which a connection can be, based on
     * [States](http://www.w3.org/TR/XMLHttpRequest/#states) from the `XMLHttpRequest` spec, but with an
     * additional "CANCELLED" state.
     * @experimental
     */
    exports.ReadyState;
    (function (ReadyState) {
        ReadyState[ReadyState["Unsent"] = 0] = "Unsent";
        ReadyState[ReadyState["Open"] = 1] = "Open";
        ReadyState[ReadyState["HeadersReceived"] = 2] = "HeadersReceived";
        ReadyState[ReadyState["Loading"] = 3] = "Loading";
        ReadyState[ReadyState["Done"] = 4] = "Done";
        ReadyState[ReadyState["Cancelled"] = 5] = "Cancelled";
    })(exports.ReadyState || (exports.ReadyState = {}));
    /**
     * Acceptable response types to be associated with a {@link Response}, based on
     * [ResponseType](https://fetch.spec.whatwg.org/#responsetype) from the Fetch spec.
     * @experimental
     */
    exports.ResponseType;
    (function (ResponseType) {
        ResponseType[ResponseType["Basic"] = 0] = "Basic";
        ResponseType[ResponseType["Cors"] = 1] = "Cors";
        ResponseType[ResponseType["Default"] = 2] = "Default";
        ResponseType[ResponseType["Error"] = 3] = "Error";
        ResponseType[ResponseType["Opaque"] = 4] = "Opaque";
    })(exports.ResponseType || (exports.ResponseType = {}));
    /**
     * Supported content type to be automatically associated with a {@link Request}.
     * @experimental
     */
    var ContentType;
    (function (ContentType) {
        ContentType[ContentType["NONE"] = 0] = "NONE";
        ContentType[ContentType["JSON"] = 1] = "JSON";
        ContentType[ContentType["FORM"] = 2] = "FORM";
        ContentType[ContentType["FORM_DATA"] = 3] = "FORM_DATA";
        ContentType[ContentType["TEXT"] = 4] = "TEXT";
        ContentType[ContentType["BLOB"] = 5] = "BLOB";
        ContentType[ContentType["ARRAY_BUFFER"] = 6] = "ARRAY_BUFFER";
    })(ContentType || (ContentType = {}));
    var Map$1 = global$1.Map;
    var Set = global$1.Set;
    // Safari and Internet Explorer do not support the iterable parameter to the
    // Map constructor.  We work around that by manually adding the items.
    var createMapFromPairs = (function () {
        try {
            if (new Map$1([[1, 2]]).size === 1) {
                return function createMapFromPairs(pairs) { return new Map$1(pairs); };
            }
        }
        catch (e) {
        }
        return function createMapAndPopulateFromPairs(pairs) {
            var map = new Map$1();
            for (var i = 0; i < pairs.length; i++) {
                var pair = pairs[i];
                map.set(pair[0], pair[1]);
            }
            return map;
        };
    })();
    var createMapFromMap = (function () {
        try {
            if (new Map$1(new Map$1())) {
                return function createMapFromMap(m) { return new Map$1(m); };
            }
        }
        catch (e) {
        }
        return function createMapAndPopulateFromMap(m) {
            var map = new Map$1();
            m.forEach(function (v, k) { map.set(k, v); });
            return map;
        };
    })();
    var _clearValues = (function () {
        if ((new Map$1()).keys().next) {
            return function _clearValues(m) {
                var keyIterator = m.keys();
                var k;
                while (!((k = keyIterator.next()).done)) {
                    m.set(k.value, null);
                }
            };
        }
        else {
            return function _clearValuesWithForeEach(m) {
                m.forEach(function (v, k) { m.set(k, null); });
            };
        }
    })();
    // Safari doesn't implement MapIterator.next(), which is used is Traceur's polyfill of Array.from
    // TODO(mlaval): remove the work around once we have a working polyfill of Array.from
    var _arrayFromMap = (function () {
        try {
            if ((new Map$1()).values().next) {
                return function createArrayFromMap(m, getValues) {
                    return getValues ? Array.from(m.values()) : Array.from(m.keys());
                };
            }
        }
        catch (e) {
        }
        return function createArrayFromMapWithForeach(m, getValues) {
            var res = ListWrapper.createFixedSize(m.size), i = 0;
            m.forEach(function (v, k) {
                res[i] = getValues ? v : k;
                i++;
            });
            return res;
        };
    })();
    var MapWrapper = (function () {
        function MapWrapper() {
        }
        MapWrapper.clone = function (m) { return createMapFromMap(m); };
        MapWrapper.createFromStringMap = function (stringMap) {
            var result = new Map$1();
            for (var prop in stringMap) {
                result.set(prop, stringMap[prop]);
            }
            return result;
        };
        MapWrapper.toStringMap = function (m) {
            var r = {};
            m.forEach(function (v, k) { return r[k] = v; });
            return r;
        };
        MapWrapper.createFromPairs = function (pairs) { return createMapFromPairs(pairs); };
        MapWrapper.clearValues = function (m) { _clearValues(m); };
        MapWrapper.iterable = function (m) { return m; };
        MapWrapper.keys = function (m) { return _arrayFromMap(m, false); };
        MapWrapper.values = function (m) { return _arrayFromMap(m, true); };
        return MapWrapper;
    }());
    /**
     * Wraps Javascript Objects
     */
    var StringMapWrapper = (function () {
        function StringMapWrapper() {
        }
        StringMapWrapper.create = function () {
            // Note: We are not using Object.create(null) here due to
            // performance!
            // http://jsperf.com/ng2-object-create-null
            return {};
        };
        StringMapWrapper.contains = function (map, key) {
            return map.hasOwnProperty(key);
        };
        StringMapWrapper.get = function (map, key) {
            return map.hasOwnProperty(key) ? map[key] : undefined;
        };
        StringMapWrapper.set = function (map, key, value) { map[key] = value; };
        StringMapWrapper.keys = function (map) { return Object.keys(map); };
        StringMapWrapper.values = function (map) {
            return Object.keys(map).reduce(function (r, a) {
                r.push(map[a]);
                return r;
            }, []);
        };
        StringMapWrapper.isEmpty = function (map) {
            for (var prop in map) {
                return false;
            }
            return true;
        };
        StringMapWrapper.delete = function (map, key) { delete map[key]; };
        StringMapWrapper.forEach = function (map, callback) {
            for (var prop in map) {
                if (map.hasOwnProperty(prop)) {
                    callback(map[prop], prop);
                }
            }
        };
        StringMapWrapper.merge = function (m1, m2) {
            var m = {};
            for (var attr in m1) {
                if (m1.hasOwnProperty(attr)) {
                    m[attr] = m1[attr];
                }
            }
            for (var attr in m2) {
                if (m2.hasOwnProperty(attr)) {
                    m[attr] = m2[attr];
                }
            }
            return m;
        };
        StringMapWrapper.equals = function (m1, m2) {
            var k1 = Object.keys(m1);
            var k2 = Object.keys(m2);
            if (k1.length != k2.length) {
                return false;
            }
            var key;
            for (var i = 0; i < k1.length; i++) {
                key = k1[i];
                if (m1[key] !== m2[key]) {
                    return false;
                }
            }
            return true;
        };
        return StringMapWrapper;
    }());
    var ListWrapper = (function () {
        function ListWrapper() {
        }
        // JS has no way to express a statically fixed size list, but dart does so we
        // keep both methods.
        ListWrapper.createFixedSize = function (size) { return new Array(size); };
        ListWrapper.createGrowableSize = function (size) { return new Array(size); };
        ListWrapper.clone = function (array) { return array.slice(0); };
        ListWrapper.forEachWithIndex = function (array, fn) {
            for (var i = 0; i < array.length; i++) {
                fn(array[i], i);
            }
        };
        ListWrapper.first = function (array) {
            if (!array)
                return null;
            return array[0];
        };
        ListWrapper.last = function (array) {
            if (!array || array.length == 0)
                return null;
            return array[array.length - 1];
        };
        ListWrapper.indexOf = function (array, value, startIndex) {
            if (startIndex === void 0) { startIndex = 0; }
            return array.indexOf(value, startIndex);
        };
        ListWrapper.contains = function (list, el) { return list.indexOf(el) !== -1; };
        ListWrapper.reversed = function (array) {
            var a = ListWrapper.clone(array);
            return a.reverse();
        };
        ListWrapper.concat = function (a, b) { return a.concat(b); };
        ListWrapper.insert = function (list, index, value) { list.splice(index, 0, value); };
        ListWrapper.removeAt = function (list, index) {
            var res = list[index];
            list.splice(index, 1);
            return res;
        };
        ListWrapper.removeAll = function (list, items) {
            for (var i = 0; i < items.length; ++i) {
                var index = list.indexOf(items[i]);
                list.splice(index, 1);
            }
        };
        ListWrapper.remove = function (list, el) {
            var index = list.indexOf(el);
            if (index > -1) {
                list.splice(index, 1);
                return true;
            }
            return false;
        };
        ListWrapper.clear = function (list) { list.length = 0; };
        ListWrapper.isEmpty = function (list) { return list.length == 0; };
        ListWrapper.fill = function (list, value, start, end) {
            if (start === void 0) { start = 0; }
            if (end === void 0) { end = null; }
            list.fill(value, start, end === null ? list.length : end);
        };
        ListWrapper.equals = function (a, b) {
            if (a.length != b.length)
                return false;
            for (var i = 0; i < a.length; ++i) {
                if (a[i] !== b[i])
                    return false;
            }
            return true;
        };
        ListWrapper.slice = function (l, from, to) {
            if (from === void 0) { from = 0; }
            if (to === void 0) { to = null; }
            return l.slice(from, to === null ? undefined : to);
        };
        ListWrapper.splice = function (l, from, length) { return l.splice(from, length); };
        ListWrapper.sort = function (l, compareFn) {
            if (isPresent(compareFn)) {
                l.sort(compareFn);
            }
            else {
                l.sort();
            }
        };
        ListWrapper.toString = function (l) { return l.toString(); };
        ListWrapper.toJSON = function (l) { return JSON.stringify(l); };
        ListWrapper.maximum = function (list, predicate) {
            if (list.length == 0) {
                return null;
            }
            var solution = null;
            var maxValue = -Infinity;
            for (var index = 0; index < list.length; index++) {
                var candidate = list[index];
                if (isBlank(candidate)) {
                    continue;
                }
                var candidateValue = predicate(candidate);
                if (candidateValue > maxValue) {
                    solution = candidate;
                    maxValue = candidateValue;
                }
            }
            return solution;
        };
        ListWrapper.flatten = function (list) {
            var target = [];
            _flattenArray(list, target);
            return target;
        };
        ListWrapper.addAll = function (list, source) {
            for (var i = 0; i < source.length; i++) {
                list.push(source[i]);
            }
        };
        return ListWrapper;
    }());
    function _flattenArray(source, target) {
        if (isPresent(source)) {
            for (var i = 0; i < source.length; i++) {
                var item = source[i];
                if (isArray(item)) {
                    _flattenArray(item, target);
                }
                else {
                    target.push(item);
                }
            }
        }
        return target;
    }
    function isListLikeIterable(obj) {
        if (!isJsObject(obj))
            return false;
        return isArray(obj) ||
            (!(obj instanceof Map$1) &&
                getSymbolIterator() in obj); // JS Iterable have a Symbol.iterator prop
    }
    function iterateListLike(obj, fn) {
        if (isArray(obj)) {
            for (var i = 0; i < obj.length; i++) {
                fn(obj[i]);
            }
        }
        else {
            var iterator = obj[getSymbolIterator()]();
            var item;
            while (!((item = iterator.next()).done)) {
                fn(item.value);
            }
        }
    }
    // Safari and Internet Explorer do not support the iterable parameter to the
    // Set constructor.  We work around that by manually adding the items.
    var createSetFromList = (function () {
        var test = new Set([1, 2, 3]);
        if (test.size === 3) {
            return function createSetFromList(lst) { return new Set(lst); };
        }
        else {
            return function createSetAndPopulateFromList(lst) {
                var res = new Set(lst);
                if (res.size !== lst.length) {
                    for (var i = 0; i < lst.length; i++) {
                        res.add(lst[i]);
                    }
                }
                return res;
            };
        }
    })();
    /**
     * @stable
     */
    var BaseException = (function (_super) {
        __extends(BaseException, _super);
        function BaseException(message) {
            if (message === void 0) { message = '--'; }
            _super.call(this, message);
            this.message = message;
            this.stack = (new Error(message)).stack;
        }
        BaseException.prototype.toString = function () { return this.message; };
        return BaseException;
    }(Error));
    function makeTypeError(message) {
        return new TypeError(message);
    }
    /**
     * Polyfill for [Headers](https://developer.mozilla.org/en-US/docs/Web/API/Headers/Headers), as
     * specified in the [Fetch Spec](https://fetch.spec.whatwg.org/#headers-class).
     *
     * The only known difference between this `Headers` implementation and the spec is the
     * lack of an `entries` method.
     *
     * ### Example ([live demo](http://plnkr.co/edit/MTdwT6?p=preview))
     *
     * ```
     * import {Headers} from '@angular/http';
     *
     * var firstHeaders = new Headers();
     * firstHeaders.append('Content-Type', 'image/jpeg');
     * console.log(firstHeaders.get('Content-Type')) //'image/jpeg'
     *
     * // Create headers from Plain Old JavaScript Object
     * var secondHeaders = new Headers({
     *   'X-My-Custom-Header': 'Angular'
     * });
     * console.log(secondHeaders.get('X-My-Custom-Header')); //'Angular'
     *
     * var thirdHeaders = new Headers(secondHeaders);
     * console.log(thirdHeaders.get('X-My-Custom-Header')); //'Angular'
     * ```
     *
     * @experimental
     */
    var Headers = (function () {
        function Headers(headers) {
            var _this = this;
            if (headers instanceof Headers) {
                this._headersMap = headers._headersMap;
                return;
            }
            this._headersMap = new Map$1();
            if (isBlank(headers)) {
                return;
            }
            // headers instanceof StringMap
            StringMapWrapper.forEach(headers, function (v, k) {
                _this._headersMap.set(k, isListLikeIterable(v) ? v : [v]);
            });
        }
        /**
         * Returns a new Headers instance from the given DOMString of Response Headers
         */
        Headers.fromResponseHeaderString = function (headersString) {
            return headersString.trim()
                .split('\n')
                .map(function (val) { return val.split(':'); })
                .map(function (_a) {
                var key = _a[0], parts = _a.slice(1);
                return ([key.trim(), parts.join(':').trim()]);
            })
                .reduce(function (headers, _a) {
                var key = _a[0], value = _a[1];
                return !headers.set(key, value) && headers;
            }, new Headers());
        };
        /**
         * Appends a header to existing list of header values for a given header name.
         */
        Headers.prototype.append = function (name, value) {
            var mapName = this._headersMap.get(name);
            var list = isListLikeIterable(mapName) ? mapName : [];
            list.push(value);
            this._headersMap.set(name, list);
        };
        /**
         * Deletes all header values for the given name.
         */
        Headers.prototype.delete = function (name) { this._headersMap.delete(name); };
        Headers.prototype.forEach = function (fn) {
            this._headersMap.forEach(fn);
        };
        /**
         * Returns first header that matches given name.
         */
        Headers.prototype.get = function (header) { return ListWrapper.first(this._headersMap.get(header)); };
        /**
         * Check for existence of header by given name.
         */
        Headers.prototype.has = function (header) { return this._headersMap.has(header); };
        /**
         * Provides names of set headers
         */
        Headers.prototype.keys = function () { return MapWrapper.keys(this._headersMap); };
        /**
         * Sets or overrides header value for given name.
         */
        Headers.prototype.set = function (header, value) {
            var list = [];
            if (isListLikeIterable(value)) {
                var pushValue = value.join(',');
                list.push(pushValue);
            }
            else {
                list.push(value);
            }
            this._headersMap.set(header, list);
        };
        /**
         * Returns values of all headers.
         */
        Headers.prototype.values = function () { return MapWrapper.values(this._headersMap); };
        /**
         * Returns string of all headers.
         */
        Headers.prototype.toJSON = function () {
            var serializableHeaders = {};
            this._headersMap.forEach(function (values, name) {
                var list = [];
                iterateListLike(values, function (val /** TODO #9100 */) { return list = ListWrapper.concat(list, val.split(',')); });
                serializableHeaders[name] = list;
            });
            return serializableHeaders;
        };
        /**
         * Returns list of header values for a given name.
         */
        Headers.prototype.getAll = function (header) {
            var headers = this._headersMap.get(header);
            return isListLikeIterable(headers) ? headers : [];
        };
        /**
         * This method is not implemented.
         */
        Headers.prototype.entries = function () { throw new BaseException('"entries" method is not implemented on Headers class'); };
        return Headers;
    }());
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
        ResponseOptions.prototype.merge = function (options) {
            return new ResponseOptions({
                body: isPresent(options) && isPresent(options.body) ? options.body : this.body,
                status: isPresent(options) && isPresent(options.status) ? options.status : this.status,
                headers: isPresent(options) && isPresent(options.headers) ? options.headers : this.headers,
                statusText: isPresent(options) && isPresent(options.statusText) ? options.statusText :
                    this.statusText,
                type: isPresent(options) && isPresent(options.type) ? options.type : this.type,
                url: isPresent(options) && isPresent(options.url) ? options.url : this.url,
            });
        };
        return ResponseOptions;
    }());
    var BaseResponseOptions = (function (_super) {
        __extends(BaseResponseOptions, _super);
        function BaseResponseOptions() {
            _super.call(this, { status: 200, statusText: 'Ok', type: exports.ResponseType.Default, headers: new Headers() });
        }
        return BaseResponseOptions;
    }(ResponseOptions));
    /** @nocollapse */
    BaseResponseOptions.decorators = [
        { type: _angular_core.Injectable },
    ];
    /** @nocollapse */
    BaseResponseOptions.ctorParameters = [];
    /**
     * @license
     * Copyright Google Inc. All Rights Reserved.
     *
     * Use of this source code is governed by an MIT-style license that can be
     * found in the LICENSE file at https://angular.io/license
     */
    /**
     * Abstract class from which real backends are derived.
     *
     * The primary purpose of a `ConnectionBackend` is to create new connections to fulfill a given
     * {@link Request}.
     *
     * @experimental
     */
    var ConnectionBackend = (function () {
        function ConnectionBackend() {
        }
        return ConnectionBackend;
    }());
    /**
     * Abstract class from which real connections are derived.
     *
     * @experimental
     */
    var Connection = (function () {
        function Connection() {
        }
        return Connection;
    }());
    /**
     * An XSRFStrategy configures XSRF protection (e.g. via headers) on an HTTP request.
     *
     * @experimental
     */
    var XSRFStrategy = (function () {
        function XSRFStrategy() {
        }
        return XSRFStrategy;
    }());
    function normalizeMethodName(method) {
        if (isString(method)) {
            var originalMethod = method;
            method = method
                .replace(/(\w)(\w*)/g, function (g0, g1, g2) { return g1.toUpperCase() + g2.toLowerCase(); });
            method = exports.RequestMethod[method];
            if (typeof method !== 'number')
                throw makeTypeError("Invalid request method. The method \"" + originalMethod + "\" is not supported.");
        }
        return method;
    }
    var isSuccess = function (status) { return (status >= 200 && status < 300); };
    function getResponseURL(xhr) {
        if ('responseURL' in xhr) {
            return xhr.responseURL;
        }
        if (/^X-Request-URL:/m.test(xhr.getAllResponseHeaders())) {
            return xhr.getResponseHeader('X-Request-URL');
        }
        return;
    }
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
    var Response = (function () {
        function Response(responseOptions) {
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
        Response.prototype.blob = function () { throw new BaseException('"blob()" method not implemented on Response superclass'); };
        /**
         * Attempts to return body as parsed `JSON` object, or raises an exception.
         */
        Response.prototype.json = function () {
            var jsonResponse;
            if (isJsObject(this._body)) {
                jsonResponse = this._body;
            }
            else if (isString(this._body)) {
                jsonResponse = Json.parse(this._body);
            }
            return jsonResponse;
        };
        /**
         * Returns the body as a string, presuming `toString()` can be called on the response body.
         */
        Response.prototype.text = function () { return this._body.toString(); };
        /**
         * Not yet implemented
         */
        // TODO: ArrayBuffer return type
        Response.prototype.arrayBuffer = function () {
            throw new BaseException('"arrayBuffer()" method not implemented on Response superclass');
        };
        Response.prototype.toString = function () {
            return "Response with status: " + this.status + " " + this.statusText + " for URL: " + this.url;
        };
        return Response;
    }());
    var JSONP_ERR_NO_CALLBACK = 'JSONP injected script did not invoke callback.';
    var JSONP_ERR_WRONG_METHOD = 'JSONP requests must use GET request method.';
    /**
     * Abstract base class for an in-flight JSONP request.
     *
     * @experimental
     */
    var JSONPConnection = (function () {
        function JSONPConnection() {
        }
        return JSONPConnection;
    }());
    var JSONPConnection_ = (function (_super) {
        __extends(JSONPConnection_, _super);
        function JSONPConnection_(req, _dom, baseResponseOptions) {
            var _this = this;
            _super.call(this);
            this._dom = _dom;
            this.baseResponseOptions = baseResponseOptions;
            this._finished = false;
            if (req.method !== exports.RequestMethod.Get) {
                throw makeTypeError(JSONP_ERR_WRONG_METHOD);
            }
            this.request = req;
            this.response = new rxjs_Observable.Observable(function (responseObserver) {
                _this.readyState = exports.ReadyState.Loading;
                var id = _this._id = _dom.nextRequestID();
                _dom.exposeConnection(id, _this);
                // Workaround Dart
                // url = url.replace(/=JSONP_CALLBACK(&|$)/, `generated method`);
                var callback = _dom.requestCallback(_this._id);
                var url = req.url;
                if (url.indexOf('=JSONP_CALLBACK&') > -1) {
                    url = StringWrapper.replace(url, '=JSONP_CALLBACK&', "=" + callback + "&");
                }
                else if (url.lastIndexOf('=JSONP_CALLBACK') === url.length - '=JSONP_CALLBACK'.length) {
                    url = url.substring(0, url.length - '=JSONP_CALLBACK'.length) + ("=" + callback);
                }
                var script = _this._script = _dom.build(url);
                var onLoad = function (event) {
                    if (_this.readyState === exports.ReadyState.Cancelled)
                        return;
                    _this.readyState = exports.ReadyState.Done;
                    _dom.cleanup(script);
                    if (!_this._finished) {
                        var responseOptions_1 = new ResponseOptions({ body: JSONP_ERR_NO_CALLBACK, type: exports.ResponseType.Error, url: url });
                        if (isPresent(baseResponseOptions)) {
                            responseOptions_1 = baseResponseOptions.merge(responseOptions_1);
                        }
                        responseObserver.error(new Response(responseOptions_1));
                        return;
                    }
                    var responseOptions = new ResponseOptions({ body: _this._responseData, url: url });
                    if (isPresent(_this.baseResponseOptions)) {
                        responseOptions = _this.baseResponseOptions.merge(responseOptions);
                    }
                    responseObserver.next(new Response(responseOptions));
                    responseObserver.complete();
                };
                var onError = function (error) {
                    if (_this.readyState === exports.ReadyState.Cancelled)
                        return;
                    _this.readyState = exports.ReadyState.Done;
                    _dom.cleanup(script);
                    var responseOptions = new ResponseOptions({ body: error.message, type: exports.ResponseType.Error });
                    if (isPresent(baseResponseOptions)) {
                        responseOptions = baseResponseOptions.merge(responseOptions);
                    }
                    responseObserver.error(new Response(responseOptions));
                };
                script.addEventListener('load', onLoad);
                script.addEventListener('error', onError);
                _dom.send(script);
                return function () {
                    _this.readyState = exports.ReadyState.Cancelled;
                    script.removeEventListener('load', onLoad);
                    script.removeEventListener('error', onError);
                    if (isPresent(script)) {
                        _this._dom.cleanup(script);
                    }
                };
            });
        }
        JSONPConnection_.prototype.finished = function (data) {
            // Don't leak connections
            this._finished = true;
            this._dom.removeConnection(this._id);
            if (this.readyState === exports.ReadyState.Cancelled)
                return;
            this._responseData = data;
        };
        return JSONPConnection_;
    }(JSONPConnection));
    /**
     * A {@link ConnectionBackend} that uses the JSONP strategy of making requests.
     *
     * @experimental
     */
    var JSONPBackend = (function (_super) {
        __extends(JSONPBackend, _super);
        function JSONPBackend() {
            _super.apply(this, arguments);
        }
        return JSONPBackend;
    }(ConnectionBackend));
    var JSONPBackend_ = (function (_super) {
        __extends(JSONPBackend_, _super);
        function JSONPBackend_(_browserJSONP, _baseResponseOptions) {
            _super.call(this);
            this._browserJSONP = _browserJSONP;
            this._baseResponseOptions = _baseResponseOptions;
        }
        JSONPBackend_.prototype.createConnection = function (request) {
            return new JSONPConnection_(request, this._browserJSONP, this._baseResponseOptions);
        };
        return JSONPBackend_;
    }(JSONPBackend));
    /** @nocollapse */
    JSONPBackend_.decorators = [
        { type: _angular_core.Injectable },
    ];
    /** @nocollapse */
    JSONPBackend_.ctorParameters = [
        { type: BrowserJsonp, },
        { type: ResponseOptions, },
    ];
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
            this.response = new rxjs_Observable.Observable(function (responseObserver) {
                var _xhr = browserXHR.build();
                _xhr.open(exports.RequestMethod[req.method].toUpperCase(), req.url);
                if (isPresent(req.withCredentials)) {
                    _xhr.withCredentials = req.withCredentials;
                }
                // load event handler
                var onLoad = function () {
                    // responseText is the old-school way of retrieving response (supported by IE8 & 9)
                    // response/responseType properties were introduced in XHR Level2 spec (supported by
                    // IE10)
                    var body = isPresent(_xhr.response) ? _xhr.response : _xhr.responseText;
                    // Implicitly strip a potential XSSI prefix.
                    if (isString(body))
                        body = body.replace(XSSI_PREFIX, '');
                    var headers = Headers.fromResponseHeaderString(_xhr.getAllResponseHeaders());
                    var url = getResponseURL(_xhr);
                    // normalize IE9 bug (http://bugs.jquery.com/ticket/1450)
                    var status = _xhr.status === 1223 ? 204 : _xhr.status;
                    // fix status code when it is 0 (0 status is undocumented).
                    // Occurs when accessing file resources or on Android 4.1 stock browser
                    // while retrieving files from application cache.
                    if (status === 0) {
                        status = body ? 200 : 0;
                    }
                    var statusText = _xhr.statusText || 'OK';
                    var responseOptions = new ResponseOptions({ body: body, status: status, headers: headers, statusText: statusText, url: url });
                    if (isPresent(baseResponseOptions)) {
                        responseOptions = baseResponseOptions.merge(responseOptions);
                    }
                    var response = new Response(responseOptions);
                    response.ok = isSuccess(status);
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
                    var responseOptions = new ResponseOptions({
                        body: err,
                        type: exports.ResponseType.Error,
                        status: _xhr.status,
                        statusText: _xhr.statusText,
                    });
                    if (isPresent(baseResponseOptions)) {
                        responseOptions = baseResponseOptions.merge(responseOptions);
                    }
                    responseObserver.error(new Response(responseOptions));
                };
                _this.setDetectedContentType(req, _xhr);
                if (isPresent(req.headers)) {
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
            if (isPresent(req.headers) && isPresent(req.headers.get('Content-Type'))) {
                return;
            }
            // Set the detected content type
            switch (req.contentType) {
                case ContentType.NONE:
                    break;
                case ContentType.JSON:
                    _xhr.setRequestHeader('Content-Type', 'application/json');
                    break;
                case ContentType.FORM:
                    _xhr.setRequestHeader('Content-Type', 'application/x-www-form-urlencoded;charset=UTF-8');
                    break;
                case ContentType.TEXT:
                    _xhr.setRequestHeader('Content-Type', 'text/plain');
                    break;
                case ContentType.BLOB:
                    var blob = req.blob();
                    if (blob.type) {
                        _xhr.setRequestHeader('Content-Type', blob.type);
                    }
                    break;
            }
        };
        return XHRConnection;
    }());
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
            var xsrfToken = _angular_platformBrowser.__platform_browser_private__.getDOM().getCookie(this._cookieName);
            if (xsrfToken && !req.headers.has(this._headerName)) {
                req.headers.set(this._headerName, xsrfToken);
            }
        };
        return CookieXSRFStrategy;
    }());
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
        return XHRBackend;
    }());
    /** @nocollapse */
    XHRBackend.decorators = [
        { type: _angular_core.Injectable },
    ];
    /** @nocollapse */
    XHRBackend.ctorParameters = [
        { type: BrowserXhr, },
        { type: ResponseOptions, },
        { type: XSRFStrategy, },
    ];
    function paramParser(rawParams) {
        if (rawParams === void 0) { rawParams = ''; }
        var map = new Map$1();
        if (rawParams.length > 0) {
            var params = rawParams.split('&');
            params.forEach(function (param) {
                var split = param.split('=', 2);
                var key = split[0];
                var val = split[1];
                var list = isPresent(map.get(key)) ? map.get(key) : [];
                list.push(val);
                map.set(key, list);
            });
        }
        return map;
    }
    /**
     * @experimental
     **/
    var QueryEncoder = (function () {
        function QueryEncoder() {
        }
        QueryEncoder.prototype.encodeKey = function (k) { return standardEncoding(k); };
        QueryEncoder.prototype.encodeValue = function (v) { return standardEncoding(v); };
        return QueryEncoder;
    }());
    function standardEncoding(v) {
        return encodeURIComponent(v)
            .replace(/%40/gi, '@')
            .replace(/%3A/gi, ':')
            .replace(/%24/gi, '$')
            .replace(/%2C/gi, ',')
            .replace(/%3B/gi, ';')
            .replace(/%2B/gi, '+')
            .replace(/%3D/gi, ';')
            .replace(/%3F/gi, '?')
            .replace(/%2F/gi, '/');
    }
    /**
     * Map-like representation of url search parameters, based on
     * [URLSearchParams](https://url.spec.whatwg.org/#urlsearchparams) in the url living standard,
     * with several extensions for merging URLSearchParams objects:
     *   - setAll()
     *   - appendAll()
     *   - replaceAll()
     *
     * This class accepts an optional second parameter of ${@link QueryEncoder},
     * which is used to serialize parameters before making a request. By default,
     * `QueryEncoder` encodes keys and values of parameters using `encodeURIComponent`,
     * and then un-encodes certain characters that are allowed to be part of the query
     * according to IETF RFC 3986: https://tools.ietf.org/html/rfc3986.
     *
     * These are the characters that are not encoded: `! $ \' ( ) * + , ; A 9 - . _ ~ ? /`
     *
     * If the set of allowed query characters is not acceptable for a particular backend,
     * `QueryEncoder` can be subclassed and provided as the 2nd argument to URLSearchParams.
     *
     * ```
     * import {URLSearchParams, QueryEncoder} from '@angular/http';
     * class MyQueryEncoder extends QueryEncoder {
     *   encodeKey(k: string): string {
     *     return myEncodingFunction(k);
     *   }
     *
     *   encodeValue(v: string): string {
     *     return myEncodingFunction(v);
     *   }
     * }
     *
     * let params = new URLSearchParams('', new MyQueryEncoder());
     * ```
     * @experimental
     */
    var URLSearchParams = (function () {
        function URLSearchParams(rawParams, queryEncoder) {
            if (rawParams === void 0) { rawParams = ''; }
            if (queryEncoder === void 0) { queryEncoder = new QueryEncoder(); }
            this.rawParams = rawParams;
            this.queryEncoder = queryEncoder;
            this.paramsMap = paramParser(rawParams);
        }
        URLSearchParams.prototype.clone = function () {
            var clone = new URLSearchParams();
            clone.appendAll(this);
            return clone;
        };
        URLSearchParams.prototype.has = function (param) { return this.paramsMap.has(param); };
        URLSearchParams.prototype.get = function (param) {
            var storedParam = this.paramsMap.get(param);
            if (isListLikeIterable(storedParam)) {
                return ListWrapper.first(storedParam);
            }
            else {
                return null;
            }
        };
        URLSearchParams.prototype.getAll = function (param) {
            var mapParam = this.paramsMap.get(param);
            return isPresent(mapParam) ? mapParam : [];
        };
        URLSearchParams.prototype.set = function (param, val) {
            var mapParam = this.paramsMap.get(param);
            var list = isPresent(mapParam) ? mapParam : [];
            ListWrapper.clear(list);
            list.push(val);
            this.paramsMap.set(param, list);
        };
        // A merge operation
        // For each name-values pair in `searchParams`, perform `set(name, values[0])`
        //
        // E.g: "a=[1,2,3], c=[8]" + "a=[4,5,6], b=[7]" = "a=[4], c=[8], b=[7]"
        //
        // TODO(@caitp): document this better
        URLSearchParams.prototype.setAll = function (searchParams) {
            var _this = this;
            searchParams.paramsMap.forEach(function (value, param) {
                var mapParam = _this.paramsMap.get(param);
                var list = isPresent(mapParam) ? mapParam : [];
                ListWrapper.clear(list);
                list.push(value[0]);
                _this.paramsMap.set(param, list);
            });
        };
        URLSearchParams.prototype.append = function (param, val) {
            var mapParam = this.paramsMap.get(param);
            var list = isPresent(mapParam) ? mapParam : [];
            list.push(val);
            this.paramsMap.set(param, list);
        };
        // A merge operation
        // For each name-values pair in `searchParams`, perform `append(name, value)`
        // for each value in `values`.
        //
        // E.g: "a=[1,2], c=[8]" + "a=[3,4], b=[7]" = "a=[1,2,3,4], c=[8], b=[7]"
        //
        // TODO(@caitp): document this better
        URLSearchParams.prototype.appendAll = function (searchParams) {
            var _this = this;
            searchParams.paramsMap.forEach(function (value, param) {
                var mapParam = _this.paramsMap.get(param);
                var list = isPresent(mapParam) ? mapParam : [];
                for (var i = 0; i < value.length; ++i) {
                    list.push(value[i]);
                }
                _this.paramsMap.set(param, list);
            });
        };
        // A merge operation
        // For each name-values pair in `searchParams`, perform `delete(name)`,
        // followed by `set(name, values)`
        //
        // E.g: "a=[1,2,3], c=[8]" + "a=[4,5,6], b=[7]" = "a=[4,5,6], c=[8], b=[7]"
        //
        // TODO(@caitp): document this better
        URLSearchParams.prototype.replaceAll = function (searchParams) {
            var _this = this;
            searchParams.paramsMap.forEach(function (value, param) {
                var mapParam = _this.paramsMap.get(param);
                var list = isPresent(mapParam) ? mapParam : [];
                ListWrapper.clear(list);
                for (var i = 0; i < value.length; ++i) {
                    list.push(value[i]);
                }
                _this.paramsMap.set(param, list);
            });
        };
        URLSearchParams.prototype.toString = function () {
            var _this = this;
            var paramsList = [];
            this.paramsMap.forEach(function (values, k) {
                values.forEach(function (v) { return paramsList.push(_this.queryEncoder.encodeKey(k) + '=' + _this.queryEncoder.encodeValue(v)); });
            });
            return paramsList.join('&');
        };
        URLSearchParams.prototype.delete = function (param) { this.paramsMap.delete(param); };
        return URLSearchParams;
    }());
    /**
     * Creates a request options object to be optionally provided when instantiating a
     * {@link Request}.
     *
     * This class is based on the `RequestInit` description in the [Fetch
     * Spec](https://fetch.spec.whatwg.org/#requestinit).
     *
     * All values are null by default. Typical defaults can be found in the {@link BaseRequestOptions}
     * class, which sub-classes `RequestOptions`.
     *
     * ### Example ([live demo](http://plnkr.co/edit/7Wvi3lfLq41aQPKlxB4O?p=preview))
     *
     * ```typescript
     * import {RequestOptions, Request, RequestMethod} from '@angular/http';
     *
     * var options = new RequestOptions({
     *   method: RequestMethod.Post,
     *   url: 'https://google.com'
     * });
     * var req = new Request(options);
     * console.log('req.method:', RequestMethod[req.method]); // Post
     * console.log('options.url:', options.url); // https://google.com
     * ```
     *
     * @experimental
     */
    var RequestOptions = (function () {
        function RequestOptions(_a) {
            var _b = _a === void 0 ? {} : _a, method = _b.method, headers = _b.headers, body = _b.body, url = _b.url, search = _b.search, withCredentials = _b.withCredentials;
            this.method = isPresent(method) ? normalizeMethodName(method) : null;
            this.headers = isPresent(headers) ? headers : null;
            this.body = isPresent(body) ? body : null;
            this.url = isPresent(url) ? url : null;
            this.search = isPresent(search) ?
                (isString(search) ? new URLSearchParams((search)) : (search)) :
                null;
            this.withCredentials = isPresent(withCredentials) ? withCredentials : null;
        }
        /**
         * Creates a copy of the `RequestOptions` instance, using the optional input as values to override
         * existing values. This method will not change the values of the instance on which it is being
         * called.
         *
         * Note that `headers` and `search` will override existing values completely if present in
         * the `options` object. If these values should be merged, it should be done prior to calling
         * `merge` on the `RequestOptions` instance.
         *
         * ### Example ([live demo](http://plnkr.co/edit/6w8XA8YTkDRcPYpdB9dk?p=preview))
         *
         * ```typescript
         * import {RequestOptions, Request, RequestMethod} from '@angular/http';
         *
         * var options = new RequestOptions({
         *   method: RequestMethod.Post
         * });
         * var req = new Request(options.merge({
         *   url: 'https://google.com'
         * }));
         * console.log('req.method:', RequestMethod[req.method]); // Post
         * console.log('options.url:', options.url); // null
         * console.log('req.url:', req.url); // https://google.com
         * ```
         */
        RequestOptions.prototype.merge = function (options) {
            return new RequestOptions({
                method: isPresent(options) && isPresent(options.method) ? options.method : this.method,
                headers: isPresent(options) && isPresent(options.headers) ? options.headers : this.headers,
                body: isPresent(options) && isPresent(options.body) ? options.body : this.body,
                url: isPresent(options) && isPresent(options.url) ? options.url : this.url,
                search: isPresent(options) && isPresent(options.search) ?
                    (isString(options.search) ? new URLSearchParams((options.search)) :
                        (options.search).clone()) :
                    this.search,
                withCredentials: isPresent(options) && isPresent(options.withCredentials) ?
                    options.withCredentials :
                    this.withCredentials
            });
        };
        return RequestOptions;
    }());
    var BaseRequestOptions = (function (_super) {
        __extends(BaseRequestOptions, _super);
        function BaseRequestOptions() {
            _super.call(this, { method: exports.RequestMethod.Get, headers: new Headers() });
        }
        return BaseRequestOptions;
    }(RequestOptions));
    /** @nocollapse */
    BaseRequestOptions.decorators = [
        { type: _angular_core.Injectable },
    ];
    /** @nocollapse */
    BaseRequestOptions.ctorParameters = [];
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
            if (isPresent(requestOptions.search)) {
                var search = requestOptions.search.toString();
                if (search.length > 0) {
                    var prefix = '?';
                    if (StringWrapper.contains(this.url, '?')) {
                        prefix = (this.url[this.url.length - 1] == '&') ? '' : '&';
                    }
                    // TODO: just delete search-query-looking string in url?
                    this.url = url + prefix + search;
                }
            }
            this._body = requestOptions.body;
            this.contentType = this.detectContentType();
            this.method = normalizeMethodName(requestOptions.method);
            // TODO(jeffbcross): implement behavior
            // Defaults to 'omit', consistent with browser
            // TODO(jeffbcross): implement behavior
            this.headers = new Headers(requestOptions.headers);
            this.withCredentials = requestOptions.withCredentials;
        }
        /**
         * Returns the request's body as string, assuming that body exists. If body is undefined, return
         * empty
         * string.
         */
        Request.prototype.text = function () { return isPresent(this._body) ? this._body.toString() : ''; };
        /**
         * Returns the request's body as JSON string, assuming that body exists. If body is undefined,
         * return
         * empty
         * string.
         */
        Request.prototype.json = function () { return isPresent(this._body) ? JSON.stringify(this._body) : ''; };
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
                return ContentType.NONE;
            }
            else if (this._body instanceof URLSearchParams) {
                return ContentType.FORM;
            }
            else if (this._body instanceof FormData) {
                return ContentType.FORM_DATA;
            }
            else if (this._body instanceof Blob) {
                return ContentType.BLOB;
            }
            else if (this._body instanceof ArrayBuffer) {
                return ContentType.ARRAY_BUFFER;
            }
            else if (this._body && typeof this._body == 'object') {
                return ContentType.JSON;
            }
            else {
                return ContentType.TEXT;
            }
        };
        /**
         * Returns the request's body according to its type. If body is undefined, return
         * null.
         */
        Request.prototype.getBody = function () {
            switch (this.contentType) {
                case ContentType.JSON:
                    return this.json();
                case ContentType.FORM:
                    return this.text();
                case ContentType.FORM_DATA:
                    return this._body;
                case ContentType.TEXT:
                    return this.text();
                case ContentType.BLOB:
                    return this.blob();
                case ContentType.ARRAY_BUFFER:
                    return this.arrayBuffer();
                default:
                    return null;
            }
        };
        return Request;
    }());
    var noop$1 = function () { };
    var w = typeof window == 'object' ? window : noop$1;
    var FormData = w['FormData'] || noop$1;
    var Blob = w['Blob'] || noop$1;
    var ArrayBuffer = w['ArrayBuffer'] || noop$1;
    function httpRequest(backend, request) {
        return backend.createConnection(request).response;
    }
    function mergeOptions(defaultOpts, providedOpts, method, url) {
        var newOptions = defaultOpts;
        if (isPresent(providedOpts)) {
            // Hack so Dart can used named parameters
            return newOptions.merge(new RequestOptions({
                method: providedOpts.method || method,
                url: providedOpts.url || url,
                search: providedOpts.search,
                headers: providedOpts.headers,
                body: providedOpts.body,
                withCredentials: providedOpts.withCredentials
            }));
        }
        if (isPresent(method)) {
            return newOptions.merge(new RequestOptions({ method: method, url: url }));
        }
        else {
            return newOptions.merge(new RequestOptions({ url: url }));
        }
    }
    var Http = (function () {
        function Http(_backend, _defaultOptions) {
            this._backend = _backend;
            this._defaultOptions = _defaultOptions;
        }
        /**
         * Performs any type of http request. First argument is required, and can either be a url or
         * a {@link Request} instance. If the first argument is a url, an optional {@link RequestOptions}
         * object can be provided as the 2nd argument. The options object will be merged with the values
         * of {@link BaseRequestOptions} before performing the request.
         */
        Http.prototype.request = function (url, options) {
            var responseObservable;
            if (isString(url)) {
                responseObservable = httpRequest(this._backend, new Request(mergeOptions(this._defaultOptions, options, exports.RequestMethod.Get, url)));
            }
            else if (url instanceof Request) {
                responseObservable = httpRequest(this._backend, url);
            }
            else {
                throw makeTypeError('First argument must be a url string or Request instance.');
            }
            return responseObservable;
        };
        /**
         * Performs a request with `get` http method.
         */
        Http.prototype.get = function (url, options) {
            return httpRequest(this._backend, new Request(mergeOptions(this._defaultOptions, options, exports.RequestMethod.Get, url)));
        };
        /**
         * Performs a request with `post` http method.
         */
        Http.prototype.post = function (url, body, options) {
            return httpRequest(this._backend, new Request(mergeOptions(this._defaultOptions.merge(new RequestOptions({ body: body })), options, exports.RequestMethod.Post, url)));
        };
        /**
         * Performs a request with `put` http method.
         */
        Http.prototype.put = function (url, body, options) {
            return httpRequest(this._backend, new Request(mergeOptions(this._defaultOptions.merge(new RequestOptions({ body: body })), options, exports.RequestMethod.Put, url)));
        };
        /**
         * Performs a request with `delete` http method.
         */
        Http.prototype.delete = function (url, options) {
            return httpRequest(this._backend, new Request(mergeOptions(this._defaultOptions, options, exports.RequestMethod.Delete, url)));
        };
        /**
         * Performs a request with `patch` http method.
         */
        Http.prototype.patch = function (url, body, options) {
            return httpRequest(this._backend, new Request(mergeOptions(this._defaultOptions.merge(new RequestOptions({ body: body })), options, exports.RequestMethod.Patch, url)));
        };
        /**
         * Performs a request with `head` http method.
         */
        Http.prototype.head = function (url, options) {
            return httpRequest(this._backend, new Request(mergeOptions(this._defaultOptions, options, exports.RequestMethod.Head, url)));
        };
        return Http;
    }());
    /** @nocollapse */
    Http.decorators = [
        { type: _angular_core.Injectable },
    ];
    /** @nocollapse */
    Http.ctorParameters = [
        { type: ConnectionBackend, },
        { type: RequestOptions, },
    ];
    var Jsonp = (function (_super) {
        __extends(Jsonp, _super);
        function Jsonp(backend, defaultOptions) {
            _super.call(this, backend, defaultOptions);
        }
        /**
         * Performs any type of http request. First argument is required, and can either be a url or
         * a {@link Request} instance. If the first argument is a url, an optional {@link RequestOptions}
         * object can be provided as the 2nd argument. The options object will be merged with the values
         * of {@link BaseRequestOptions} before performing the request.
         *
         * @security Regular XHR is the safest alternative to JSONP for most applications, and is
         * supported by all current browsers. Because JSONP creates a `<script>` element with
         * contents retrieved from a remote source, attacker-controlled data introduced by an untrusted
         * source could expose your application to XSS risks. Data exposed by JSONP may also be
         * readable by malicious third-party websites. In addition, JSONP introduces potential risk for
         * future security issues (e.g. content sniffing).  For more detail, see the
         * [Security Guide](http://g.co/ng/security).
         */
        Jsonp.prototype.request = function (url, options) {
            var responseObservable;
            if (isString(url)) {
                url =
                    new Request(mergeOptions(this._defaultOptions, options, exports.RequestMethod.Get, url));
            }
            if (url instanceof Request) {
                if (url.method !== exports.RequestMethod.Get) {
                    makeTypeError('JSONP requests must use GET request method.');
                }
                responseObservable = httpRequest(this._backend, url);
            }
            else {
                throw makeTypeError('First argument must be a url string or Request instance.');
            }
            return responseObservable;
        };
        return Jsonp;
    }(Http));
    /** @nocollapse */
    Jsonp.decorators = [
        { type: _angular_core.Injectable },
    ];
    /** @nocollapse */
    Jsonp.ctorParameters = [
        { type: ConnectionBackend, },
        { type: RequestOptions, },
    ];
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
    var HTTP_PROVIDERS = [
        // TODO(pascal): use factory type annotations once supported in DI
        // issue: https://github.com/angular/angular/issues/3183
        { provide: Http, useFactory: httpFactory, deps: [XHRBackend, RequestOptions] },
        BrowserXhr,
        { provide: RequestOptions, useClass: BaseRequestOptions },
        { provide: ResponseOptions, useClass: BaseResponseOptions },
        XHRBackend,
        { provide: XSRFStrategy, useValue: new CookieXSRFStrategy() },
    ];
    /**
     * @experimental
     */
    function httpFactory(xhrBackend, requestOptions) {
        return new Http(xhrBackend, requestOptions);
    }
    /**
     * See {@link HTTP_PROVIDERS} instead.
     *
     * @deprecated
     */
    var HTTP_BINDINGS = HTTP_PROVIDERS;
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
    var JSONP_PROVIDERS = [
        // TODO(pascal): use factory type annotations once supported in DI
        // issue: https://github.com/angular/angular/issues/3183
        { provide: Jsonp, useFactory: jsonpFactory, deps: [JSONPBackend, RequestOptions] },
        BrowserJsonp,
        { provide: RequestOptions, useClass: BaseRequestOptions },
        { provide: ResponseOptions, useClass: BaseResponseOptions },
        { provide: JSONPBackend, useClass: JSONPBackend_ },
    ];
    function jsonpFactory(jsonpBackend, requestOptions) {
        return new Jsonp(jsonpBackend, requestOptions);
    }
    /**
     * See {@link JSONP_PROVIDERS} instead.
     *
     * @deprecated
     */
    var JSON_BINDINGS = JSONP_PROVIDERS;
    exports.HTTP_PROVIDERS = HTTP_PROVIDERS;
    exports.httpFactory = httpFactory;
    exports.HTTP_BINDINGS = HTTP_BINDINGS;
    exports.JSONP_PROVIDERS = JSONP_PROVIDERS;
    exports.JSON_BINDINGS = JSON_BINDINGS;
    exports.BrowserXhr = BrowserXhr;
    exports.JSONPBackend = JSONPBackend;
    exports.JSONPConnection = JSONPConnection;
    exports.CookieXSRFStrategy = CookieXSRFStrategy;
    exports.XHRBackend = XHRBackend;
    exports.XHRConnection = XHRConnection;
    exports.BaseRequestOptions = BaseRequestOptions;
    exports.RequestOptions = RequestOptions;
    exports.BaseResponseOptions = BaseResponseOptions;
    exports.ResponseOptions = ResponseOptions;
    exports.Headers = Headers;
    exports.Http = Http;
    exports.Jsonp = Jsonp;
    exports.Connection = Connection;
    exports.ConnectionBackend = ConnectionBackend;
    exports.XSRFStrategy = XSRFStrategy;
    exports.Request = Request;
    exports.Response = Response;
    exports.QueryEncoder = QueryEncoder;
    exports.URLSearchParams = URLSearchParams;
}));
