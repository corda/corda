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
    typeof exports === 'object' && typeof module !== 'undefined' ? factory(exports, require('@angular/common'), require('@angular/core'), require('rxjs/Subject'), require('rxjs/observable/PromiseObservable'), require('rxjs/operator/toPromise'), require('rxjs/Observable')) :
        typeof define === 'function' && define.amd ? define(['exports', '@angular/common', '@angular/core', 'rxjs/Subject', 'rxjs/observable/PromiseObservable', 'rxjs/operator/toPromise', 'rxjs/Observable'], factory) :
            (factory((global.ng = global.ng || {}, global.ng.platformBrowser = global.ng.platformBrowser || {}), global.ng.common, global.ng.core, global.Rx, global.Rx, global.Rx.Observable.prototype, global.Rx));
}(this, function (exports, _angular_common, _angular_core, rxjs_Subject, rxjs_observable_PromiseObservable, rxjs_operator_toPromise, rxjs_Observable) {
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
    var Date = global$1.Date;
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
    function isNumber(obj) {
        return typeof obj === 'number';
    }
    function isString(obj) {
        return typeof obj === 'string';
    }
    function isFunction(obj) {
        return typeof obj === 'function';
    }
    function isArray(obj) {
        return Array.isArray(obj);
    }
    function noop() { }
    function stringify(token) {
        if (typeof token === 'string') {
            return token;
        }
        if (token === undefined || token === null) {
            return '' + token;
        }
        if (token.name) {
            return token.name;
        }
        if (token.overriddenName) {
            return token.overriddenName;
        }
        var res = token.toString();
        var newLineIndex = res.indexOf('\n');
        return (newLineIndex === -1) ? res : res.substring(0, newLineIndex);
    }
    // serialize / deserialize enum exist only for consistency with dart API
    // enums in typescript don't need to be serialized
    function serializeEnum(val) {
        return val;
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
    var NumberParseError = (function (_super) {
        __extends(NumberParseError, _super);
        function NumberParseError(message) {
            _super.call(this);
            this.message = message;
        }
        NumberParseError.prototype.toString = function () { return this.message; };
        return NumberParseError;
    }(Error));
    var NumberWrapper = (function () {
        function NumberWrapper() {
        }
        NumberWrapper.toFixed = function (n, fractionDigits) { return n.toFixed(fractionDigits); };
        NumberWrapper.equal = function (a, b) { return a === b; };
        NumberWrapper.parseIntAutoRadix = function (text) {
            var result = parseInt(text);
            if (isNaN(result)) {
                throw new NumberParseError('Invalid integer literal when parsing ' + text);
            }
            return result;
        };
        NumberWrapper.parseInt = function (text, radix) {
            if (radix == 10) {
                if (/^(\-|\+)?[0-9]+$/.test(text)) {
                    return parseInt(text, radix);
                }
            }
            else if (radix == 16) {
                if (/^(\-|\+)?[0-9ABCDEFabcdef]+$/.test(text)) {
                    return parseInt(text, radix);
                }
            }
            else {
                var result = parseInt(text, radix);
                if (!isNaN(result)) {
                    return result;
                }
            }
            throw new NumberParseError('Invalid integer literal when parsing ' + text + ' in base ' + radix);
        };
        // TODO: NaN is a valid literal but is returned by parseFloat to indicate an error.
        NumberWrapper.parseFloat = function (text) { return parseFloat(text); };
        Object.defineProperty(NumberWrapper, "NaN", {
            get: function () { return NaN; },
            enumerable: true,
            configurable: true
        });
        NumberWrapper.isNumeric = function (value) { return !isNaN(value - parseFloat(value)); };
        NumberWrapper.isNaN = function (value) { return isNaN(value); };
        NumberWrapper.isInteger = function (value) { return Number.isInteger(value); };
        return NumberWrapper;
    }());
    var RegExpWrapper = (function () {
        function RegExpWrapper() {
        }
        RegExpWrapper.create = function (regExpStr, flags) {
            if (flags === void 0) { flags = ''; }
            flags = flags.replace(/g/g, '');
            return new global$1.RegExp(regExpStr, flags + 'g');
        };
        RegExpWrapper.firstMatch = function (regExp, input) {
            // Reset multimatch regex state
            regExp.lastIndex = 0;
            return regExp.exec(input);
        };
        RegExpWrapper.test = function (regExp, input) {
            regExp.lastIndex = 0;
            return regExp.test(input);
        };
        RegExpWrapper.matcher = function (regExp, input) {
            // Reset regex state for the case
            // someone did not loop over all matches
            // last time.
            regExp.lastIndex = 0;
            return { re: regExp, input: input };
        };
        RegExpWrapper.replaceAll = function (regExp, input, replace) {
            var c = regExp.exec(input);
            var res = '';
            regExp.lastIndex = 0;
            var prev = 0;
            while (c) {
                res += input.substring(prev, c.index);
                res += replace(c);
                prev = c.index + c[0].length;
                regExp.lastIndex = prev;
                c = regExp.exec(input);
            }
            res += input.substring(prev);
            return res;
        };
        return RegExpWrapper;
    }());
    var FunctionWrapper = (function () {
        function FunctionWrapper() {
        }
        FunctionWrapper.apply = function (fn, posArgs) { return fn.apply(null, posArgs); };
        FunctionWrapper.bind = function (fn, scope) { return fn.bind(scope); };
        return FunctionWrapper;
    }());
    function print(obj) {
        console.log(obj);
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
    var DateWrapper = (function () {
        function DateWrapper() {
        }
        DateWrapper.create = function (year, month, day, hour, minutes, seconds, milliseconds) {
            if (month === void 0) { month = 1; }
            if (day === void 0) { day = 1; }
            if (hour === void 0) { hour = 0; }
            if (minutes === void 0) { minutes = 0; }
            if (seconds === void 0) { seconds = 0; }
            if (milliseconds === void 0) { milliseconds = 0; }
            return new Date(year, month - 1, day, hour, minutes, seconds, milliseconds);
        };
        DateWrapper.fromISOString = function (str) { return new Date(str); };
        DateWrapper.fromMillis = function (ms) { return new Date(ms); };
        DateWrapper.toMillis = function (date) { return date.getTime(); };
        DateWrapper.now = function () { return new Date(); };
        DateWrapper.toJson = function (date) { return date.toJSON(); };
        return DateWrapper;
    }());
    function setValueOnPath(global, path, value) {
        var parts = path.split('.');
        var obj = global;
        while (parts.length > 1) {
            var name = parts.shift();
            if (obj.hasOwnProperty(name) && isPresent(obj[name])) {
                obj = obj[name];
            }
            else {
                obj = obj[name] = {};
            }
        }
        if (obj === undefined || obj === null) {
            obj = {};
        }
        obj[parts.shift()] = value;
    }
    var _DOM = null;
    function getDOM() {
        return _DOM;
    }
    function setRootDomAdapter(adapter) {
        if (isBlank(_DOM)) {
            _DOM = adapter;
        }
    }
    /* tslint:disable:requireParameterType */
    /**
     * Provides DOM operations in an environment-agnostic way.
     */
    var DomAdapter = (function () {
        function DomAdapter() {
            this.xhrType = null;
        }
        /** @deprecated */
        DomAdapter.prototype.getXHR = function () { return this.xhrType; };
        Object.defineProperty(DomAdapter.prototype, "attrToPropMap", {
            /**
             * Maps attribute names to their corresponding property names for cases
             * where attribute name doesn't match property name.
             */
            get: function () { return this._attrToPropMap; },
            set: function (value) { this._attrToPropMap = value; },
            enumerable: true,
            configurable: true
        });
        ;
        ;
        return DomAdapter;
    }());
    /**
     * @license
     * Copyright Google Inc. All Rights Reserved.
     *
     * Use of this source code is governed by an MIT-style license that can be
     * found in the LICENSE file at https://angular.io/license
     */
    function supportsState() {
        return !!window.history.pushState;
    }
    var BrowserPlatformLocation = (function (_super) {
        __extends(BrowserPlatformLocation, _super);
        function BrowserPlatformLocation() {
            _super.call(this);
            this._init();
        }
        // This is moved to its own method so that `MockPlatformLocationStrategy` can overwrite it
        /** @internal */
        BrowserPlatformLocation.prototype._init = function () {
            this._location = getDOM().getLocation();
            this._history = getDOM().getHistory();
        };
        Object.defineProperty(BrowserPlatformLocation.prototype, "location", {
            /** @internal */
            get: function () { return this._location; },
            enumerable: true,
            configurable: true
        });
        BrowserPlatformLocation.prototype.getBaseHrefFromDOM = function () { return getDOM().getBaseHref(); };
        BrowserPlatformLocation.prototype.onPopState = function (fn) {
            getDOM().getGlobalEventTarget('window').addEventListener('popstate', fn, false);
        };
        BrowserPlatformLocation.prototype.onHashChange = function (fn) {
            getDOM().getGlobalEventTarget('window').addEventListener('hashchange', fn, false);
        };
        Object.defineProperty(BrowserPlatformLocation.prototype, "pathname", {
            get: function () { return this._location.pathname; },
            set: function (newPath) { this._location.pathname = newPath; },
            enumerable: true,
            configurable: true
        });
        Object.defineProperty(BrowserPlatformLocation.prototype, "search", {
            get: function () { return this._location.search; },
            enumerable: true,
            configurable: true
        });
        Object.defineProperty(BrowserPlatformLocation.prototype, "hash", {
            get: function () { return this._location.hash; },
            enumerable: true,
            configurable: true
        });
        BrowserPlatformLocation.prototype.pushState = function (state, title, url) {
            if (supportsState()) {
                this._history.pushState(state, title, url);
            }
            else {
                this._location.hash = url;
            }
        };
        BrowserPlatformLocation.prototype.replaceState = function (state, title, url) {
            if (supportsState()) {
                this._history.replaceState(state, title, url);
            }
            else {
                this._location.hash = url;
            }
        };
        BrowserPlatformLocation.prototype.forward = function () { this._history.forward(); };
        BrowserPlatformLocation.prototype.back = function () { this._history.back(); };
        return BrowserPlatformLocation;
    }(_angular_common.PlatformLocation));
    /** @nocollapse */
    BrowserPlatformLocation.decorators = [
        { type: _angular_core.Injectable },
    ];
    /** @nocollapse */
    BrowserPlatformLocation.ctorParameters = [];
    /**
     * A service that can be used to get and set the title of a current HTML document.
     *
     * Since an Angular 2 application can't be bootstrapped on the entire HTML document (`<html>` tag)
     * it is not possible to bind to the `text` property of the `HTMLTitleElement` elements
     * (representing the `<title>` tag). Instead, this service can be used to set and get the current
     * title value.
     *
     * @experimental
     */
    var Title = (function () {
        function Title() {
        }
        /**
         * Get the title of the current HTML document.
         * @returns {string}
         */
        Title.prototype.getTitle = function () { return getDOM().getTitle(); };
        /**
         * Set the title of the current HTML document.
         * @param newTitle
         */
        Title.prototype.setTitle = function (newTitle) { getDOM().setTitle(newTitle); };
        return Title;
    }());
    /**
     * @license
     * Copyright Google Inc. All Rights Reserved.
     *
     * Use of this source code is governed by an MIT-style license that can be
     * found in the LICENSE file at https://angular.io/license
     */
    /**
     * JS version of browser APIs. This library can only run in the browser.
     */
    var win = typeof window !== 'undefined' && window || {};
    var ChangeDetectionPerfRecord = (function () {
        function ChangeDetectionPerfRecord(msPerTick, numTicks) {
            this.msPerTick = msPerTick;
            this.numTicks = numTicks;
        }
        return ChangeDetectionPerfRecord;
    }());
    /**
     * Entry point for all Angular debug tools. This object corresponds to the `ng`
     * global variable accessible in the dev console.
     */
    var AngularTools = (function () {
        function AngularTools(ref) {
            this.profiler = new AngularProfiler(ref);
        }
        return AngularTools;
    }());
    /**
     * Entry point for all Angular profiling-related debug tools. This object
     * corresponds to the `ng.profiler` in the dev console.
     */
    var AngularProfiler = (function () {
        function AngularProfiler(ref) {
            this.appRef = ref.injector.get(_angular_core.ApplicationRef);
        }
        /**
         * Exercises change detection in a loop and then prints the average amount of
         * time in milliseconds how long a single round of change detection takes for
         * the current state of the UI. It runs a minimum of 5 rounds for a minimum
         * of 500 milliseconds.
         *
         * Optionally, a user may pass a `config` parameter containing a map of
         * options. Supported options are:
         *
         * `record` (boolean) - causes the profiler to record a CPU profile while
         * it exercises the change detector. Example:
         *
         * ```
         * ng.profiler.timeChangeDetection({record: true})
         * ```
         */
        AngularProfiler.prototype.timeChangeDetection = function (config) {
            var record = isPresent(config) && config['record'];
            var profileName = 'Change Detection';
            // Profiler is not available in Android browsers, nor in IE 9 without dev tools opened
            var isProfilerAvailable = isPresent(win.console.profile);
            if (record && isProfilerAvailable) {
                win.console.profile(profileName);
            }
            var start = getDOM().performanceNow();
            var numTicks = 0;
            while (numTicks < 5 || (getDOM().performanceNow() - start) < 500) {
                this.appRef.tick();
                numTicks++;
            }
            var end = getDOM().performanceNow();
            if (record && isProfilerAvailable) {
                // need to cast to <any> because type checker thinks there's no argument
                // while in fact there is:
                //
                // https://developer.mozilla.org/en-US/docs/Web/API/Console/profileEnd
                win.console.profileEnd(profileName);
            }
            var msPerTick = (end - start) / numTicks;
            win.console.log("ran " + numTicks + " change detection cycles");
            win.console.log(NumberWrapper.toFixed(msPerTick, 2) + " ms per check");
            return new ChangeDetectionPerfRecord(msPerTick, numTicks);
        };
        return AngularProfiler;
    }());
    var context = global$1;
    /**
     * Enabled Angular 2 debug tools that are accessible via your browser's
     * developer console.
     *
     * Usage:
     *
     * 1. Open developer console (e.g. in Chrome Ctrl + Shift + j)
     * 1. Type `ng.` (usually the console will show auto-complete suggestion)
     * 1. Try the change detection profiler `ng.profiler.timeChangeDetection()`
     *    then hit Enter.
     *
     * @experimental All debugging apis are currently experimental.
     */
    function enableDebugTools(ref) {
        context.ng = new AngularTools(ref);
        return ref;
    }
    /**
     * Disables Angular 2 tools.
     *
     * @experimental All debugging apis are currently experimental.
     */
    function disableDebugTools() {
        delete context.ng;
    }
    /**
     * Predicates for use with {@link DebugElement}'s query functions.
     *
     * @experimental All debugging apis are currently experimental.
     */
    var By = (function () {
        function By() {
        }
        /**
         * Match all elements.
         *
         * ## Example
         *
         * {@example platform/dom/debug/ts/by/by.ts region='by_all'}
         */
        By.all = function () { return function (debugElement) { return true; }; };
        /**
         * Match elements by the given CSS selector.
         *
         * ## Example
         *
         * {@example platform/dom/debug/ts/by/by.ts region='by_css'}
         */
        By.css = function (selector) {
            return function (debugElement) {
                return isPresent(debugElement.nativeElement) ?
                    getDOM().elementMatches(debugElement.nativeElement, selector) :
                    false;
            };
        };
        /**
         * Match elements that have the given directive present.
         *
         * ## Example
         *
         * {@example platform/dom/debug/ts/by/by.ts region='by_directive'}
         */
        By.directive = function (type) {
            return function (debugElement) { return debugElement.providerTokens.indexOf(type) !== -1; };
        };
        return By;
    }());
    /**
     * A DI Token representing the main rendering context. In a browser this is the DOM Document.
     *
     * Note: Document might not be available in the Application Context when Application and Rendering
     * Contexts are not the same (e.g. when running the application into a Web Worker).
     *
     * @stable
     */
    var DOCUMENT = new _angular_core.OpaqueToken('DocumentToken');
    var Map$1 = global$1.Map;
    var Set$1 = global$1.Set;
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
    // Safari and Internet Explorer do not support the iterable parameter to the
    // Set constructor.  We work around that by manually adding the items.
    var createSetFromList = (function () {
        var test = new Set$1([1, 2, 3]);
        if (test.size === 3) {
            return function createSetFromList(lst) { return new Set$1(lst); };
        }
        else {
            return function createSetAndPopulateFromList(lst) {
                var res = new Set$1(lst);
                if (res.size !== lst.length) {
                    for (var i = 0; i < lst.length; i++) {
                        res.add(lst[i]);
                    }
                }
                return res;
            };
        }
    })();
    var SetWrapper = (function () {
        function SetWrapper() {
        }
        SetWrapper.createFromList = function (lst) { return createSetFromList(lst); };
        SetWrapper.has = function (s, key) { return s.has(key); };
        SetWrapper.delete = function (m, k) { m.delete(k); };
        return SetWrapper;
    }());
    /**
     * @stable
     */
    var BaseException$1 = (function (_super) {
        __extends(BaseException$1, _super);
        function BaseException$1(message) {
            if (message === void 0) { message = '--'; }
            _super.call(this, message);
            this.message = message;
            this.stack = (new Error(message)).stack;
        }
        BaseException$1.prototype.toString = function () { return this.message; };
        return BaseException$1;
    }(Error));
    /**
     * @stable
     */
    var EVENT_MANAGER_PLUGINS = new _angular_core.OpaqueToken('EventManagerPlugins');
    var EventManager = (function () {
        function EventManager(plugins, _zone) {
            var _this = this;
            this._zone = _zone;
            plugins.forEach(function (p) { return p.manager = _this; });
            this._plugins = ListWrapper.reversed(plugins);
        }
        EventManager.prototype.addEventListener = function (element, eventName, handler) {
            var plugin = this._findPluginFor(eventName);
            return plugin.addEventListener(element, eventName, handler);
        };
        EventManager.prototype.addGlobalEventListener = function (target, eventName, handler) {
            var plugin = this._findPluginFor(eventName);
            return plugin.addGlobalEventListener(target, eventName, handler);
        };
        EventManager.prototype.getZone = function () { return this._zone; };
        /** @internal */
        EventManager.prototype._findPluginFor = function (eventName) {
            var plugins = this._plugins;
            for (var i = 0; i < plugins.length; i++) {
                var plugin = plugins[i];
                if (plugin.supports(eventName)) {
                    return plugin;
                }
            }
            throw new BaseException$1("No event manager plugin found for event " + eventName);
        };
        return EventManager;
    }());
    /** @nocollapse */
    EventManager.decorators = [
        { type: _angular_core.Injectable },
    ];
    /** @nocollapse */
    EventManager.ctorParameters = [
        { type: Array, decorators: [{ type: _angular_core.Inject, args: [EVENT_MANAGER_PLUGINS,] },] },
        { type: _angular_core.NgZone, },
    ];
    var EventManagerPlugin = (function () {
        function EventManagerPlugin() {
        }
        // That is equivalent to having supporting $event.target
        EventManagerPlugin.prototype.supports = function (eventName) { return false; };
        EventManagerPlugin.prototype.addEventListener = function (element, eventName, handler) {
            throw 'not implemented';
        };
        EventManagerPlugin.prototype.addGlobalEventListener = function (element, eventName, handler) {
            throw 'not implemented';
        };
        return EventManagerPlugin;
    }());
    var _eventNames = {
        // pan
        'pan': true,
        'panstart': true,
        'panmove': true,
        'panend': true,
        'pancancel': true,
        'panleft': true,
        'panright': true,
        'panup': true,
        'pandown': true,
        // pinch
        'pinch': true,
        'pinchstart': true,
        'pinchmove': true,
        'pinchend': true,
        'pinchcancel': true,
        'pinchin': true,
        'pinchout': true,
        // press
        'press': true,
        'pressup': true,
        // rotate
        'rotate': true,
        'rotatestart': true,
        'rotatemove': true,
        'rotateend': true,
        'rotatecancel': true,
        // swipe
        'swipe': true,
        'swipeleft': true,
        'swiperight': true,
        'swipeup': true,
        'swipedown': true,
        // tap
        'tap': true,
    };
    var HammerGesturesPluginCommon = (function (_super) {
        __extends(HammerGesturesPluginCommon, _super);
        function HammerGesturesPluginCommon() {
            _super.call(this);
        }
        HammerGesturesPluginCommon.prototype.supports = function (eventName) {
            eventName = eventName.toLowerCase();
            return StringMapWrapper.contains(_eventNames, eventName);
        };
        return HammerGesturesPluginCommon;
    }(EventManagerPlugin));
    /**
     * A DI token that you can use to provide{@link HammerGestureConfig} to Angular. Use it to configure
     * Hammer gestures.
     *
     * @experimental
     */
    var HAMMER_GESTURE_CONFIG = new _angular_core.OpaqueToken('HammerGestureConfig');
    var HammerGestureConfig = (function () {
        function HammerGestureConfig() {
            this.events = [];
            this.overrides = {};
        }
        HammerGestureConfig.prototype.buildHammer = function (element) {
            var mc = new Hammer(element);
            mc.get('pinch').set({ enable: true });
            mc.get('rotate').set({ enable: true });
            for (var eventName in this.overrides) {
                mc.get(eventName).set(this.overrides[eventName]);
            }
            return mc;
        };
        return HammerGestureConfig;
    }());
    /** @nocollapse */
    HammerGestureConfig.decorators = [
        { type: _angular_core.Injectable },
    ];
    var HammerGesturesPlugin = (function (_super) {
        __extends(HammerGesturesPlugin, _super);
        function HammerGesturesPlugin(_config) {
            _super.call(this);
            this._config = _config;
        }
        HammerGesturesPlugin.prototype.supports = function (eventName) {
            if (!_super.prototype.supports.call(this, eventName) && !this.isCustomEvent(eventName))
                return false;
            if (!isPresent(window['Hammer'])) {
                throw new BaseException$1("Hammer.js is not loaded, can not bind " + eventName + " event");
            }
            return true;
        };
        HammerGesturesPlugin.prototype.addEventListener = function (element, eventName, handler) {
            var _this = this;
            var zone = this.manager.getZone();
            eventName = eventName.toLowerCase();
            return zone.runOutsideAngular(function () {
                // Creating the manager bind events, must be done outside of angular
                var mc = _this._config.buildHammer(element);
                var callback = function (eventObj /** TODO #???? */) {
                    zone.runGuarded(function () { handler(eventObj); });
                };
                mc.on(eventName, callback);
                return function () { mc.off(eventName, callback); };
            });
        };
        HammerGesturesPlugin.prototype.isCustomEvent = function (eventName) { return this._config.events.indexOf(eventName) > -1; };
        return HammerGesturesPlugin;
    }(HammerGesturesPluginCommon));
    /** @nocollapse */
    HammerGesturesPlugin.decorators = [
        { type: _angular_core.Injectable },
    ];
    /** @nocollapse */
    HammerGesturesPlugin.ctorParameters = [
        { type: HammerGestureConfig, decorators: [{ type: _angular_core.Inject, args: [HAMMER_GESTURE_CONFIG,] },] },
    ];
    var wtfInit = _angular_core.__core_private__.wtfInit;
    var VIEW_ENCAPSULATION_VALUES = _angular_core.__core_private__.VIEW_ENCAPSULATION_VALUES;
    var DebugDomRootRenderer = _angular_core.__core_private__.DebugDomRootRenderer;
    /**
     * @experimental bogus marker to pass the ts-api-guardian's check - this api should be public so
     * this line will go away when that happens
     */
    var SecurityContext = _angular_core.__core_private__.SecurityContext;
    var SanitizationService = _angular_core.__core_private__.SanitizationService;
    var NoOpAnimationDriver = _angular_core.__core_private__.NoOpAnimationDriver;
    var AnimationDriver = _angular_core.__core_private__.AnimationDriver;
    /**
     * A pattern that recognizes a commonly useful subset of URLs that are safe.
     *
     * This regular expression matches a subset of URLs that will not cause script
     * execution if used in URL context within a HTML document. Specifically, this
     * regular expression matches if (comment from here on and regex copied from
     * Soy's EscapingConventions):
     * (1) Either a protocol in a whitelist (http, https, mailto or ftp).
     * (2) or no protocol.  A protocol must be followed by a colon. The below
     *     allows that by allowing colons only after one of the characters [/?#].
     *     A colon after a hash (#) must be in the fragment.
     *     Otherwise, a colon after a (?) must be in a query.
     *     Otherwise, a colon after a single solidus (/) must be in a path.
     *     Otherwise, a colon after a double solidus (//) must be in the authority
     *     (before port).
     *
     * The pattern disallows &, used in HTML entity declarations before
     * one of the characters in [/?#]. This disallows HTML entities used in the
     * protocol name, which should never happen, e.g. "h&#116;tp" for "http".
     * It also disallows HTML entities in the first path part of a relative path,
     * e.g. "foo&lt;bar/baz".  Our existing escaping functions should not produce
     * that. More importantly, it disallows masking of a colon,
     * e.g. "javascript&#58;...".
     *
     * This regular expression was taken from the Closure sanitization library.
     */
    var SAFE_URL_PATTERN = /^(?:(?:https?|mailto|ftp|tel|file):|[^&:/?#]*(?:[/?#]|$))/gi;
    /** A pattern that matches safe data URLs. Only matches image, video and audio types. */
    var DATA_URL_PATTERN = /^data:(?:image\/(?:bmp|gif|jpeg|jpg|png|tiff|webp)|video\/(?:mpeg|mp4|ogg|webm)|audio\/(?:mp3|oga|ogg|opus));base64,[a-z0-9+\/]+=*$/i;
    function sanitizeUrl(url) {
        url = String(url);
        if (url.match(SAFE_URL_PATTERN) || url.match(DATA_URL_PATTERN))
            return url;
        if (_angular_core.isDevMode()) {
            getDOM().log("WARNING: sanitizing unsafe URL value " + url + " (see http://g.co/ng/security#xss)");
        }
        return 'unsafe:' + url;
    }
    function sanitizeSrcset(srcset) {
        srcset = String(srcset);
        return srcset.split(',').map(function (srcset) { return sanitizeUrl(srcset.trim()); }).join(', ');
    }
    /** A <body> element that can be safely used to parse untrusted HTML. Lazily initialized below. */
    var inertElement = null;
    /** Lazily initialized to make sure the DOM adapter gets set before use. */
    var DOM = null;
    /** Returns an HTML element that is guaranteed to not execute code when creating elements in it. */
    function getInertElement() {
        if (inertElement)
            return inertElement;
        DOM = getDOM();
        // Prefer using <template> element if supported.
        var templateEl = DOM.createElement('template');
        if ('content' in templateEl)
            return templateEl;
        var doc = DOM.createHtmlDocument();
        inertElement = DOM.querySelector(doc, 'body');
        if (inertElement == null) {
            // usually there should be only one body element in the document, but IE doesn't have any, so we
            // need to create one.
            var html = DOM.createElement('html', doc);
            inertElement = DOM.createElement('body', doc);
            DOM.appendChild(html, inertElement);
            DOM.appendChild(doc, html);
        }
        return inertElement;
    }
    function tagSet(tags) {
        var res = {};
        for (var _i = 0, _a = tags.split(','); _i < _a.length; _i++) {
            var t = _a[_i];
            res[t.toLowerCase()] = true;
        }
        return res;
    }
    function merge() {
        var sets = [];
        for (var _i = 0; _i < arguments.length; _i++) {
            sets[_i - 0] = arguments[_i];
        }
        var res = {};
        for (var _a = 0, sets_1 = sets; _a < sets_1.length; _a++) {
            var s = sets_1[_a];
            for (var v in s) {
                if (s.hasOwnProperty(v))
                    res[v] = true;
            }
        }
        return res;
    }
    // Good source of info about elements and attributes
    // http://dev.w3.org/html5/spec/Overview.html#semantics
    // http://simon.html5.org/html-elements
    // Safe Void Elements - HTML5
    // http://dev.w3.org/html5/spec/Overview.html#void-elements
    var VOID_ELEMENTS = tagSet('area,br,col,hr,img,wbr');
    // Elements that you can, intentionally, leave open (and which close themselves)
    // http://dev.w3.org/html5/spec/Overview.html#optional-tags
    var OPTIONAL_END_TAG_BLOCK_ELEMENTS = tagSet('colgroup,dd,dt,li,p,tbody,td,tfoot,th,thead,tr');
    var OPTIONAL_END_TAG_INLINE_ELEMENTS = tagSet('rp,rt');
    var OPTIONAL_END_TAG_ELEMENTS = merge(OPTIONAL_END_TAG_INLINE_ELEMENTS, OPTIONAL_END_TAG_BLOCK_ELEMENTS);
    // Safe Block Elements - HTML5
    var BLOCK_ELEMENTS = merge(OPTIONAL_END_TAG_BLOCK_ELEMENTS, tagSet('address,article,' +
        'aside,blockquote,caption,center,del,details,dialog,dir,div,dl,figure,figcaption,footer,h1,h2,h3,h4,h5,' +
        'h6,header,hgroup,hr,ins,main,map,menu,nav,ol,pre,section,summary,table,ul'));
    // Inline Elements - HTML5
    var INLINE_ELEMENTS = merge(OPTIONAL_END_TAG_INLINE_ELEMENTS, tagSet('a,abbr,acronym,audio,b,' +
        'bdi,bdo,big,br,cite,code,del,dfn,em,font,i,img,ins,kbd,label,map,mark,picture,q,ruby,rp,rt,s,' +
        'samp,small,source,span,strike,strong,sub,sup,time,track,tt,u,var,video'));
    var VALID_ELEMENTS = merge(VOID_ELEMENTS, BLOCK_ELEMENTS, INLINE_ELEMENTS, OPTIONAL_END_TAG_ELEMENTS);
    // Attributes that have href and hence need to be sanitized
    var URI_ATTRS = tagSet('background,cite,href,itemtype,longdesc,poster,src,xlink:href');
    // Attributes that have special href set hence need to be sanitized
    var SRCSET_ATTRS = tagSet('srcset');
    var HTML_ATTRS = tagSet('abbr,accesskey,align,alt,autoplay,axis,bgcolor,border,cellpadding,cellspacing,class,clear,color,cols,colspan,' +
        'compact,controls,coords,datetime,default,dir,download,face,headers,height,hidden,hreflang,hspace,' +
        'ismap,itemscope,itemprop,kind,label,lang,language,loop,media,muted,nohref,nowrap,open,preload,rel,rev,role,rows,rowspan,rules,' +
        'scope,scrolling,shape,size,sizes,span,srclang,start,summary,tabindex,target,title,translate,type,usemap,' +
        'valign,value,vspace,width');
    // NB: This currently conciously doesn't support SVG. SVG sanitization has had several security
    // issues in the past, so it seems safer to leave it out if possible. If support for binding SVG via
    // innerHTML is required, SVG attributes should be added here.
    // NB: Sanitization does not allow <form> elements or other active elements (<button> etc). Those
    // can be sanitized, but they increase security surface area without a legitimate use case, so they
    // are left out here.
    var VALID_ATTRS = merge(URI_ATTRS, SRCSET_ATTRS, HTML_ATTRS);
    /**
     * SanitizingHtmlSerializer serializes a DOM fragment, stripping out any unsafe elements and unsafe
     * attributes.
     */
    var SanitizingHtmlSerializer = (function () {
        function SanitizingHtmlSerializer() {
            this.buf = [];
        }
        SanitizingHtmlSerializer.prototype.sanitizeChildren = function (el) {
            // This cannot use a TreeWalker, as it has to run on Angular's various DOM adapters.
            // However this code never accesses properties off of `document` before deleting its contents
            // again, so it shouldn't be vulnerable to DOM clobbering.
            var current = el.firstChild;
            while (current) {
                if (DOM.isElementNode(current)) {
                    this.startElement(current);
                }
                else if (DOM.isTextNode(current)) {
                    this.chars(DOM.nodeValue(current));
                }
                if (DOM.firstChild(current)) {
                    current = DOM.firstChild(current);
                    continue;
                }
                while (current) {
                    // Leaving the element. Walk up and to the right, closing tags as we go.
                    if (DOM.isElementNode(current)) {
                        this.endElement(DOM.nodeName(current).toLowerCase());
                    }
                    if (DOM.nextSibling(current)) {
                        current = DOM.nextSibling(current);
                        break;
                    }
                    current = DOM.parentElement(current);
                }
            }
            return this.buf.join('');
        };
        SanitizingHtmlSerializer.prototype.startElement = function (element) {
            var _this = this;
            var tagName = DOM.nodeName(element).toLowerCase();
            tagName = tagName.toLowerCase();
            if (VALID_ELEMENTS.hasOwnProperty(tagName)) {
                this.buf.push('<');
                this.buf.push(tagName);
                DOM.attributeMap(element).forEach(function (value, attrName) {
                    var lower = attrName.toLowerCase();
                    if (!VALID_ATTRS.hasOwnProperty(lower))
                        return;
                    // TODO(martinprobst): Special case image URIs for data:image/...
                    if (URI_ATTRS[lower])
                        value = sanitizeUrl(value);
                    if (SRCSET_ATTRS[lower])
                        value = sanitizeSrcset(value);
                    _this.buf.push(' ');
                    _this.buf.push(attrName);
                    _this.buf.push('="');
                    _this.buf.push(encodeEntities(value));
                    _this.buf.push('"');
                });
                this.buf.push('>');
            }
        };
        SanitizingHtmlSerializer.prototype.endElement = function (tagName) {
            tagName = tagName.toLowerCase();
            if (VALID_ELEMENTS.hasOwnProperty(tagName) && !VOID_ELEMENTS.hasOwnProperty(tagName)) {
                this.buf.push('</');
                this.buf.push(tagName);
                this.buf.push('>');
            }
        };
        SanitizingHtmlSerializer.prototype.chars = function (chars /** TODO #9100 */) { this.buf.push(encodeEntities(chars)); };
        return SanitizingHtmlSerializer;
    }());
    // Regular Expressions for parsing tags and attributes
    var SURROGATE_PAIR_REGEXP = /[\uD800-\uDBFF][\uDC00-\uDFFF]/g;
    // ! to ~ is the ASCII range.
    var NON_ALPHANUMERIC_REGEXP = /([^\#-~ |!])/g;
    /**
     * Escapes all potentially dangerous characters, so that the
     * resulting string can be safely inserted into attribute or
     * element text.
     * @param value
     * @returns {string} escaped text
     */
    function encodeEntities(value /** TODO #9100 */) {
        return value.replace(/&/g, '&amp;')
            .replace(SURROGATE_PAIR_REGEXP, function (match /** TODO #9100 */) {
            var hi = match.charCodeAt(0);
            var low = match.charCodeAt(1);
            return '&#' + (((hi - 0xD800) * 0x400) + (low - 0xDC00) + 0x10000) + ';';
        })
            .replace(NON_ALPHANUMERIC_REGEXP, function (match /** TODO #9100 */) { return '&#' + match.charCodeAt(0) + ';'; })
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;');
    }
    /**
     * When IE9-11 comes across an unknown namespaced attribute e.g. 'xlink:foo' it adds 'xmlns:ns1'
     * attribute to declare ns1 namespace and prefixes the attribute with 'ns1' (e.g. 'ns1:xlink:foo').
     *
     * This is undesirable since we don't want to allow any of these custom attributes. This method
     * strips them all.
     */
    function stripCustomNsAttrs(el) {
        DOM.attributeMap(el).forEach(function (_, attrName) {
            if (attrName === 'xmlns:ns1' || attrName.indexOf('ns1:') === 0) {
                DOM.removeAttribute(el, attrName);
            }
        });
        for (var _i = 0, _a = DOM.childNodesAsList(el); _i < _a.length; _i++) {
            var n = _a[_i];
            if (DOM.isElementNode(n))
                stripCustomNsAttrs(n);
        }
    }
    /**
     * Sanitizes the given unsafe, untrusted HTML fragment, and returns HTML text that is safe to add to
     * the DOM in a browser environment.
     */
    function sanitizeHtml(unsafeHtmlInput) {
        try {
            var containerEl = getInertElement();
            // Make sure unsafeHtml is actually a string (TypeScript types are not enforced at runtime).
            var unsafeHtml = unsafeHtmlInput ? String(unsafeHtmlInput) : '';
            // mXSS protection. Repeatedly parse the document to make sure it stabilizes, so that a browser
            // trying to auto-correct incorrect HTML cannot cause formerly inert HTML to become dangerous.
            var mXSSAttempts = 5;
            var parsedHtml = unsafeHtml;
            do {
                if (mXSSAttempts === 0) {
                    throw new Error('Failed to sanitize html because the input is unstable');
                }
                mXSSAttempts--;
                unsafeHtml = parsedHtml;
                DOM.setInnerHTML(containerEl, unsafeHtml);
                if (DOM.defaultDoc().documentMode) {
                    // strip custom-namespaced attributes on IE<=11
                    stripCustomNsAttrs(containerEl);
                }
                parsedHtml = DOM.getInnerHTML(containerEl);
            } while (unsafeHtml !== parsedHtml);
            var sanitizer = new SanitizingHtmlSerializer();
            var safeHtml = sanitizer.sanitizeChildren(DOM.getTemplateContent(containerEl) || containerEl);
            // Clear out the body element.
            var parent_1 = DOM.getTemplateContent(containerEl) || containerEl;
            for (var _i = 0, _a = DOM.childNodesAsList(parent_1); _i < _a.length; _i++) {
                var child = _a[_i];
                DOM.removeChild(parent_1, child);
            }
            if (_angular_core.isDevMode() && safeHtml !== unsafeHtmlInput) {
                DOM.log('WARNING: sanitizing HTML stripped some content (see http://g.co/ng/security#xss).');
            }
            return safeHtml;
        }
        catch (e) {
            // In case anything goes wrong, clear out inertElement to reset the entire DOM structure.
            inertElement = null;
            throw e;
        }
    }
    /**
     * Regular expression for safe style values.
     *
     * Quotes (" and ') are allowed, but a check must be done elsewhere to ensure they're balanced.
     *
     * ',' allows multiple values to be assigned to the same property (e.g. background-attachment or
     * font-family) and hence could allow multiple values to get injected, but that should pose no risk
     * of XSS.
     *
     * The function expression checks only for XSS safety, not for CSS validity.
     *
     * This regular expression was taken from the Closure sanitization library, and augmented for
     * transformation values.
     */
    var VALUES = '[-,."\'%_!# a-zA-Z0-9]+';
    var TRANSFORMATION_FNS = '(?:matrix|translate|scale|rotate|skew|perspective)(?:X|Y|3d)?';
    var COLOR_FNS = '(?:rgb|hsl)a?';
    var FN_ARGS = '\\([-0-9.%, a-zA-Z]+\\)';
    var SAFE_STYLE_VALUE = new RegExp("^(" + VALUES + "|(?:" + TRANSFORMATION_FNS + "|" + COLOR_FNS + ")" + FN_ARGS + ")$", 'g');
    /**
     * Matches a `url(...)` value with an arbitrary argument as long as it does
     * not contain parentheses.
     *
     * The URL value still needs to be sanitized separately.
     *
     * `url(...)` values are a very common use case, e.g. for `background-image`. With carefully crafted
     * CSS style rules, it is possible to construct an information leak with `url` values in CSS, e.g.
     * by observing whether scroll bars are displayed, or character ranges used by a font face
     * definition.
     *
     * Angular only allows binding CSS values (as opposed to entire CSS rules), so it is unlikely that
     * binding a URL value without further cooperation from the page will cause an information leak, and
     * if so, it is just a leak, not a full blown XSS vulnerability.
     *
     * Given the common use case, low likelihood of attack vector, and low impact of an attack, this
     * code is permissive and allows URLs that sanitize otherwise.
     */
    var URL_RE = /^url\(([^)]+)\)$/;
    /**
     * Checks that quotes (" and ') are properly balanced inside a string. Assumes
     * that neither escape (\) nor any other character that could result in
     * breaking out of a string parsing context are allowed;
     * see http://www.w3.org/TR/css3-syntax/#string-token-diagram.
     *
     * This code was taken from the Closure sanitization library.
     */
    function hasBalancedQuotes(value) {
        var outsideSingle = true;
        var outsideDouble = true;
        for (var i = 0; i < value.length; i++) {
            var c = value.charAt(i);
            if (c === '\'' && outsideDouble) {
                outsideSingle = !outsideSingle;
            }
            else if (c === '"' && outsideSingle) {
                outsideDouble = !outsideDouble;
            }
        }
        return outsideSingle && outsideDouble;
    }
    /**
     * Sanitizes the given untrusted CSS style property value (i.e. not an entire object, just a single
     * value) and returns a value that is safe to use in a browser environment.
     */
    function sanitizeStyle(value) {
        value = String(value).trim(); // Make sure it's actually a string.
        if (!value)
            return '';
        // Single url(...) values are supported, but only for URLs that sanitize cleanly. See above for
        // reasoning behind this.
        var urlMatch = URL_RE.exec(value);
        if ((urlMatch && sanitizeUrl(urlMatch[1]) === urlMatch[1]) ||
            value.match(SAFE_STYLE_VALUE) && hasBalancedQuotes(value)) {
            return value; // Safe style values.
        }
        if (_angular_core.isDevMode()) {
            getDOM().log("WARNING: sanitizing unsafe style value " + value + " (see http://g.co/ng/security#xss).");
        }
        return 'unsafe';
    }
    /**
     * DomSanitizationService helps preventing Cross Site Scripting Security bugs (XSS) by sanitizing
     * values to be safe to use in the different DOM contexts.
     *
     * For example, when binding a URL in an `<a [href]="someValue">` hyperlink, `someValue` will be
     * sanitized so that an attacker cannot inject e.g. a `javascript:` URL that would execute code on
     * the website.
     *
     * In specific situations, it might be necessary to disable sanitization, for example if the
     * application genuinely needs to produce a `javascript:` style link with a dynamic value in it.
     * Users can bypass security by constructing a value with one of the `bypassSecurityTrust...`
     * methods, and then binding to that value from the template.
     *
     * These situations should be very rare, and extraordinary care must be taken to avoid creating a
     * Cross Site Scripting (XSS) security bug!
     *
     * When using `bypassSecurityTrust...`, make sure to call the method as early as possible and as
     * close as possible to the source of the value, to make it easy to verify no security bug is
     * created by its use.
     *
     * It is not required (and not recommended) to bypass security if the value is safe, e.g. a URL that
     * does not start with a suspicious protocol, or an HTML snippet that does not contain dangerous
     * code. The sanitizer leaves safe values intact.
     *
     * @security Calling any of the `bypassSecurityTrust...` APIs disables Angular's built-in
     * sanitization for the value passed in. Carefully check and audit all values and code paths going
     * into this call. Make sure any user data is appropriately escaped for this security context.
     * For more detail, see the [Security Guide](http://g.co/ng/security).
     *
     * @stable
     */
    var DomSanitizationService = (function () {
        function DomSanitizationService() {
        }
        return DomSanitizationService;
    }());
    var DomSanitizationServiceImpl = (function (_super) {
        __extends(DomSanitizationServiceImpl, _super);
        function DomSanitizationServiceImpl() {
            _super.apply(this, arguments);
        }
        DomSanitizationServiceImpl.prototype.sanitize = function (ctx, value) {
            if (value == null)
                return null;
            switch (ctx) {
                case SecurityContext.NONE:
                    return value;
                case SecurityContext.HTML:
                    if (value instanceof SafeHtmlImpl)
                        return value.changingThisBreaksApplicationSecurity;
                    this.checkNotSafeValue(value, 'HTML');
                    return sanitizeHtml(String(value));
                case SecurityContext.STYLE:
                    if (value instanceof SafeStyleImpl)
                        return value.changingThisBreaksApplicationSecurity;
                    this.checkNotSafeValue(value, 'Style');
                    return sanitizeStyle(value);
                case SecurityContext.SCRIPT:
                    if (value instanceof SafeScriptImpl)
                        return value.changingThisBreaksApplicationSecurity;
                    this.checkNotSafeValue(value, 'Script');
                    throw new Error('unsafe value used in a script context');
                case SecurityContext.URL:
                    if (value instanceof SafeUrlImpl)
                        return value.changingThisBreaksApplicationSecurity;
                    this.checkNotSafeValue(value, 'URL');
                    return sanitizeUrl(String(value));
                case SecurityContext.RESOURCE_URL:
                    if (value instanceof SafeResourceUrlImpl) {
                        return value.changingThisBreaksApplicationSecurity;
                    }
                    this.checkNotSafeValue(value, 'ResourceURL');
                    throw new Error('unsafe value used in a resource URL context (see http://g.co/ng/security#xss)');
                default:
                    throw new Error("Unexpected SecurityContext " + ctx + " (see http://g.co/ng/security#xss)");
            }
        };
        DomSanitizationServiceImpl.prototype.checkNotSafeValue = function (value, expectedType) {
            if (value instanceof SafeValueImpl) {
                throw new Error(("Required a safe " + expectedType + ", got a " + value.getTypeName() + " ") +
                    "(see http://g.co/ng/security#xss)");
            }
        };
        DomSanitizationServiceImpl.prototype.bypassSecurityTrustHtml = function (value) { return new SafeHtmlImpl(value); };
        DomSanitizationServiceImpl.prototype.bypassSecurityTrustStyle = function (value) { return new SafeStyleImpl(value); };
        DomSanitizationServiceImpl.prototype.bypassSecurityTrustScript = function (value) { return new SafeScriptImpl(value); };
        DomSanitizationServiceImpl.prototype.bypassSecurityTrustUrl = function (value) { return new SafeUrlImpl(value); };
        DomSanitizationServiceImpl.prototype.bypassSecurityTrustResourceUrl = function (value) {
            return new SafeResourceUrlImpl(value);
        };
        return DomSanitizationServiceImpl;
    }(DomSanitizationService));
    /** @nocollapse */
    DomSanitizationServiceImpl.decorators = [
        { type: _angular_core.Injectable },
    ];
    var SafeValueImpl = (function () {
        function SafeValueImpl(changingThisBreaksApplicationSecurity) {
            this.changingThisBreaksApplicationSecurity = changingThisBreaksApplicationSecurity;
            // empty
        }
        SafeValueImpl.prototype.toString = function () {
            return ("SafeValue must use [property]=binding: " + this.changingThisBreaksApplicationSecurity) +
                " (see http://g.co/ng/security#xss)";
        };
        return SafeValueImpl;
    }());
    var SafeHtmlImpl = (function (_super) {
        __extends(SafeHtmlImpl, _super);
        function SafeHtmlImpl() {
            _super.apply(this, arguments);
        }
        SafeHtmlImpl.prototype.getTypeName = function () { return 'HTML'; };
        return SafeHtmlImpl;
    }(SafeValueImpl));
    var SafeStyleImpl = (function (_super) {
        __extends(SafeStyleImpl, _super);
        function SafeStyleImpl() {
            _super.apply(this, arguments);
        }
        SafeStyleImpl.prototype.getTypeName = function () { return 'Style'; };
        return SafeStyleImpl;
    }(SafeValueImpl));
    var SafeScriptImpl = (function (_super) {
        __extends(SafeScriptImpl, _super);
        function SafeScriptImpl() {
            _super.apply(this, arguments);
        }
        SafeScriptImpl.prototype.getTypeName = function () { return 'Script'; };
        return SafeScriptImpl;
    }(SafeValueImpl));
    var SafeUrlImpl = (function (_super) {
        __extends(SafeUrlImpl, _super);
        function SafeUrlImpl() {
            _super.apply(this, arguments);
        }
        SafeUrlImpl.prototype.getTypeName = function () { return 'URL'; };
        return SafeUrlImpl;
    }(SafeValueImpl));
    var SafeResourceUrlImpl = (function (_super) {
        __extends(SafeResourceUrlImpl, _super);
        function SafeResourceUrlImpl() {
            _super.apply(this, arguments);
        }
        SafeResourceUrlImpl.prototype.getTypeName = function () { return 'ResourceURL'; };
        return SafeResourceUrlImpl;
    }(SafeValueImpl));
    var CAMEL_CASE_REGEXP = /([A-Z])/g;
    var DASH_CASE_REGEXP = /-([a-z])/g;
    function camelCaseToDashCase(input) {
        return StringWrapper.replaceAllMapped(input, CAMEL_CASE_REGEXP, function (m /** TODO #9100 */) { return '-' + m[1].toLowerCase(); });
    }
    function dashCaseToCamelCase(input) {
        return StringWrapper.replaceAllMapped(input, DASH_CASE_REGEXP, function (m /** TODO #9100 */) { return m[1].toUpperCase(); });
    }
    var WebAnimationsPlayer = (function () {
        function WebAnimationsPlayer(_player, totalTime) {
            var _this = this;
            this._player = _player;
            this.totalTime = totalTime;
            this._subscriptions = [];
            this._finished = false;
            this.parentPlayer = null;
            // this is required to make the player startable at a later time
            this.reset();
            this._player.onfinish = function () { return _this._onFinish(); };
        }
        WebAnimationsPlayer.prototype._onFinish = function () {
            if (!this._finished) {
                this._finished = true;
                if (!isPresent(this.parentPlayer)) {
                    this.destroy();
                }
                this._subscriptions.forEach(function (fn) { return fn(); });
                this._subscriptions = [];
            }
        };
        WebAnimationsPlayer.prototype.onDone = function (fn) { this._subscriptions.push(fn); };
        WebAnimationsPlayer.prototype.play = function () { this._player.play(); };
        WebAnimationsPlayer.prototype.pause = function () { this._player.pause(); };
        WebAnimationsPlayer.prototype.finish = function () {
            this._onFinish();
            this._player.finish();
        };
        WebAnimationsPlayer.prototype.reset = function () { this._player.cancel(); };
        WebAnimationsPlayer.prototype.restart = function () {
            this.reset();
            this.play();
        };
        WebAnimationsPlayer.prototype.destroy = function () {
            this.reset();
            this._onFinish();
        };
        WebAnimationsPlayer.prototype.setPosition = function (p /** TODO #9100 */) { this._player.currentTime = p * this.totalTime; };
        WebAnimationsPlayer.prototype.getPosition = function () { return this._player.currentTime / this.totalTime; };
        return WebAnimationsPlayer;
    }());
    var WebAnimationsDriver = (function () {
        function WebAnimationsDriver() {
        }
        WebAnimationsDriver.prototype.animate = function (element, startingStyles, keyframes, duration, delay, easing) {
            var anyElm = element;
            var formattedSteps = [];
            var startingStyleLookup = {};
            if (isPresent(startingStyles) && startingStyles.styles.length > 0) {
                startingStyleLookup = _populateStyles(anyElm, startingStyles, {});
                startingStyleLookup['offset'] = 0;
                formattedSteps.push(startingStyleLookup);
            }
            keyframes.forEach(function (keyframe) {
                var data = _populateStyles(anyElm, keyframe.styles, startingStyleLookup);
                data['offset'] = keyframe.offset;
                formattedSteps.push(data);
            });
            // this is a special case when only styles are applied as an
            // animation. When this occurs we want to animate from start to
            // end with the same values. Removing the offset and having only
            // start/end values is suitable enough for the web-animations API
            if (formattedSteps.length == 1) {
                var start = formattedSteps[0];
                start['offset'] = null;
                formattedSteps = [start, start];
            }
            var playerOptions = {
                'duration': duration,
                'delay': delay,
                'easing': easing,
                'fill': 'both' // we use `both` because it allows for styling at 0% to work with `delay`
            };
            var player = this._triggerWebAnimation(anyElm, formattedSteps, playerOptions);
            return new WebAnimationsPlayer(player, duration);
        };
        /** @internal */
        WebAnimationsDriver.prototype._triggerWebAnimation = function (elm, keyframes, options) {
            return elm.animate(keyframes, options);
        };
        return WebAnimationsDriver;
    }());
    function _populateStyles(element, styles, defaultStyles) {
        var data = {};
        styles.styles.forEach(function (entry) {
            StringMapWrapper.forEach(entry, function (val, prop) {
                var formattedProp = dashCaseToCamelCase(prop);
                data[formattedProp] = val == _angular_core.AUTO_STYLE ?
                    _computeStyle(element, formattedProp) :
                    val.toString() + _resolveStyleUnit(val, prop, formattedProp);
            });
        });
        StringMapWrapper.forEach(defaultStyles, function (value, prop) {
            if (!isPresent(data[prop])) {
                data[prop] = value;
            }
        });
        return data;
    }
    function _resolveStyleUnit(val, userProvidedProp, formattedProp) {
        var unit = '';
        if (_isPixelDimensionStyle(formattedProp) && val != 0 && val != '0') {
            if (isNumber(val)) {
                unit = 'px';
            }
            else if (_findDimensionalSuffix(val.toString()).length == 0) {
                throw new _angular_core.BaseException('Please provide a CSS unit value for ' + userProvidedProp + ':' + val);
            }
        }
        return unit;
    }
    var _$0 = 48;
    var _$9 = 57;
    var _$PERIOD = 46;
    function _findDimensionalSuffix(value) {
        for (var i = 0; i < value.length; i++) {
            var c = StringWrapper.charCodeAt(value, i);
            if ((c >= _$0 && c <= _$9) || c == _$PERIOD)
                continue;
            return value.substring(i, value.length);
        }
        return '';
    }
    function _isPixelDimensionStyle(prop) {
        switch (prop) {
            case 'width':
            case 'height':
            case 'minWidth':
            case 'minHeight':
            case 'maxWidth':
            case 'maxHeight':
            case 'left':
            case 'top':
            case 'bottom':
            case 'right':
            case 'fontSize':
            case 'outlineWidth':
            case 'outlineOffset':
            case 'paddingTop':
            case 'paddingLeft':
            case 'paddingBottom':
            case 'paddingRight':
            case 'marginTop':
            case 'marginLeft':
            case 'marginBottom':
            case 'marginRight':
            case 'borderRadius':
            case 'borderWidth':
            case 'borderTopWidth':
            case 'borderLeftWidth':
            case 'borderRightWidth':
            case 'borderBottomWidth':
            case 'textIndent':
                return true;
            default:
                return false;
        }
    }
    function _computeStyle(element, prop) {
        return getDOM().getComputedStyle(element)[prop];
    }
    /**
     * Provides DOM operations in any browser environment.
     */
    var GenericBrowserDomAdapter = (function (_super) {
        __extends(GenericBrowserDomAdapter, _super);
        function GenericBrowserDomAdapter() {
            var _this = this;
            _super.call(this);
            this._animationPrefix = null;
            this._transitionEnd = null;
            try {
                var element = this.createElement('div', this.defaultDoc());
                if (isPresent(this.getStyle(element, 'animationName'))) {
                    this._animationPrefix = '';
                }
                else {
                    var domPrefixes = ['Webkit', 'Moz', 'O', 'ms'];
                    for (var i = 0; i < domPrefixes.length; i++) {
                        if (isPresent(this.getStyle(element, domPrefixes[i] + 'AnimationName'))) {
                            this._animationPrefix = '-' + domPrefixes[i].toLowerCase() + '-';
                            break;
                        }
                    }
                }
                var transEndEventNames = {
                    WebkitTransition: 'webkitTransitionEnd',
                    MozTransition: 'transitionend',
                    OTransition: 'oTransitionEnd otransitionend',
                    transition: 'transitionend'
                };
                StringMapWrapper.forEach(transEndEventNames, function (value, key) {
                    if (isPresent(_this.getStyle(element, key))) {
                        _this._transitionEnd = value;
                    }
                });
            }
            catch (e) {
                this._animationPrefix = null;
                this._transitionEnd = null;
            }
        }
        GenericBrowserDomAdapter.prototype.getDistributedNodes = function (el) { return el.getDistributedNodes(); };
        GenericBrowserDomAdapter.prototype.resolveAndSetHref = function (el, baseUrl, href) {
            el.href = href == null ? baseUrl : baseUrl + '/../' + href;
        };
        GenericBrowserDomAdapter.prototype.supportsDOMEvents = function () { return true; };
        GenericBrowserDomAdapter.prototype.supportsNativeShadowDOM = function () {
            return isFunction(this.defaultDoc().body.createShadowRoot);
        };
        GenericBrowserDomAdapter.prototype.getAnimationPrefix = function () {
            return isPresent(this._animationPrefix) ? this._animationPrefix : '';
        };
        GenericBrowserDomAdapter.prototype.getTransitionEnd = function () { return isPresent(this._transitionEnd) ? this._transitionEnd : ''; };
        GenericBrowserDomAdapter.prototype.supportsAnimation = function () {
            return isPresent(this._animationPrefix) && isPresent(this._transitionEnd);
        };
        return GenericBrowserDomAdapter;
    }(DomAdapter));
    var _attrToPropMap = {
        'class': 'className',
        'innerHtml': 'innerHTML',
        'readonly': 'readOnly',
        'tabindex': 'tabIndex'
    };
    var DOM_KEY_LOCATION_NUMPAD = 3;
    // Map to convert some key or keyIdentifier values to what will be returned by getEventKey
    var _keyMap = {
        // The following values are here for cross-browser compatibility and to match the W3C standard
        // cf http://www.w3.org/TR/DOM-Level-3-Events-key/
        '\b': 'Backspace',
        '\t': 'Tab',
        '\x7F': 'Delete',
        '\x1B': 'Escape',
        'Del': 'Delete',
        'Esc': 'Escape',
        'Left': 'ArrowLeft',
        'Right': 'ArrowRight',
        'Up': 'ArrowUp',
        'Down': 'ArrowDown',
        'Menu': 'ContextMenu',
        'Scroll': 'ScrollLock',
        'Win': 'OS'
    };
    // There is a bug in Chrome for numeric keypad keys:
    // https://code.google.com/p/chromium/issues/detail?id=155654
    // 1, 2, 3 ... are reported as A, B, C ...
    var _chromeNumKeyPadMap = {
        'A': '1',
        'B': '2',
        'C': '3',
        'D': '4',
        'E': '5',
        'F': '6',
        'G': '7',
        'H': '8',
        'I': '9',
        'J': '*',
        'K': '+',
        'M': '-',
        'N': '.',
        'O': '/',
        '\x60': '0',
        '\x90': 'NumLock'
    };
    /**
     * A `DomAdapter` powered by full browser DOM APIs.
     */
    /* tslint:disable:requireParameterType */
    var BrowserDomAdapter = (function (_super) {
        __extends(BrowserDomAdapter, _super);
        function BrowserDomAdapter() {
            _super.apply(this, arguments);
        }
        BrowserDomAdapter.prototype.parse = function (templateHtml) { throw new Error('parse not implemented'); };
        BrowserDomAdapter.makeCurrent = function () { setRootDomAdapter(new BrowserDomAdapter()); };
        BrowserDomAdapter.prototype.hasProperty = function (element /** TODO #9100 */, name) { return name in element; };
        BrowserDomAdapter.prototype.setProperty = function (el, name, value) { el[name] = value; };
        BrowserDomAdapter.prototype.getProperty = function (el, name) { return el[name]; };
        BrowserDomAdapter.prototype.invoke = function (el, methodName, args) {
            el[methodName].apply(el, args);
        };
        // TODO(tbosch): move this into a separate environment class once we have it
        BrowserDomAdapter.prototype.logError = function (error /** TODO #9100 */) {
            if (window.console.error) {
                window.console.error(error);
            }
            else {
                window.console.log(error);
            }
        };
        BrowserDomAdapter.prototype.log = function (error /** TODO #9100 */) { window.console.log(error); };
        BrowserDomAdapter.prototype.logGroup = function (error /** TODO #9100 */) {
            if (window.console.group) {
                window.console.group(error);
                this.logError(error);
            }
            else {
                window.console.log(error);
            }
        };
        BrowserDomAdapter.prototype.logGroupEnd = function () {
            if (window.console.groupEnd) {
                window.console.groupEnd();
            }
        };
        Object.defineProperty(BrowserDomAdapter.prototype, "attrToPropMap", {
            get: function () { return _attrToPropMap; },
            enumerable: true,
            configurable: true
        });
        BrowserDomAdapter.prototype.query = function (selector) { return document.querySelector(selector); };
        BrowserDomAdapter.prototype.querySelector = function (el /** TODO #9100 */, selector) {
            return el.querySelector(selector);
        };
        BrowserDomAdapter.prototype.querySelectorAll = function (el /** TODO #9100 */, selector) {
            return el.querySelectorAll(selector);
        };
        BrowserDomAdapter.prototype.on = function (el /** TODO #9100 */, evt /** TODO #9100 */, listener /** TODO #9100 */) {
            el.addEventListener(evt, listener, false);
        };
        BrowserDomAdapter.prototype.onAndCancel = function (el /** TODO #9100 */, evt /** TODO #9100 */, listener /** TODO #9100 */) {
            el.addEventListener(evt, listener, false);
            // Needed to follow Dart's subscription semantic, until fix of
            // https://code.google.com/p/dart/issues/detail?id=17406
            return function () { el.removeEventListener(evt, listener, false); };
        };
        BrowserDomAdapter.prototype.dispatchEvent = function (el /** TODO #9100 */, evt /** TODO #9100 */) { el.dispatchEvent(evt); };
        BrowserDomAdapter.prototype.createMouseEvent = function (eventType) {
            var evt = document.createEvent('MouseEvent');
            evt.initEvent(eventType, true, true);
            return evt;
        };
        BrowserDomAdapter.prototype.createEvent = function (eventType /** TODO #9100 */) {
            var evt = document.createEvent('Event');
            evt.initEvent(eventType, true, true);
            return evt;
        };
        BrowserDomAdapter.prototype.preventDefault = function (evt) {
            evt.preventDefault();
            evt.returnValue = false;
        };
        BrowserDomAdapter.prototype.isPrevented = function (evt) {
            return evt.defaultPrevented || isPresent(evt.returnValue) && !evt.returnValue;
        };
        BrowserDomAdapter.prototype.getInnerHTML = function (el /** TODO #9100 */) { return el.innerHTML; };
        BrowserDomAdapter.prototype.getTemplateContent = function (el /** TODO #9100 */) {
            return 'content' in el && el instanceof HTMLTemplateElement ? el.content : null;
        };
        BrowserDomAdapter.prototype.getOuterHTML = function (el /** TODO #9100 */) { return el.outerHTML; };
        BrowserDomAdapter.prototype.nodeName = function (node) { return node.nodeName; };
        BrowserDomAdapter.prototype.nodeValue = function (node) { return node.nodeValue; };
        BrowserDomAdapter.prototype.type = function (node) { return node.type; };
        BrowserDomAdapter.prototype.content = function (node) {
            if (this.hasProperty(node, 'content')) {
                return node.content;
            }
            else {
                return node;
            }
        };
        BrowserDomAdapter.prototype.firstChild = function (el /** TODO #9100 */) { return el.firstChild; };
        BrowserDomAdapter.prototype.nextSibling = function (el /** TODO #9100 */) { return el.nextSibling; };
        BrowserDomAdapter.prototype.parentElement = function (el /** TODO #9100 */) { return el.parentNode; };
        BrowserDomAdapter.prototype.childNodes = function (el /** TODO #9100 */) { return el.childNodes; };
        BrowserDomAdapter.prototype.childNodesAsList = function (el /** TODO #9100 */) {
            var childNodes = el.childNodes;
            var res = ListWrapper.createFixedSize(childNodes.length);
            for (var i = 0; i < childNodes.length; i++) {
                res[i] = childNodes[i];
            }
            return res;
        };
        BrowserDomAdapter.prototype.clearNodes = function (el /** TODO #9100 */) {
            while (el.firstChild) {
                el.removeChild(el.firstChild);
            }
        };
        BrowserDomAdapter.prototype.appendChild = function (el /** TODO #9100 */, node /** TODO #9100 */) { el.appendChild(node); };
        BrowserDomAdapter.prototype.removeChild = function (el /** TODO #9100 */, node /** TODO #9100 */) { el.removeChild(node); };
        BrowserDomAdapter.prototype.replaceChild = function (el, newChild /** TODO #9100 */, oldChild /** TODO #9100 */) {
            el.replaceChild(newChild, oldChild);
        };
        BrowserDomAdapter.prototype.remove = function (node /** TODO #9100 */) {
            if (node.parentNode) {
                node.parentNode.removeChild(node);
            }
            return node;
        };
        BrowserDomAdapter.prototype.insertBefore = function (el /** TODO #9100 */, node /** TODO #9100 */) {
            el.parentNode.insertBefore(node, el);
        };
        BrowserDomAdapter.prototype.insertAllBefore = function (el /** TODO #9100 */, nodes /** TODO #9100 */) {
            nodes.forEach(function (n /** TODO #9100 */) { return el.parentNode.insertBefore(n, el); });
        };
        BrowserDomAdapter.prototype.insertAfter = function (el /** TODO #9100 */, node /** TODO #9100 */) {
            el.parentNode.insertBefore(node, el.nextSibling);
        };
        BrowserDomAdapter.prototype.setInnerHTML = function (el /** TODO #9100 */, value /** TODO #9100 */) { el.innerHTML = value; };
        BrowserDomAdapter.prototype.getText = function (el /** TODO #9100 */) { return el.textContent; };
        // TODO(vicb): removed Element type because it does not support StyleElement
        BrowserDomAdapter.prototype.setText = function (el /** TODO #9100 */, value) { el.textContent = value; };
        BrowserDomAdapter.prototype.getValue = function (el /** TODO #9100 */) { return el.value; };
        BrowserDomAdapter.prototype.setValue = function (el /** TODO #9100 */, value) { el.value = value; };
        BrowserDomAdapter.prototype.getChecked = function (el /** TODO #9100 */) { return el.checked; };
        BrowserDomAdapter.prototype.setChecked = function (el /** TODO #9100 */, value) { el.checked = value; };
        BrowserDomAdapter.prototype.createComment = function (text) { return document.createComment(text); };
        BrowserDomAdapter.prototype.createTemplate = function (html /** TODO #9100 */) {
            var t = document.createElement('template');
            t.innerHTML = html;
            return t;
        };
        BrowserDomAdapter.prototype.createElement = function (tagName /* TODO #9100 */, doc) {
            if (doc === void 0) { doc = document; }
            return doc.createElement(tagName);
        };
        BrowserDomAdapter.prototype.createElementNS = function (ns /* TODO #9100 */, tagName /* TODO #9100 */, doc) {
            if (doc === void 0) { doc = document; }
            return doc.createElementNS(ns, tagName);
        };
        BrowserDomAdapter.prototype.createTextNode = function (text, doc) {
            if (doc === void 0) { doc = document; }
            return doc.createTextNode(text);
        };
        BrowserDomAdapter.prototype.createScriptTag = function (attrName, attrValue, doc) {
            if (doc === void 0) { doc = document; }
            var el = doc.createElement('SCRIPT');
            el.setAttribute(attrName, attrValue);
            return el;
        };
        BrowserDomAdapter.prototype.createStyleElement = function (css, doc) {
            if (doc === void 0) { doc = document; }
            var style = doc.createElement('style');
            this.appendChild(style, this.createTextNode(css));
            return style;
        };
        BrowserDomAdapter.prototype.createShadowRoot = function (el) { return el.createShadowRoot(); };
        BrowserDomAdapter.prototype.getShadowRoot = function (el) { return el.shadowRoot; };
        BrowserDomAdapter.prototype.getHost = function (el) { return el.host; };
        BrowserDomAdapter.prototype.clone = function (node) { return node.cloneNode(true); };
        BrowserDomAdapter.prototype.getElementsByClassName = function (element /** TODO #9100 */, name) {
            return element.getElementsByClassName(name);
        };
        BrowserDomAdapter.prototype.getElementsByTagName = function (element /** TODO #9100 */, name) {
            return element.getElementsByTagName(name);
        };
        BrowserDomAdapter.prototype.classList = function (element /** TODO #9100 */) {
            return Array.prototype.slice.call(element.classList, 0);
        };
        BrowserDomAdapter.prototype.addClass = function (element /** TODO #9100 */, className) { element.classList.add(className); };
        BrowserDomAdapter.prototype.removeClass = function (element /** TODO #9100 */, className) {
            element.classList.remove(className);
        };
        BrowserDomAdapter.prototype.hasClass = function (element /** TODO #9100 */, className) {
            return element.classList.contains(className);
        };
        BrowserDomAdapter.prototype.setStyle = function (element /** TODO #9100 */, styleName, styleValue) {
            element.style[styleName] = styleValue;
        };
        BrowserDomAdapter.prototype.removeStyle = function (element /** TODO #9100 */, stylename) {
            element.style[stylename] = null;
        };
        BrowserDomAdapter.prototype.getStyle = function (element /** TODO #9100 */, stylename) {
            return element.style[stylename];
        };
        BrowserDomAdapter.prototype.hasStyle = function (element /** TODO #9100 */, styleName, styleValue) {
            if (styleValue === void 0) { styleValue = null; }
            var value = this.getStyle(element, styleName) || '';
            return styleValue ? value == styleValue : value.length > 0;
        };
        BrowserDomAdapter.prototype.tagName = function (element /** TODO #9100 */) { return element.tagName; };
        BrowserDomAdapter.prototype.attributeMap = function (element /** TODO #9100 */) {
            var res = new Map();
            var elAttrs = element.attributes;
            for (var i = 0; i < elAttrs.length; i++) {
                var attrib = elAttrs[i];
                res.set(attrib.name, attrib.value);
            }
            return res;
        };
        BrowserDomAdapter.prototype.hasAttribute = function (element /** TODO #9100 */, attribute) {
            return element.hasAttribute(attribute);
        };
        BrowserDomAdapter.prototype.hasAttributeNS = function (element /** TODO #9100 */, ns, attribute) {
            return element.hasAttributeNS(ns, attribute);
        };
        BrowserDomAdapter.prototype.getAttribute = function (element /** TODO #9100 */, attribute) {
            return element.getAttribute(attribute);
        };
        BrowserDomAdapter.prototype.getAttributeNS = function (element /** TODO #9100 */, ns, name) {
            return element.getAttributeNS(ns, name);
        };
        BrowserDomAdapter.prototype.setAttribute = function (element /** TODO #9100 */, name, value) {
            element.setAttribute(name, value);
        };
        BrowserDomAdapter.prototype.setAttributeNS = function (element /** TODO #9100 */, ns, name, value) {
            element.setAttributeNS(ns, name, value);
        };
        BrowserDomAdapter.prototype.removeAttribute = function (element /** TODO #9100 */, attribute) {
            element.removeAttribute(attribute);
        };
        BrowserDomAdapter.prototype.removeAttributeNS = function (element /** TODO #9100 */, ns, name) {
            element.removeAttributeNS(ns, name);
        };
        BrowserDomAdapter.prototype.templateAwareRoot = function (el /** TODO #9100 */) {
            return this.isTemplateElement(el) ? this.content(el) : el;
        };
        BrowserDomAdapter.prototype.createHtmlDocument = function () {
            return document.implementation.createHTMLDocument('fakeTitle');
        };
        BrowserDomAdapter.prototype.defaultDoc = function () { return document; };
        BrowserDomAdapter.prototype.getBoundingClientRect = function (el /** TODO #9100 */) {
            try {
                return el.getBoundingClientRect();
            }
            catch (e) {
                return { top: 0, bottom: 0, left: 0, right: 0, width: 0, height: 0 };
            }
        };
        BrowserDomAdapter.prototype.getTitle = function () { return document.title; };
        BrowserDomAdapter.prototype.setTitle = function (newTitle) { document.title = newTitle || ''; };
        BrowserDomAdapter.prototype.elementMatches = function (n /** TODO #9100 */, selector) {
            var matches = false;
            if (n instanceof HTMLElement) {
                if (n.matches) {
                    matches = n.matches(selector);
                }
                else if (n.msMatchesSelector) {
                    matches = n.msMatchesSelector(selector);
                }
                else if (n.webkitMatchesSelector) {
                    matches = n.webkitMatchesSelector(selector);
                }
            }
            return matches;
        };
        BrowserDomAdapter.prototype.isTemplateElement = function (el) {
            return el instanceof HTMLElement && el.nodeName == 'TEMPLATE';
        };
        BrowserDomAdapter.prototype.isTextNode = function (node) { return node.nodeType === Node.TEXT_NODE; };
        BrowserDomAdapter.prototype.isCommentNode = function (node) { return node.nodeType === Node.COMMENT_NODE; };
        BrowserDomAdapter.prototype.isElementNode = function (node) { return node.nodeType === Node.ELEMENT_NODE; };
        BrowserDomAdapter.prototype.hasShadowRoot = function (node /** TODO #9100 */) {
            return node instanceof HTMLElement && isPresent(node.shadowRoot);
        };
        BrowserDomAdapter.prototype.isShadowRoot = function (node /** TODO #9100 */) { return node instanceof DocumentFragment; };
        BrowserDomAdapter.prototype.importIntoDoc = function (node) {
            var toImport = node;
            if (this.isTemplateElement(node)) {
                toImport = this.content(node);
            }
            return document.importNode(toImport, true);
        };
        BrowserDomAdapter.prototype.adoptNode = function (node) { return document.adoptNode(node); };
        BrowserDomAdapter.prototype.getHref = function (el) { return el.href; };
        BrowserDomAdapter.prototype.getEventKey = function (event /** TODO #9100 */) {
            var key = event.key;
            if (isBlank(key)) {
                key = event.keyIdentifier;
                // keyIdentifier is defined in the old draft of DOM Level 3 Events implemented by Chrome and
                // Safari
                // cf
                // http://www.w3.org/TR/2007/WD-DOM-Level-3-Events-20071221/events.html#Events-KeyboardEvents-Interfaces
                if (isBlank(key)) {
                    return 'Unidentified';
                }
                if (key.startsWith('U+')) {
                    key = String.fromCharCode(parseInt(key.substring(2), 16));
                    if (event.location === DOM_KEY_LOCATION_NUMPAD && _chromeNumKeyPadMap.hasOwnProperty(key)) {
                        // There is a bug in Chrome for numeric keypad keys:
                        // https://code.google.com/p/chromium/issues/detail?id=155654
                        // 1, 2, 3 ... are reported as A, B, C ...
                        key = _chromeNumKeyPadMap[key];
                    }
                }
            }
            if (_keyMap.hasOwnProperty(key)) {
                key = _keyMap[key];
            }
            return key;
        };
        BrowserDomAdapter.prototype.getGlobalEventTarget = function (target) {
            if (target == 'window') {
                return window;
            }
            else if (target == 'document') {
                return document;
            }
            else if (target == 'body') {
                return document.body;
            }
        };
        BrowserDomAdapter.prototype.getHistory = function () { return window.history; };
        BrowserDomAdapter.prototype.getLocation = function () { return window.location; };
        BrowserDomAdapter.prototype.getBaseHref = function () {
            var href = getBaseElementHref();
            if (isBlank(href)) {
                return null;
            }
            return relativePath(href);
        };
        BrowserDomAdapter.prototype.resetBaseElement = function () { baseElement = null; };
        BrowserDomAdapter.prototype.getUserAgent = function () { return window.navigator.userAgent; };
        BrowserDomAdapter.prototype.setData = function (element /** TODO #9100 */, name, value) {
            this.setAttribute(element, 'data-' + name, value);
        };
        BrowserDomAdapter.prototype.getData = function (element /** TODO #9100 */, name) {
            return this.getAttribute(element, 'data-' + name);
        };
        BrowserDomAdapter.prototype.getComputedStyle = function (element /** TODO #9100 */) { return getComputedStyle(element); };
        // TODO(tbosch): move this into a separate environment class once we have it
        BrowserDomAdapter.prototype.setGlobalVar = function (path, value) { setValueOnPath(global$1, path, value); };
        BrowserDomAdapter.prototype.requestAnimationFrame = function (callback /** TODO #9100 */) {
            return window.requestAnimationFrame(callback);
        };
        BrowserDomAdapter.prototype.cancelAnimationFrame = function (id) { window.cancelAnimationFrame(id); };
        BrowserDomAdapter.prototype.supportsWebAnimation = function () {
            return isFunction(document.body['animate']);
        };
        BrowserDomAdapter.prototype.performanceNow = function () {
            // performance.now() is not available in all browsers, see
            // http://caniuse.com/#search=performance.now
            if (isPresent(window.performance) && isPresent(window.performance.now)) {
                return window.performance.now();
            }
            else {
                return DateWrapper.toMillis(DateWrapper.now());
            }
        };
        BrowserDomAdapter.prototype.supportsCookies = function () { return true; };
        BrowserDomAdapter.prototype.getCookie = function (name) { return parseCookieValue(document.cookie, name); };
        BrowserDomAdapter.prototype.setCookie = function (name, value) {
            // document.cookie is magical, assigning into it assigns/overrides one cookie value, but does
            // not clear other cookies.
            document.cookie = encodeURIComponent(name) + '=' + encodeURIComponent(value);
        };
        return BrowserDomAdapter;
    }(GenericBrowserDomAdapter));
    var baseElement = null;
    function getBaseElementHref() {
        if (isBlank(baseElement)) {
            baseElement = document.querySelector('base');
            if (isBlank(baseElement)) {
                return null;
            }
        }
        return baseElement.getAttribute('href');
    }
    // based on urlUtils.js in AngularJS 1
    var urlParsingNode = null;
    function relativePath(url /** TODO #9100 */) {
        if (isBlank(urlParsingNode)) {
            urlParsingNode = document.createElement('a');
        }
        urlParsingNode.setAttribute('href', url);
        return (urlParsingNode.pathname.charAt(0) === '/') ? urlParsingNode.pathname :
            '/' + urlParsingNode.pathname;
    }
    function parseCookieValue(cookie, name) {
        name = encodeURIComponent(name);
        var cookies = cookie.split(';');
        for (var _i = 0, cookies_1 = cookies; _i < cookies_1.length; _i++) {
            var cookie_1 = cookies_1[_i];
            var _a = cookie_1.split('=', 2), key = _a[0], value = _a[1];
            if (key.trim() === name) {
                return decodeURIComponent(value);
            }
        }
        return null;
    }
    var PublicTestability = (function () {
        function PublicTestability(testability) {
            this._testability = testability;
        }
        PublicTestability.prototype.isStable = function () { return this._testability.isStable(); };
        PublicTestability.prototype.whenStable = function (callback) { this._testability.whenStable(callback); };
        PublicTestability.prototype.findBindings = function (using, provider, exactMatch) {
            return this.findProviders(using, provider, exactMatch);
        };
        PublicTestability.prototype.findProviders = function (using, provider, exactMatch) {
            return this._testability.findBindings(using, provider, exactMatch);
        };
        return PublicTestability;
    }());
    var BrowserGetTestability = (function () {
        function BrowserGetTestability() {
        }
        BrowserGetTestability.init = function () { _angular_core.setTestabilityGetter(new BrowserGetTestability()); };
        BrowserGetTestability.prototype.addToWindow = function (registry) {
            global$1.getAngularTestability = function (elem, findInAncestors) {
                if (findInAncestors === void 0) { findInAncestors = true; }
                var testability = registry.findTestabilityInTree(elem, findInAncestors);
                if (testability == null) {
                    throw new Error('Could not find testability for element.');
                }
                return new PublicTestability(testability);
            };
            global$1.getAllAngularTestabilities = function () {
                var testabilities = registry.getAllTestabilities();
                return testabilities.map(function (testability) { return new PublicTestability(testability); });
            };
            global$1.getAllAngularRootElements = function () { return registry.getAllRootElements(); };
            var whenAllStable = function (callback /** TODO #9100 */) {
                var testabilities = global$1.getAllAngularTestabilities();
                var count = testabilities.length;
                var didWork = false;
                var decrement = function (didWork_ /** TODO #9100 */) {
                    didWork = didWork || didWork_;
                    count--;
                    if (count == 0) {
                        callback(didWork);
                    }
                };
                testabilities.forEach(function (testability /** TODO #9100 */) {
                    testability.whenStable(decrement);
                });
            };
            if (!global$1.frameworkStabilizers) {
                global$1.frameworkStabilizers = ListWrapper.createGrowableSize(0);
            }
            global$1.frameworkStabilizers.push(whenAllStable);
        };
        BrowserGetTestability.prototype.findTestabilityInTree = function (registry, elem, findInAncestors) {
            if (elem == null) {
                return null;
            }
            var t = registry.getTestability(elem);
            if (isPresent(t)) {
                return t;
            }
            else if (!findInAncestors) {
                return null;
            }
            if (getDOM().isShadowRoot(elem)) {
                return this.findTestabilityInTree(registry, getDOM().getHost(elem), true);
            }
            return this.findTestabilityInTree(registry, getDOM().parentElement(elem), true);
        };
        return BrowserGetTestability;
    }());
    var SharedStylesHost = (function () {
        function SharedStylesHost() {
            /** @internal */
            this._styles = [];
            /** @internal */
            this._stylesSet = new Set();
        }
        SharedStylesHost.prototype.addStyles = function (styles) {
            var _this = this;
            var additions = [];
            styles.forEach(function (style) {
                if (!SetWrapper.has(_this._stylesSet, style)) {
                    _this._stylesSet.add(style);
                    _this._styles.push(style);
                    additions.push(style);
                }
            });
            this.onStylesAdded(additions);
        };
        SharedStylesHost.prototype.onStylesAdded = function (additions) { };
        SharedStylesHost.prototype.getAllStyles = function () { return this._styles; };
        return SharedStylesHost;
    }());
    /** @nocollapse */
    SharedStylesHost.decorators = [
        { type: _angular_core.Injectable },
    ];
    /** @nocollapse */
    SharedStylesHost.ctorParameters = [];
    var DomSharedStylesHost = (function (_super) {
        __extends(DomSharedStylesHost, _super);
        function DomSharedStylesHost(doc) {
            _super.call(this);
            this._hostNodes = new Set();
            this._hostNodes.add(doc.head);
        }
        /** @internal */
        DomSharedStylesHost.prototype._addStylesToHost = function (styles, host) {
            for (var i = 0; i < styles.length; i++) {
                var style = styles[i];
                getDOM().appendChild(host, getDOM().createStyleElement(style));
            }
        };
        DomSharedStylesHost.prototype.addHost = function (hostNode) {
            this._addStylesToHost(this._styles, hostNode);
            this._hostNodes.add(hostNode);
        };
        DomSharedStylesHost.prototype.removeHost = function (hostNode) { SetWrapper.delete(this._hostNodes, hostNode); };
        DomSharedStylesHost.prototype.onStylesAdded = function (additions) {
            var _this = this;
            this._hostNodes.forEach(function (hostNode) { _this._addStylesToHost(additions, hostNode); });
        };
        return DomSharedStylesHost;
    }(SharedStylesHost));
    /** @nocollapse */
    DomSharedStylesHost.decorators = [
        { type: _angular_core.Injectable },
    ];
    /** @nocollapse */
    DomSharedStylesHost.ctorParameters = [
        { type: undefined, decorators: [{ type: _angular_core.Inject, args: [DOCUMENT,] },] },
    ];
    var NAMESPACE_URIS = {
        'xlink': 'http://www.w3.org/1999/xlink',
        'svg': 'http://www.w3.org/2000/svg',
        'xhtml': 'http://www.w3.org/1999/xhtml'
    };
    var TEMPLATE_COMMENT_TEXT = 'template bindings={}';
    var TEMPLATE_BINDINGS_EXP = /^template bindings=(.*)$/g;
    var DomRootRenderer = (function () {
        function DomRootRenderer(document, eventManager, sharedStylesHost, animationDriver) {
            this.document = document;
            this.eventManager = eventManager;
            this.sharedStylesHost = sharedStylesHost;
            this.animationDriver = animationDriver;
            this.registeredComponents = new Map();
        }
        DomRootRenderer.prototype.renderComponent = function (componentProto) {
            var renderer = this.registeredComponents.get(componentProto.id);
            if (isBlank(renderer)) {
                renderer = new DomRenderer(this, componentProto, this.animationDriver);
                this.registeredComponents.set(componentProto.id, renderer);
            }
            return renderer;
        };
        return DomRootRenderer;
    }());
    var DomRootRenderer_ = (function (_super) {
        __extends(DomRootRenderer_, _super);
        function DomRootRenderer_(_document, _eventManager, sharedStylesHost, animationDriver) {
            _super.call(this, _document, _eventManager, sharedStylesHost, animationDriver);
        }
        return DomRootRenderer_;
    }(DomRootRenderer));
    /** @nocollapse */
    DomRootRenderer_.decorators = [
        { type: _angular_core.Injectable },
    ];
    /** @nocollapse */
    DomRootRenderer_.ctorParameters = [
        { type: undefined, decorators: [{ type: _angular_core.Inject, args: [DOCUMENT,] },] },
        { type: EventManager, },
        { type: DomSharedStylesHost, },
        { type: AnimationDriver, },
    ];
    var DomRenderer = (function () {
        function DomRenderer(_rootRenderer, componentProto, _animationDriver) {
            this._rootRenderer = _rootRenderer;
            this.componentProto = componentProto;
            this._animationDriver = _animationDriver;
            this._styles = _flattenStyles(componentProto.id, componentProto.styles, []);
            if (componentProto.encapsulation !== _angular_core.ViewEncapsulation.Native) {
                this._rootRenderer.sharedStylesHost.addStyles(this._styles);
            }
            if (this.componentProto.encapsulation === _angular_core.ViewEncapsulation.Emulated) {
                this._contentAttr = _shimContentAttribute(componentProto.id);
                this._hostAttr = _shimHostAttribute(componentProto.id);
            }
            else {
                this._contentAttr = null;
                this._hostAttr = null;
            }
        }
        DomRenderer.prototype.selectRootElement = function (selectorOrNode, debugInfo) {
            var el;
            if (isString(selectorOrNode)) {
                el = getDOM().querySelector(this._rootRenderer.document, selectorOrNode);
                if (isBlank(el)) {
                    throw new BaseException$1("The selector \"" + selectorOrNode + "\" did not match any elements");
                }
            }
            else {
                el = selectorOrNode;
            }
            getDOM().clearNodes(el);
            return el;
        };
        DomRenderer.prototype.createElement = function (parent, name, debugInfo) {
            var nsAndName = splitNamespace(name);
            var el = isPresent(nsAndName[0]) ?
                getDOM().createElementNS(NAMESPACE_URIS[nsAndName[0]], nsAndName[1]) :
                getDOM().createElement(nsAndName[1]);
            if (isPresent(this._contentAttr)) {
                getDOM().setAttribute(el, this._contentAttr, '');
            }
            if (isPresent(parent)) {
                getDOM().appendChild(parent, el);
            }
            return el;
        };
        DomRenderer.prototype.createViewRoot = function (hostElement) {
            var nodesParent;
            if (this.componentProto.encapsulation === _angular_core.ViewEncapsulation.Native) {
                nodesParent = getDOM().createShadowRoot(hostElement);
                this._rootRenderer.sharedStylesHost.addHost(nodesParent);
                for (var i = 0; i < this._styles.length; i++) {
                    getDOM().appendChild(nodesParent, getDOM().createStyleElement(this._styles[i]));
                }
            }
            else {
                if (isPresent(this._hostAttr)) {
                    getDOM().setAttribute(hostElement, this._hostAttr, '');
                }
                nodesParent = hostElement;
            }
            return nodesParent;
        };
        DomRenderer.prototype.createTemplateAnchor = function (parentElement, debugInfo) {
            var comment = getDOM().createComment(TEMPLATE_COMMENT_TEXT);
            if (isPresent(parentElement)) {
                getDOM().appendChild(parentElement, comment);
            }
            return comment;
        };
        DomRenderer.prototype.createText = function (parentElement, value, debugInfo) {
            var node = getDOM().createTextNode(value);
            if (isPresent(parentElement)) {
                getDOM().appendChild(parentElement, node);
            }
            return node;
        };
        DomRenderer.prototype.projectNodes = function (parentElement, nodes) {
            if (isBlank(parentElement))
                return;
            appendNodes(parentElement, nodes);
        };
        DomRenderer.prototype.attachViewAfter = function (node, viewRootNodes) { moveNodesAfterSibling(node, viewRootNodes); };
        DomRenderer.prototype.detachView = function (viewRootNodes) {
            for (var i = 0; i < viewRootNodes.length; i++) {
                getDOM().remove(viewRootNodes[i]);
            }
        };
        DomRenderer.prototype.destroyView = function (hostElement, viewAllNodes) {
            if (this.componentProto.encapsulation === _angular_core.ViewEncapsulation.Native && isPresent(hostElement)) {
                this._rootRenderer.sharedStylesHost.removeHost(getDOM().getShadowRoot(hostElement));
            }
        };
        DomRenderer.prototype.listen = function (renderElement, name, callback) {
            return this._rootRenderer.eventManager.addEventListener(renderElement, name, decoratePreventDefault(callback));
        };
        DomRenderer.prototype.listenGlobal = function (target, name, callback) {
            return this._rootRenderer.eventManager.addGlobalEventListener(target, name, decoratePreventDefault(callback));
        };
        DomRenderer.prototype.setElementProperty = function (renderElement, propertyName, propertyValue) {
            getDOM().setProperty(renderElement, propertyName, propertyValue);
        };
        DomRenderer.prototype.setElementAttribute = function (renderElement, attributeName, attributeValue) {
            var attrNs;
            var nsAndName = splitNamespace(attributeName);
            if (isPresent(nsAndName[0])) {
                attributeName = nsAndName[0] + ':' + nsAndName[1];
                attrNs = NAMESPACE_URIS[nsAndName[0]];
            }
            if (isPresent(attributeValue)) {
                if (isPresent(attrNs)) {
                    getDOM().setAttributeNS(renderElement, attrNs, attributeName, attributeValue);
                }
                else {
                    getDOM().setAttribute(renderElement, attributeName, attributeValue);
                }
            }
            else {
                if (isPresent(attrNs)) {
                    getDOM().removeAttributeNS(renderElement, attrNs, nsAndName[1]);
                }
                else {
                    getDOM().removeAttribute(renderElement, attributeName);
                }
            }
        };
        DomRenderer.prototype.setBindingDebugInfo = function (renderElement, propertyName, propertyValue) {
            var dashCasedPropertyName = camelCaseToDashCase(propertyName);
            if (getDOM().isCommentNode(renderElement)) {
                var existingBindings = RegExpWrapper.firstMatch(TEMPLATE_BINDINGS_EXP, StringWrapper.replaceAll(getDOM().getText(renderElement), /\n/g, ''));
                var parsedBindings = Json.parse(existingBindings[1]);
                parsedBindings[dashCasedPropertyName] = propertyValue;
                getDOM().setText(renderElement, StringWrapper.replace(TEMPLATE_COMMENT_TEXT, '{}', Json.stringify(parsedBindings)));
            }
            else {
                this.setElementAttribute(renderElement, propertyName, propertyValue);
            }
        };
        DomRenderer.prototype.setElementClass = function (renderElement, className, isAdd) {
            if (isAdd) {
                getDOM().addClass(renderElement, className);
            }
            else {
                getDOM().removeClass(renderElement, className);
            }
        };
        DomRenderer.prototype.setElementStyle = function (renderElement, styleName, styleValue) {
            if (isPresent(styleValue)) {
                getDOM().setStyle(renderElement, styleName, stringify(styleValue));
            }
            else {
                getDOM().removeStyle(renderElement, styleName);
            }
        };
        DomRenderer.prototype.invokeElementMethod = function (renderElement, methodName, args) {
            getDOM().invoke(renderElement, methodName, args);
        };
        DomRenderer.prototype.setText = function (renderNode, text) { getDOM().setText(renderNode, text); };
        DomRenderer.prototype.animate = function (element, startingStyles, keyframes, duration, delay, easing) {
            return this._animationDriver.animate(element, startingStyles, keyframes, duration, delay, easing);
        };
        return DomRenderer;
    }());
    function moveNodesAfterSibling(sibling /** TODO #9100 */, nodes /** TODO #9100 */) {
        var parent = getDOM().parentElement(sibling);
        if (nodes.length > 0 && isPresent(parent)) {
            var nextSibling = getDOM().nextSibling(sibling);
            if (isPresent(nextSibling)) {
                for (var i = 0; i < nodes.length; i++) {
                    getDOM().insertBefore(nextSibling, nodes[i]);
                }
            }
            else {
                for (var i = 0; i < nodes.length; i++) {
                    getDOM().appendChild(parent, nodes[i]);
                }
            }
        }
    }
    function appendNodes(parent /** TODO #9100 */, nodes /** TODO #9100 */) {
        for (var i = 0; i < nodes.length; i++) {
            getDOM().appendChild(parent, nodes[i]);
        }
    }
    function decoratePreventDefault(eventHandler) {
        return function (event /** TODO #9100 */) {
            var allowDefaultBehavior = eventHandler(event);
            if (allowDefaultBehavior === false) {
                // TODO(tbosch): move preventDefault into event plugins...
                getDOM().preventDefault(event);
            }
        };
    }
    var COMPONENT_REGEX = /%COMP%/g;
    var COMPONENT_VARIABLE = '%COMP%';
    var HOST_ATTR = "_nghost-" + COMPONENT_VARIABLE;
    var CONTENT_ATTR = "_ngcontent-" + COMPONENT_VARIABLE;
    function _shimContentAttribute(componentShortId) {
        return StringWrapper.replaceAll(CONTENT_ATTR, COMPONENT_REGEX, componentShortId);
    }
    function _shimHostAttribute(componentShortId) {
        return StringWrapper.replaceAll(HOST_ATTR, COMPONENT_REGEX, componentShortId);
    }
    function _flattenStyles(compId, styles, target) {
        for (var i = 0; i < styles.length; i++) {
            var style = styles[i];
            if (isArray(style)) {
                _flattenStyles(compId, style, target);
            }
            else {
                style = StringWrapper.replaceAll(style, COMPONENT_REGEX, compId);
                target.push(style);
            }
        }
        return target;
    }
    var NS_PREFIX_RE = /^:([^:]+):(.+)/g;
    function splitNamespace(name) {
        if (name[0] != ':') {
            return [null, name];
        }
        var match = RegExpWrapper.firstMatch(NS_PREFIX_RE, name);
        return [match[1], match[2]];
    }
    var CORE_TOKENS = {
        'ApplicationRef': _angular_core.ApplicationRef,
        'NgZone': _angular_core.NgZone
    };
    var INSPECT_GLOBAL_NAME = 'ng.probe';
    var CORE_TOKENS_GLOBAL_NAME = 'ng.coreTokens';
    /**
     * Returns a {@link DebugElement} for the given native DOM element, or
     * null if the given native element does not have an Angular view associated
     * with it.
     */
    function inspectNativeElement(element /** TODO #9100 */) {
        return _angular_core.getDebugNode(element);
    }
    function _createConditionalRootRenderer(rootRenderer /** TODO #9100 */) {
        if (_angular_core.isDevMode()) {
            return _createRootRenderer(rootRenderer);
        }
        return rootRenderer;
    }
    function _createRootRenderer(rootRenderer /** TODO #9100 */) {
        getDOM().setGlobalVar(INSPECT_GLOBAL_NAME, inspectNativeElement);
        getDOM().setGlobalVar(CORE_TOKENS_GLOBAL_NAME, CORE_TOKENS);
        return new DebugDomRootRenderer(rootRenderer);
    }
    /**
     * Providers which support debugging Angular applications (e.g. via `ng.probe`).
     */
    var ELEMENT_PROBE_PROVIDERS = [{ provide: _angular_core.RootRenderer, useFactory: _createConditionalRootRenderer, deps: [DomRootRenderer] }];
    var DomEventsPlugin = (function (_super) {
        __extends(DomEventsPlugin, _super);
        function DomEventsPlugin() {
            _super.apply(this, arguments);
        }
        // This plugin should come last in the list of plugins, because it accepts all
        // events.
        DomEventsPlugin.prototype.supports = function (eventName) { return true; };
        DomEventsPlugin.prototype.addEventListener = function (element, eventName, handler) {
            var zone = this.manager.getZone();
            var outsideHandler = function (event /** TODO #9100 */) { return zone.runGuarded(function () { return handler(event); }); };
            return this.manager.getZone().runOutsideAngular(function () { return getDOM().onAndCancel(element, eventName, outsideHandler); });
        };
        DomEventsPlugin.prototype.addGlobalEventListener = function (target, eventName, handler) {
            var element = getDOM().getGlobalEventTarget(target);
            var zone = this.manager.getZone();
            var outsideHandler = function (event /** TODO #9100 */) { return zone.runGuarded(function () { return handler(event); }); };
            return this.manager.getZone().runOutsideAngular(function () { return getDOM().onAndCancel(element, eventName, outsideHandler); });
        };
        return DomEventsPlugin;
    }(EventManagerPlugin));
    /** @nocollapse */
    DomEventsPlugin.decorators = [
        { type: _angular_core.Injectable },
    ];
    var modifierKeys = ['alt', 'control', 'meta', 'shift'];
    var modifierKeyGetters = {
        'alt': function (event) { return event.altKey; },
        'control': function (event) { return event.ctrlKey; },
        'meta': function (event) { return event.metaKey; },
        'shift': function (event) { return event.shiftKey; }
    };
    var KeyEventsPlugin = (function (_super) {
        __extends(KeyEventsPlugin, _super);
        function KeyEventsPlugin() {
            _super.call(this);
        }
        KeyEventsPlugin.prototype.supports = function (eventName) {
            return isPresent(KeyEventsPlugin.parseEventName(eventName));
        };
        KeyEventsPlugin.prototype.addEventListener = function (element, eventName, handler) {
            var parsedEvent = KeyEventsPlugin.parseEventName(eventName);
            var outsideHandler = KeyEventsPlugin.eventCallback(element, StringMapWrapper.get(parsedEvent, 'fullKey'), handler, this.manager.getZone());
            return this.manager.getZone().runOutsideAngular(function () {
                return getDOM().onAndCancel(element, StringMapWrapper.get(parsedEvent, 'domEventName'), outsideHandler);
            });
        };
        KeyEventsPlugin.parseEventName = function (eventName) {
            var parts = eventName.toLowerCase().split('.');
            var domEventName = parts.shift();
            if ((parts.length === 0) ||
                !(StringWrapper.equals(domEventName, 'keydown') ||
                    StringWrapper.equals(domEventName, 'keyup'))) {
                return null;
            }
            var key = KeyEventsPlugin._normalizeKey(parts.pop());
            var fullKey = '';
            modifierKeys.forEach(function (modifierName) {
                if (ListWrapper.contains(parts, modifierName)) {
                    ListWrapper.remove(parts, modifierName);
                    fullKey += modifierName + '.';
                }
            });
            fullKey += key;
            if (parts.length != 0 || key.length === 0) {
                // returning null instead of throwing to let another plugin process the event
                return null;
            }
            var result = StringMapWrapper.create();
            StringMapWrapper.set(result, 'domEventName', domEventName);
            StringMapWrapper.set(result, 'fullKey', fullKey);
            return result;
        };
        KeyEventsPlugin.getEventFullKey = function (event) {
            var fullKey = '';
            var key = getDOM().getEventKey(event);
            key = key.toLowerCase();
            if (StringWrapper.equals(key, ' ')) {
                key = 'space'; // for readability
            }
            else if (StringWrapper.equals(key, '.')) {
                key = 'dot'; // because '.' is used as a separator in event names
            }
            modifierKeys.forEach(function (modifierName) {
                if (modifierName != key) {
                    var modifierGetter = StringMapWrapper.get(modifierKeyGetters, modifierName);
                    if (modifierGetter(event)) {
                        fullKey += modifierName + '.';
                    }
                }
            });
            fullKey += key;
            return fullKey;
        };
        KeyEventsPlugin.eventCallback = function (element, fullKey, handler, zone) {
            return function (event /** TODO #9100 */) {
                if (StringWrapper.equals(KeyEventsPlugin.getEventFullKey(event), fullKey)) {
                    zone.runGuarded(function () { return handler(event); });
                }
            };
        };
        /** @internal */
        KeyEventsPlugin._normalizeKey = function (keyName) {
            // TODO: switch to a StringMap if the mapping grows too much
            switch (keyName) {
                case 'esc':
                    return 'escape';
                default:
                    return keyName;
            }
        };
        return KeyEventsPlugin;
    }(EventManagerPlugin));
    /** @nocollapse */
    KeyEventsPlugin.decorators = [
        { type: _angular_core.Injectable },
    ];
    /** @nocollapse */
    KeyEventsPlugin.ctorParameters = [];
    var BROWSER_PLATFORM_MARKER = new _angular_core.OpaqueToken('BrowserPlatformMarker');
    /**
     * A set of providers to initialize the Angular platform in a web browser.
     *
     * Used automatically by `bootstrap`, or can be passed to {@link platform}.
     *
     * @experimental API related to bootstrapping are still under review.
     */
    var BROWSER_PLATFORM_PROVIDERS = [
        { provide: BROWSER_PLATFORM_MARKER, useValue: true }, _angular_core.PLATFORM_COMMON_PROVIDERS,
        { provide: _angular_core.PLATFORM_INITIALIZER, useValue: initDomAdapter, multi: true },
        { provide: _angular_common.PlatformLocation, useClass: BrowserPlatformLocation }
    ];
    /**
     * @security Replacing built-in sanitization providers exposes the application to XSS risks.
     * Attacker-controlled data introduced by an unsanitized provider could expose your
     * application to XSS risks. For more detail, see the [Security Guide](http://g.co/ng/security).
     * @experimental
     */
    var BROWSER_SANITIZATION_PROVIDERS = [
        { provide: SanitizationService, useExisting: DomSanitizationService },
        { provide: DomSanitizationService, useClass: DomSanitizationServiceImpl },
    ];
    /**
     * A set of providers to initialize an Angular application in a web browser.
     *
     * Used automatically by `bootstrap`, or can be passed to {@link PlatformRef.application}.
     *
     * @experimental API related to bootstrapping are still under review.
     */
    var BROWSER_APP_PROVIDERS = [
        _angular_core.APPLICATION_COMMON_PROVIDERS, _angular_common.FORM_PROVIDERS, BROWSER_SANITIZATION_PROVIDERS,
        { provide: _angular_core.ExceptionHandler, useFactory: _exceptionHandler, deps: [] },
        { provide: DOCUMENT, useFactory: _document, deps: [] },
        { provide: EVENT_MANAGER_PLUGINS, useClass: DomEventsPlugin, multi: true },
        { provide: EVENT_MANAGER_PLUGINS, useClass: KeyEventsPlugin, multi: true },
        { provide: EVENT_MANAGER_PLUGINS, useClass: HammerGesturesPlugin, multi: true },
        { provide: HAMMER_GESTURE_CONFIG, useClass: HammerGestureConfig },
        { provide: DomRootRenderer, useClass: DomRootRenderer_ },
        { provide: _angular_core.RootRenderer, useExisting: DomRootRenderer },
        { provide: SharedStylesHost, useExisting: DomSharedStylesHost },
        { provide: AnimationDriver, useFactory: _resolveDefaultAnimationDriver }, DomSharedStylesHost,
        _angular_core.Testability, EventManager, ELEMENT_PROBE_PROVIDERS
    ];
    /**
     * @experimental API related to bootstrapping are still under review.
     */
    function browserPlatform() {
        if (isBlank(_angular_core.getPlatform())) {
            _angular_core.createPlatform(_angular_core.ReflectiveInjector.resolveAndCreate(BROWSER_PLATFORM_PROVIDERS));
        }
        return _angular_core.assertPlatform(BROWSER_PLATFORM_MARKER);
    }
    function initDomAdapter() {
        BrowserDomAdapter.makeCurrent();
        wtfInit();
        BrowserGetTestability.init();
    }
    function _exceptionHandler() {
        return new _angular_core.ExceptionHandler(getDOM());
    }
    function _document() {
        return getDOM().defaultDoc();
    }
    function _resolveDefaultAnimationDriver() {
        if (getDOM().supportsWebAnimation()) {
            return new WebAnimationsDriver();
        }
        return new NoOpAnimationDriver();
    }
    /**
     * @license
     * Copyright Google Inc. All Rights Reserved.
     *
     * Use of this source code is governed by an MIT-style license that can be
     * found in the LICENSE file at https://angular.io/license
     */
    var PromiseCompleter = (function () {
        function PromiseCompleter() {
            var _this = this;
            this.promise = new Promise(function (res, rej) {
                _this.resolve = res;
                _this.reject = rej;
            });
        }
        return PromiseCompleter;
    }());
    var PromiseWrapper = (function () {
        function PromiseWrapper() {
        }
        PromiseWrapper.resolve = function (obj) { return Promise.resolve(obj); };
        PromiseWrapper.reject = function (obj, _) { return Promise.reject(obj); };
        // Note: We can't rename this method into `catch`, as this is not a valid
        // method name in Dart.
        PromiseWrapper.catchError = function (promise, onError) {
            return promise.catch(onError);
        };
        PromiseWrapper.all = function (promises) {
            if (promises.length == 0)
                return Promise.resolve([]);
            return Promise.all(promises);
        };
        PromiseWrapper.then = function (promise, success, rejection) {
            return promise.then(success, rejection);
        };
        PromiseWrapper.wrap = function (computation) {
            return new Promise(function (res, rej) {
                try {
                    res(computation());
                }
                catch (e) {
                    rej(e);
                }
            });
        };
        PromiseWrapper.scheduleMicrotask = function (computation) {
            PromiseWrapper.then(PromiseWrapper.resolve(null), computation, function (_) { });
        };
        PromiseWrapper.completer = function () { return new PromiseCompleter(); };
        return PromiseWrapper;
    }());
    var ObservableWrapper = (function () {
        function ObservableWrapper() {
        }
        // TODO(vsavkin): when we use rxnext, try inferring the generic type from the first arg
        ObservableWrapper.subscribe = function (emitter, onNext, onError, onComplete) {
            if (onComplete === void 0) { onComplete = function () { }; }
            onError = (typeof onError === 'function') && onError || noop;
            onComplete = (typeof onComplete === 'function') && onComplete || noop;
            return emitter.subscribe({ next: onNext, error: onError, complete: onComplete });
        };
        ObservableWrapper.isObservable = function (obs) { return !!obs.subscribe; };
        /**
         * Returns whether `obs` has any subscribers listening to events.
         */
        ObservableWrapper.hasSubscribers = function (obs) { return obs.observers.length > 0; };
        ObservableWrapper.dispose = function (subscription) { subscription.unsubscribe(); };
        /**
         * @deprecated - use callEmit() instead
         */
        ObservableWrapper.callNext = function (emitter, value) { emitter.emit(value); };
        ObservableWrapper.callEmit = function (emitter, value) { emitter.emit(value); };
        ObservableWrapper.callError = function (emitter, error) { emitter.error(error); };
        ObservableWrapper.callComplete = function (emitter) { emitter.complete(); };
        ObservableWrapper.fromPromise = function (promise) {
            return rxjs_observable_PromiseObservable.PromiseObservable.create(promise);
        };
        ObservableWrapper.toPromise = function (obj) { return rxjs_operator_toPromise.toPromise.call(obj); };
        return ObservableWrapper;
    }());
    /**
     * Use by directives and components to emit custom Events.
     *
     * ### Examples
     *
     * In the following example, `Zippy` alternatively emits `open` and `close` events when its
     * title gets clicked:
     *
     * ```
     * @Component({
     *   selector: 'zippy',
     *   template: `
     *   <div class="zippy">
     *     <div (click)="toggle()">Toggle</div>
     *     <div [hidden]="!visible">
     *       <ng-content></ng-content>
     *     </div>
     *  </div>`})
     * export class Zippy {
     *   visible: boolean = true;
     *   @Output() open: EventEmitter<any> = new EventEmitter();
     *   @Output() close: EventEmitter<any> = new EventEmitter();
     *
     *   toggle() {
     *     this.visible = !this.visible;
     *     if (this.visible) {
     *       this.open.emit(null);
     *     } else {
     *       this.close.emit(null);
     *     }
     *   }
     * }
     * ```
     *
     * The events payload can be accessed by the parameter `$event` on the components output event
     * handler:
     *
     * ```
     * <zippy (open)="onOpen($event)" (close)="onClose($event)"></zippy>
     * ```
     *
     * Uses Rx.Observable but provides an adapter to make it work as specified here:
     * https://github.com/jhusain/observable-spec
     *
     * Once a reference implementation of the spec is available, switch to it.
     * @stable
     */
    var EventEmitter = (function (_super) {
        __extends(EventEmitter, _super);
        /**
         * Creates an instance of [EventEmitter], which depending on [isAsync],
         * delivers events synchronously or asynchronously.
         */
        function EventEmitter(isAsync) {
            if (isAsync === void 0) { isAsync = false; }
            _super.call(this);
            this.__isAsync = isAsync;
        }
        EventEmitter.prototype.emit = function (value) { _super.prototype.next.call(this, value); };
        /**
         * @deprecated - use .emit(value) instead
         */
        EventEmitter.prototype.next = function (value) { _super.prototype.next.call(this, value); };
        EventEmitter.prototype.subscribe = function (generatorOrNext, error, complete) {
            var schedulerFn;
            var errorFn = function (err) { return null; };
            var completeFn = function () { return null; };
            if (generatorOrNext && typeof generatorOrNext === 'object') {
                schedulerFn = this.__isAsync ? function (value /** TODO #9100 */) {
                    setTimeout(function () { return generatorOrNext.next(value); });
                } : function (value /** TODO #9100 */) { generatorOrNext.next(value); };
                if (generatorOrNext.error) {
                    errorFn = this.__isAsync ? function (err) { setTimeout(function () { return generatorOrNext.error(err); }); } :
                        function (err) { generatorOrNext.error(err); };
                }
                if (generatorOrNext.complete) {
                    completeFn = this.__isAsync ? function () { setTimeout(function () { return generatorOrNext.complete(); }); } :
                        function () { generatorOrNext.complete(); };
                }
            }
            else {
                schedulerFn = this.__isAsync ? function (value /** TODO #9100 */) {
                    setTimeout(function () { return generatorOrNext(value); });
                } : function (value /** TODO #9100 */) { generatorOrNext(value); };
                if (error) {
                    errorFn =
                        this.__isAsync ? function (err) { setTimeout(function () { return error(err); }); } : function (err) { error(err); };
                }
                if (complete) {
                    completeFn =
                        this.__isAsync ? function () { setTimeout(function () { return complete(); }); } : function () { complete(); };
                }
            }
            return _super.prototype.subscribe.call(this, schedulerFn, errorFn, completeFn);
        };
        return EventEmitter;
    }(rxjs_Subject.Subject));
    /**
     * @license
     * Copyright Google Inc. All Rights Reserved.
     *
     * Use of this source code is governed by an MIT-style license that can be
     * found in the LICENSE file at https://angular.io/license
     */
    /**
     * Message Bus is a low level API used to communicate between the UI and the background.
     * Communication is based on a channel abstraction. Messages published in a
     * given channel to one MessageBusSink are received on the same channel
     * by the corresponding MessageBusSource.
     *
     * @experimental WebWorker support in Angular is currenlty experimental.
     */
    var MessageBus = (function () {
        function MessageBus() {
        }
        return MessageBus;
    }());
    var RenderStore = (function () {
        function RenderStore() {
            this._nextIndex = 0;
            this._lookupById = new Map();
            this._lookupByObject = new Map();
        }
        RenderStore.prototype.allocateId = function () { return this._nextIndex++; };
        RenderStore.prototype.store = function (obj, id) {
            this._lookupById.set(id, obj);
            this._lookupByObject.set(obj, id);
        };
        RenderStore.prototype.remove = function (obj) {
            var index = this._lookupByObject.get(obj);
            this._lookupByObject.delete(obj);
            this._lookupById.delete(index);
        };
        RenderStore.prototype.deserialize = function (id) {
            if (id == null) {
                return null;
            }
            if (!this._lookupById.has(id)) {
                return null;
            }
            return this._lookupById.get(id);
        };
        RenderStore.prototype.serialize = function (obj) {
            if (obj == null) {
                return null;
            }
            return this._lookupByObject.get(obj);
        };
        return RenderStore;
    }());
    /** @nocollapse */
    RenderStore.decorators = [
        { type: _angular_core.Injectable },
    ];
    /** @nocollapse */
    RenderStore.ctorParameters = [];
    /**
     * @license
     * Copyright Google Inc. All Rights Reserved.
     *
     * Use of this source code is governed by an MIT-style license that can be
     * found in the LICENSE file at https://angular.io/license
     */
    // This file contains interface versions of browser types that can be serialized to Plain Old
    // JavaScript Objects
    var LocationType = (function () {
        function LocationType(href, protocol, host, hostname, port, pathname, search, hash, origin) {
            this.href = href;
            this.protocol = protocol;
            this.host = host;
            this.hostname = hostname;
            this.port = port;
            this.pathname = pathname;
            this.search = search;
            this.hash = hash;
            this.origin = origin;
        }
        return LocationType;
    }());
    // PRIMITIVE is any type that does not need to be serialized (string, number, boolean)
    // We set it to String so that it is considered a Type.
    /**
     * @experimental WebWorker support in Angular is currently experimental.
     */
    var PRIMITIVE = String;
    var Serializer = (function () {
        function Serializer(_renderStore) {
            this._renderStore = _renderStore;
        }
        Serializer.prototype.serialize = function (obj, type) {
            var _this = this;
            if (!isPresent(obj)) {
                return null;
            }
            if (isArray(obj)) {
                return obj.map(function (v) { return _this.serialize(v, type); });
            }
            if (type == PRIMITIVE) {
                return obj;
            }
            if (type == RenderStoreObject) {
                return this._renderStore.serialize(obj);
            }
            else if (type === _angular_core.RenderComponentType) {
                return this._serializeRenderComponentType(obj);
            }
            else if (type === _angular_core.ViewEncapsulation) {
                return serializeEnum(obj);
            }
            else if (type === LocationType) {
                return this._serializeLocation(obj);
            }
            else {
                throw new BaseException$1('No serializer for ' + type.toString());
            }
        };
        Serializer.prototype.deserialize = function (map, type, data) {
            var _this = this;
            if (!isPresent(map)) {
                return null;
            }
            if (isArray(map)) {
                var obj = [];
                map.forEach(function (val) { return obj.push(_this.deserialize(val, type, data)); });
                return obj;
            }
            if (type == PRIMITIVE) {
                return map;
            }
            if (type == RenderStoreObject) {
                return this._renderStore.deserialize(map);
            }
            else if (type === _angular_core.RenderComponentType) {
                return this._deserializeRenderComponentType(map);
            }
            else if (type === _angular_core.ViewEncapsulation) {
                return VIEW_ENCAPSULATION_VALUES[map];
            }
            else if (type === LocationType) {
                return this._deserializeLocation(map);
            }
            else {
                throw new BaseException$1('No deserializer for ' + type.toString());
            }
        };
        Serializer.prototype._serializeLocation = function (loc) {
            return {
                'href': loc.href,
                'protocol': loc.protocol,
                'host': loc.host,
                'hostname': loc.hostname,
                'port': loc.port,
                'pathname': loc.pathname,
                'search': loc.search,
                'hash': loc.hash,
                'origin': loc.origin
            };
        };
        Serializer.prototype._deserializeLocation = function (loc) {
            return new LocationType(loc['href'], loc['protocol'], loc['host'], loc['hostname'], loc['port'], loc['pathname'], loc['search'], loc['hash'], loc['origin']);
        };
        Serializer.prototype._serializeRenderComponentType = function (obj) {
            return {
                'id': obj.id,
                'templateUrl': obj.templateUrl,
                'slotCount': obj.slotCount,
                'encapsulation': this.serialize(obj.encapsulation, _angular_core.ViewEncapsulation),
                'styles': this.serialize(obj.styles, PRIMITIVE)
            };
        };
        Serializer.prototype._deserializeRenderComponentType = function (map) {
            return new _angular_core.RenderComponentType(map['id'], map['templateUrl'], map['slotCount'], this.deserialize(map['encapsulation'], _angular_core.ViewEncapsulation), this.deserialize(map['styles'], PRIMITIVE));
        };
        return Serializer;
    }());
    /** @nocollapse */
    Serializer.decorators = [
        { type: _angular_core.Injectable },
    ];
    /** @nocollapse */
    Serializer.ctorParameters = [
        { type: RenderStore, },
    ];
    var RenderStoreObject = (function () {
        function RenderStoreObject() {
        }
        return RenderStoreObject;
    }());
    /**
     * @experimental WebWorker support in Angular is experimental.
     */
    var ClientMessageBrokerFactory = (function () {
        function ClientMessageBrokerFactory() {
        }
        return ClientMessageBrokerFactory;
    }());
    var ClientMessageBrokerFactory_ = (function (_super) {
        __extends(ClientMessageBrokerFactory_, _super);
        function ClientMessageBrokerFactory_(_messageBus, _serializer) {
            _super.call(this);
            this._messageBus = _messageBus;
            this._serializer = _serializer;
        }
        /**
         * Initializes the given channel and attaches a new {@link ClientMessageBroker} to it.
         */
        ClientMessageBrokerFactory_.prototype.createMessageBroker = function (channel, runInZone) {
            if (runInZone === void 0) { runInZone = true; }
            this._messageBus.initChannel(channel, runInZone);
            return new ClientMessageBroker_(this._messageBus, this._serializer, channel);
        };
        return ClientMessageBrokerFactory_;
    }(ClientMessageBrokerFactory));
    /** @nocollapse */
    ClientMessageBrokerFactory_.decorators = [
        { type: _angular_core.Injectable },
    ];
    /** @nocollapse */
    ClientMessageBrokerFactory_.ctorParameters = [
        { type: MessageBus, },
        { type: Serializer, },
    ];
    /**
     * @experimental WebWorker support in Angular is experimental.
     */
    var ClientMessageBroker = (function () {
        function ClientMessageBroker() {
        }
        return ClientMessageBroker;
    }());
    var ClientMessageBroker_ = (function (_super) {
        __extends(ClientMessageBroker_, _super);
        function ClientMessageBroker_(messageBus, _serializer, channel /** TODO #9100 */) {
            var _this = this;
            _super.call(this);
            this.channel = channel;
            this._pending = new Map();
            this._sink = messageBus.to(channel);
            this._serializer = _serializer;
            var source = messageBus.from(channel);
            ObservableWrapper.subscribe(source, function (message) { return _this._handleMessage(message); });
        }
        ClientMessageBroker_.prototype._generateMessageId = function (name) {
            var time = stringify(DateWrapper.toMillis(DateWrapper.now()));
            var iteration = 0;
            var id = name + time + stringify(iteration);
            while (isPresent(this._pending[id])) {
                id = "" + name + time + iteration;
                iteration++;
            }
            return id;
        };
        ClientMessageBroker_.prototype.runOnService = function (args, returnType) {
            var _this = this;
            var fnArgs = [];
            if (isPresent(args.args)) {
                args.args.forEach(function (argument) {
                    if (argument.type != null) {
                        fnArgs.push(_this._serializer.serialize(argument.value, argument.type));
                    }
                    else {
                        fnArgs.push(argument.value);
                    }
                });
            }
            var promise;
            var id = null;
            if (returnType != null) {
                var completer = PromiseWrapper.completer();
                id = this._generateMessageId(args.method);
                this._pending.set(id, completer);
                PromiseWrapper.catchError(completer.promise, function (err, stack) {
                    print(err);
                    completer.reject(err, stack);
                });
                promise = PromiseWrapper.then(completer.promise, function (value) {
                    if (_this._serializer == null) {
                        return value;
                    }
                    else {
                        return _this._serializer.deserialize(value, returnType);
                    }
                });
            }
            else {
                promise = null;
            }
            // TODO(jteplitz602): Create a class for these messages so we don't keep using StringMap #3685
            var message = { 'method': args.method, 'args': fnArgs };
            if (id != null) {
                message['id'] = id;
            }
            ObservableWrapper.callEmit(this._sink, message);
            return promise;
        };
        ClientMessageBroker_.prototype._handleMessage = function (message) {
            var data = new MessageData(message);
            // TODO(jteplitz602): replace these strings with messaging constants #3685
            if (StringWrapper.equals(data.type, 'result') || StringWrapper.equals(data.type, 'error')) {
                var id = data.id;
                if (this._pending.has(id)) {
                    if (StringWrapper.equals(data.type, 'result')) {
                        this._pending.get(id).resolve(data.value);
                    }
                    else {
                        this._pending.get(id).reject(data.value, null);
                    }
                    this._pending.delete(id);
                }
            }
        };
        return ClientMessageBroker_;
    }(ClientMessageBroker));
    var MessageData = (function () {
        function MessageData(data) {
            this.type = StringMapWrapper.get(data, 'type');
            this.id = this._getValueIfPresent(data, 'id');
            this.value = this._getValueIfPresent(data, 'value');
        }
        /**
         * Returns the value from the StringMap if present. Otherwise returns null
         * @internal
         */
        MessageData.prototype._getValueIfPresent = function (data, key) {
            if (StringMapWrapper.contains(data, key)) {
                return StringMapWrapper.get(data, key);
            }
            else {
                return null;
            }
        };
        return MessageData;
    }());
    /**
     * @experimental WebWorker support in Angular is experimental.
     */
    var FnArg = (function () {
        function FnArg(value /** TODO #9100 */, type) {
            this.value = value;
            this.type = type;
        }
        return FnArg;
    }());
    /**
     * @experimental WebWorker support in Angular is experimental.
     */
    var UiArguments = (function () {
        function UiArguments(method, args) {
            this.method = method;
            this.args = args;
        }
        return UiArguments;
    }());
    /**
     * @experimental WebWorker support in Angular is currently experimental.
     */
    var ServiceMessageBrokerFactory = (function () {
        function ServiceMessageBrokerFactory() {
        }
        return ServiceMessageBrokerFactory;
    }());
    var ServiceMessageBrokerFactory_ = (function (_super) {
        __extends(ServiceMessageBrokerFactory_, _super);
        function ServiceMessageBrokerFactory_(_messageBus, _serializer) {
            _super.call(this);
            this._messageBus = _messageBus;
            this._serializer = _serializer;
        }
        ServiceMessageBrokerFactory_.prototype.createMessageBroker = function (channel, runInZone) {
            if (runInZone === void 0) { runInZone = true; }
            this._messageBus.initChannel(channel, runInZone);
            return new ServiceMessageBroker_(this._messageBus, this._serializer, channel);
        };
        return ServiceMessageBrokerFactory_;
    }(ServiceMessageBrokerFactory));
    /** @nocollapse */
    ServiceMessageBrokerFactory_.decorators = [
        { type: _angular_core.Injectable },
    ];
    /** @nocollapse */
    ServiceMessageBrokerFactory_.ctorParameters = [
        { type: MessageBus, },
        { type: Serializer, },
    ];
    /**
     * Helper class for UIComponents that allows components to register methods.
     * If a registered method message is received from the broker on the worker,
     * the UIMessageBroker deserializes its arguments and calls the registered method.
     * If that method returns a promise, the UIMessageBroker returns the result to the worker.
     *
     * @experimental WebWorker support in Angular is currently experimental.
     */
    var ServiceMessageBroker = (function () {
        function ServiceMessageBroker() {
        }
        return ServiceMessageBroker;
    }());
    var ServiceMessageBroker_ = (function (_super) {
        __extends(ServiceMessageBroker_, _super);
        function ServiceMessageBroker_(messageBus, _serializer, channel /** TODO #9100 */) {
            var _this = this;
            _super.call(this);
            this._serializer = _serializer;
            this.channel = channel;
            this._methods = new Map$1();
            this._sink = messageBus.to(channel);
            var source = messageBus.from(channel);
            ObservableWrapper.subscribe(source, function (message) { return _this._handleMessage(message); });
        }
        ServiceMessageBroker_.prototype.registerMethod = function (methodName, signature, method, returnType) {
            var _this = this;
            this._methods.set(methodName, function (message) {
                var serializedArgs = message.args;
                var numArgs = signature === null ? 0 : signature.length;
                var deserializedArgs = ListWrapper.createFixedSize(numArgs);
                for (var i = 0; i < numArgs; i++) {
                    var serializedArg = serializedArgs[i];
                    deserializedArgs[i] = _this._serializer.deserialize(serializedArg, signature[i]);
                }
                var promise = FunctionWrapper.apply(method, deserializedArgs);
                if (isPresent(returnType) && isPresent(promise)) {
                    _this._wrapWebWorkerPromise(message.id, promise, returnType);
                }
            });
        };
        ServiceMessageBroker_.prototype._handleMessage = function (map) {
            var message = new ReceivedMessage(map);
            if (this._methods.has(message.method)) {
                this._methods.get(message.method)(message);
            }
        };
        ServiceMessageBroker_.prototype._wrapWebWorkerPromise = function (id, promise, type) {
            var _this = this;
            PromiseWrapper.then(promise, function (result) {
                ObservableWrapper.callEmit(_this._sink, { 'type': 'result', 'value': _this._serializer.serialize(result, type), 'id': id });
            });
        };
        return ServiceMessageBroker_;
    }(ServiceMessageBroker));
    /**
     * @experimental WebWorker support in Angular is currently experimental.
     */
    var ReceivedMessage = (function () {
        function ReceivedMessage(data) {
            this.method = data['method'];
            this.args = data['args'];
            this.id = data['id'];
            this.type = data['type'];
        }
        return ReceivedMessage;
    }());
    /**
     * @license
     * Copyright Google Inc. All Rights Reserved.
     *
     * Use of this source code is governed by an MIT-style license that can be
     * found in the LICENSE file at https://angular.io/license
     */
    /**
     * All channels used by angular's WebWorker components are listed here.
     * You should not use these channels in your application code.
     */
    var RENDERER_CHANNEL = 'ng-Renderer';
    var EVENT_CHANNEL = 'ng-Events';
    var ROUTER_CHANNEL = 'ng-Router';
    /**
     * @license
     * Copyright Google Inc. All Rights Reserved.
     *
     * Use of this source code is governed by an MIT-style license that can be
     * found in the LICENSE file at https://angular.io/license
     */
    // no deserialization is necessary in TS.
    // This is only here to match dart interface
    function deserializeGenericEvent(serializedEvent) {
        return serializedEvent;
    }
    var WebWorkerPlatformLocation = (function (_super) {
        __extends(WebWorkerPlatformLocation, _super);
        function WebWorkerPlatformLocation(brokerFactory, bus, _serializer) {
            var _this = this;
            _super.call(this);
            this._serializer = _serializer;
            this._popStateListeners = [];
            this._hashChangeListeners = [];
            this._location = null;
            this._broker = brokerFactory.createMessageBroker(ROUTER_CHANNEL);
            this._channelSource = bus.from(ROUTER_CHANNEL);
            ObservableWrapper.subscribe(this._channelSource, function (msg) {
                var listeners = null;
                if (StringMapWrapper.contains(msg, 'event')) {
                    var type = msg['event']['type'];
                    if (StringWrapper.equals(type, 'popstate')) {
                        listeners = _this._popStateListeners;
                    }
                    else if (StringWrapper.equals(type, 'hashchange')) {
                        listeners = _this._hashChangeListeners;
                    }
                    if (listeners !== null) {
                        var e_1 = deserializeGenericEvent(msg['event']);
                        // There was a popState or hashChange event, so the location object thas been updated
                        _this._location = _this._serializer.deserialize(msg['location'], LocationType);
                        listeners.forEach(function (fn) { return fn(e_1); });
                    }
                }
            });
        }
        /** @internal **/
        WebWorkerPlatformLocation.prototype.init = function () {
            var _this = this;
            var args = new UiArguments('getLocation');
            var locationPromise = this._broker.runOnService(args, LocationType);
            return PromiseWrapper.then(locationPromise, function (val) {
                _this._location = val;
                return true;
            }, function (err) { throw new BaseException$1(err); });
        };
        WebWorkerPlatformLocation.prototype.getBaseHrefFromDOM = function () {
            throw new BaseException$1('Attempt to get base href from DOM from WebWorker. You must either provide a value for the APP_BASE_HREF token through DI or use the hash location strategy.');
        };
        WebWorkerPlatformLocation.prototype.onPopState = function (fn) { this._popStateListeners.push(fn); };
        WebWorkerPlatformLocation.prototype.onHashChange = function (fn) { this._hashChangeListeners.push(fn); };
        Object.defineProperty(WebWorkerPlatformLocation.prototype, "pathname", {
            get: function () {
                if (this._location === null) {
                    return null;
                }
                return this._location.pathname;
            },
            set: function (newPath) {
                if (this._location === null) {
                    throw new BaseException$1('Attempt to set pathname before value is obtained from UI');
                }
                this._location.pathname = newPath;
                var fnArgs = [new FnArg(newPath, PRIMITIVE)];
                var args = new UiArguments('setPathname', fnArgs);
                this._broker.runOnService(args, null);
            },
            enumerable: true,
            configurable: true
        });
        Object.defineProperty(WebWorkerPlatformLocation.prototype, "search", {
            get: function () {
                if (this._location === null) {
                    return null;
                }
                return this._location.search;
            },
            enumerable: true,
            configurable: true
        });
        Object.defineProperty(WebWorkerPlatformLocation.prototype, "hash", {
            get: function () {
                if (this._location === null) {
                    return null;
                }
                return this._location.hash;
            },
            enumerable: true,
            configurable: true
        });
        WebWorkerPlatformLocation.prototype.pushState = function (state, title, url) {
            var fnArgs = [new FnArg(state, PRIMITIVE), new FnArg(title, PRIMITIVE), new FnArg(url, PRIMITIVE)];
            var args = new UiArguments('pushState', fnArgs);
            this._broker.runOnService(args, null);
        };
        WebWorkerPlatformLocation.prototype.replaceState = function (state, title, url) {
            var fnArgs = [new FnArg(state, PRIMITIVE), new FnArg(title, PRIMITIVE), new FnArg(url, PRIMITIVE)];
            var args = new UiArguments('replaceState', fnArgs);
            this._broker.runOnService(args, null);
        };
        WebWorkerPlatformLocation.prototype.forward = function () {
            var args = new UiArguments('forward');
            this._broker.runOnService(args, null);
        };
        WebWorkerPlatformLocation.prototype.back = function () {
            var args = new UiArguments('back');
            this._broker.runOnService(args, null);
        };
        return WebWorkerPlatformLocation;
    }(_angular_common.PlatformLocation));
    /** @nocollapse */
    WebWorkerPlatformLocation.decorators = [
        { type: _angular_core.Injectable },
    ];
    /** @nocollapse */
    WebWorkerPlatformLocation.ctorParameters = [
        { type: ClientMessageBrokerFactory, },
        { type: MessageBus, },
        { type: Serializer, },
    ];
    /**
     * Those providers should be added when the router is used in a worker context in addition to the
     * {@link ROUTER_PROVIDERS} and after them.
     * @experimental
     */
    var WORKER_APP_LOCATION_PROVIDERS = [
        { provide: _angular_common.PlatformLocation, useClass: WebWorkerPlatformLocation }, {
            provide: _angular_core.APP_INITIALIZER,
            useFactory: appInitFnFactory,
            multi: true,
            deps: [_angular_common.PlatformLocation, _angular_core.NgZone]
        }
    ];
    function appInitFnFactory(platformLocation, zone) {
        return function () { return zone.runGuarded(function () { return platformLocation.init(); }); };
    }
    var MessageBasedPlatformLocation = (function () {
        function MessageBasedPlatformLocation(_brokerFactory, _platformLocation, bus, _serializer) {
            this._brokerFactory = _brokerFactory;
            this._platformLocation = _platformLocation;
            this._serializer = _serializer;
            this._platformLocation.onPopState(FunctionWrapper.bind(this._sendUrlChangeEvent, this));
            this._platformLocation.onHashChange(FunctionWrapper.bind(this._sendUrlChangeEvent, this));
            this._broker = this._brokerFactory.createMessageBroker(ROUTER_CHANNEL);
            this._channelSink = bus.to(ROUTER_CHANNEL);
        }
        MessageBasedPlatformLocation.prototype.start = function () {
            this._broker.registerMethod('getLocation', null, FunctionWrapper.bind(this._getLocation, this), LocationType);
            this._broker.registerMethod('setPathname', [PRIMITIVE], FunctionWrapper.bind(this._setPathname, this));
            this._broker.registerMethod('pushState', [PRIMITIVE, PRIMITIVE, PRIMITIVE], FunctionWrapper.bind(this._platformLocation.pushState, this._platformLocation));
            this._broker.registerMethod('replaceState', [PRIMITIVE, PRIMITIVE, PRIMITIVE], FunctionWrapper.bind(this._platformLocation.replaceState, this._platformLocation));
            this._broker.registerMethod('forward', null, FunctionWrapper.bind(this._platformLocation.forward, this._platformLocation));
            this._broker.registerMethod('back', null, FunctionWrapper.bind(this._platformLocation.back, this._platformLocation));
        };
        MessageBasedPlatformLocation.prototype._getLocation = function () {
            return PromiseWrapper.resolve(this._platformLocation.location);
        };
        MessageBasedPlatformLocation.prototype._sendUrlChangeEvent = function (e) {
            var loc = this._serializer.serialize(this._platformLocation.location, LocationType);
            var serializedEvent = { 'type': e.type };
            ObservableWrapper.callEmit(this._channelSink, { 'event': serializedEvent, 'location': loc });
        };
        MessageBasedPlatformLocation.prototype._setPathname = function (pathname) { this._platformLocation.pathname = pathname; };
        return MessageBasedPlatformLocation;
    }());
    /** @nocollapse */
    MessageBasedPlatformLocation.decorators = [
        { type: _angular_core.Injectable },
    ];
    /** @nocollapse */
    MessageBasedPlatformLocation.ctorParameters = [
        { type: ServiceMessageBrokerFactory, },
        { type: BrowserPlatformLocation, },
        { type: MessageBus, },
        { type: Serializer, },
    ];
    /**
     * A list of {@link Provider}s. To use the router in a Worker enabled application you must
     * include these providers when setting up the render thread.
     * @experimental
     */
    var WORKER_UI_LOCATION_PROVIDERS = [
        MessageBasedPlatformLocation, BrowserPlatformLocation,
        { provide: _angular_core.APP_INITIALIZER, useFactory: initUiLocation, multi: true, deps: [_angular_core.Injector] }
    ];
    function initUiLocation(injector) {
        return function () {
            var zone = injector.get(_angular_core.NgZone);
            zone.runGuarded(function () { return injector.get(MessageBasedPlatformLocation).start(); });
        };
    }
    var ON_WEB_WORKER = new _angular_core.OpaqueToken('WebWorker.onWebWorker');
    var PostMessageBusSink = (function () {
        function PostMessageBusSink(_postMessageTarget) {
            this._postMessageTarget = _postMessageTarget;
            this._channels = StringMapWrapper.create();
            this._messageBuffer = [];
        }
        PostMessageBusSink.prototype.attachToZone = function (zone) {
            var _this = this;
            this._zone = zone;
            this._zone.runOutsideAngular(function () {
                ObservableWrapper.subscribe(_this._zone.onStable, function (_) { _this._handleOnEventDone(); });
            });
        };
        PostMessageBusSink.prototype.initChannel = function (channel, runInZone) {
            var _this = this;
            if (runInZone === void 0) { runInZone = true; }
            if (StringMapWrapper.contains(this._channels, channel)) {
                throw new BaseException$1(channel + " has already been initialized");
            }
            var emitter = new EventEmitter(false);
            var channelInfo = new _Channel(emitter, runInZone);
            this._channels[channel] = channelInfo;
            emitter.subscribe(function (data) {
                var message = { channel: channel, message: data };
                if (runInZone) {
                    _this._messageBuffer.push(message);
                }
                else {
                    _this._sendMessages([message]);
                }
            });
        };
        PostMessageBusSink.prototype.to = function (channel) {
            if (StringMapWrapper.contains(this._channels, channel)) {
                return this._channels[channel].emitter;
            }
            else {
                throw new BaseException$1(channel + " is not set up. Did you forget to call initChannel?");
            }
        };
        PostMessageBusSink.prototype._handleOnEventDone = function () {
            if (this._messageBuffer.length > 0) {
                this._sendMessages(this._messageBuffer);
                this._messageBuffer = [];
            }
        };
        PostMessageBusSink.prototype._sendMessages = function (messages) { this._postMessageTarget.postMessage(messages); };
        return PostMessageBusSink;
    }());
    var PostMessageBusSource = (function () {
        function PostMessageBusSource(eventTarget) {
            var _this = this;
            this._channels = StringMapWrapper.create();
            if (eventTarget) {
                eventTarget.addEventListener('message', function (ev) { return _this._handleMessages(ev); });
            }
            else {
                // if no eventTarget is given we assume we're in a WebWorker and listen on the global scope
                var workerScope = self;
                workerScope.addEventListener('message', function (ev) { return _this._handleMessages(ev); });
            }
        }
        PostMessageBusSource.prototype.attachToZone = function (zone) { this._zone = zone; };
        PostMessageBusSource.prototype.initChannel = function (channel, runInZone) {
            if (runInZone === void 0) { runInZone = true; }
            if (StringMapWrapper.contains(this._channels, channel)) {
                throw new BaseException$1(channel + " has already been initialized");
            }
            var emitter = new EventEmitter(false);
            var channelInfo = new _Channel(emitter, runInZone);
            this._channels[channel] = channelInfo;
        };
        PostMessageBusSource.prototype.from = function (channel) {
            if (StringMapWrapper.contains(this._channels, channel)) {
                return this._channels[channel].emitter;
            }
            else {
                throw new BaseException$1(channel + " is not set up. Did you forget to call initChannel?");
            }
        };
        PostMessageBusSource.prototype._handleMessages = function (ev) {
            var messages = ev.data;
            for (var i = 0; i < messages.length; i++) {
                this._handleMessage(messages[i]);
            }
        };
        PostMessageBusSource.prototype._handleMessage = function (data) {
            var channel = data.channel;
            if (StringMapWrapper.contains(this._channels, channel)) {
                var channelInfo = this._channels[channel];
                if (channelInfo.runInZone) {
                    this._zone.run(function () { channelInfo.emitter.emit(data.message); });
                }
                else {
                    channelInfo.emitter.emit(data.message);
                }
            }
        };
        return PostMessageBusSource;
    }());
    var PostMessageBus = (function () {
        function PostMessageBus(sink, source) {
            this.sink = sink;
            this.source = source;
        }
        PostMessageBus.prototype.attachToZone = function (zone) {
            this.source.attachToZone(zone);
            this.sink.attachToZone(zone);
        };
        PostMessageBus.prototype.initChannel = function (channel, runInZone) {
            if (runInZone === void 0) { runInZone = true; }
            this.source.initChannel(channel, runInZone);
            this.sink.initChannel(channel, runInZone);
        };
        PostMessageBus.prototype.from = function (channel) { return this.source.from(channel); };
        PostMessageBus.prototype.to = function (channel) { return this.sink.to(channel); };
        return PostMessageBus;
    }());
    /** @nocollapse */
    PostMessageBus.decorators = [
        { type: _angular_core.Injectable },
    ];
    /** @nocollapse */
    PostMessageBus.ctorParameters = [
        { type: PostMessageBusSink, },
        { type: PostMessageBusSource, },
    ];
    /**
     * Helper class that wraps a channel's {@link EventEmitter} and
     * keeps track of if it should run in the zone.
     */
    var _Channel = (function () {
        function _Channel(emitter, runInZone) {
            this.emitter = emitter;
            this.runInZone = runInZone;
        }
        return _Channel;
    }());
    var MOUSE_EVENT_PROPERTIES = [
        'altKey', 'button', 'clientX', 'clientY', 'metaKey', 'movementX', 'movementY', 'offsetX',
        'offsetY', 'region', 'screenX', 'screenY', 'shiftKey'
    ];
    var KEYBOARD_EVENT_PROPERTIES = [
        'altkey', 'charCode', 'code', 'ctrlKey', 'isComposing', 'key', 'keyCode', 'location', 'metaKey',
        'repeat', 'shiftKey', 'which'
    ];
    var TRANSITION_EVENT_PROPERTIES = ['propertyName', 'elapsedTime', 'pseudoElement'];
    var EVENT_PROPERTIES = ['type', 'bubbles', 'cancelable'];
    var NODES_WITH_VALUE = new Set$1(['input', 'select', 'option', 'button', 'li', 'meter', 'progress', 'param', 'textarea']);
    function serializeGenericEvent(e) {
        return serializeEvent(e, EVENT_PROPERTIES);
    }
    // TODO(jteplitz602): Allow users to specify the properties they need rather than always
    // adding value and files #3374
    function serializeEventWithTarget(e) {
        var serializedEvent = serializeEvent(e, EVENT_PROPERTIES);
        return addTarget(e, serializedEvent);
    }
    function serializeMouseEvent(e) {
        return serializeEvent(e, MOUSE_EVENT_PROPERTIES);
    }
    function serializeKeyboardEvent(e) {
        var serializedEvent = serializeEvent(e, KEYBOARD_EVENT_PROPERTIES);
        return addTarget(e, serializedEvent);
    }
    function serializeTransitionEvent(e) {
        var serializedEvent = serializeEvent(e, TRANSITION_EVENT_PROPERTIES);
        return addTarget(e, serializedEvent);
    }
    // TODO(jteplitz602): #3374. See above.
    function addTarget(e, serializedEvent) {
        if (NODES_WITH_VALUE.has(e.target.tagName.toLowerCase())) {
            var target = e.target;
            serializedEvent['target'] = { 'value': target.value };
            if (isPresent(target.files)) {
                serializedEvent['target']['files'] = target.files;
            }
        }
        return serializedEvent;
    }
    function serializeEvent(e, properties) {
        var serialized = {};
        for (var i = 0; i < properties.length; i++) {
            var prop = properties[i];
            serialized[prop] = e[prop];
        }
        return serialized;
    }
    var EventDispatcher = (function () {
        function EventDispatcher(_sink, _serializer) {
            this._sink = _sink;
            this._serializer = _serializer;
        }
        EventDispatcher.prototype.dispatchRenderEvent = function (element, eventTarget, eventName, event) {
            var serializedEvent;
            // TODO (jteplitz602): support custom events #3350
            switch (event.type) {
                case 'click':
                case 'mouseup':
                case 'mousedown':
                case 'dblclick':
                case 'contextmenu':
                case 'mouseenter':
                case 'mouseleave':
                case 'mousemove':
                case 'mouseout':
                case 'mouseover':
                case 'show':
                    serializedEvent = serializeMouseEvent(event);
                    break;
                case 'keydown':
                case 'keypress':
                case 'keyup':
                    serializedEvent = serializeKeyboardEvent(event);
                    break;
                case 'input':
                case 'change':
                case 'blur':
                    serializedEvent = serializeEventWithTarget(event);
                    break;
                case 'abort':
                case 'afterprint':
                case 'beforeprint':
                case 'cached':
                case 'canplay':
                case 'canplaythrough':
                case 'chargingchange':
                case 'chargingtimechange':
                case 'close':
                case 'dischargingtimechange':
                case 'DOMContentLoaded':
                case 'downloading':
                case 'durationchange':
                case 'emptied':
                case 'ended':
                case 'error':
                case 'fullscreenchange':
                case 'fullscreenerror':
                case 'invalid':
                case 'languagechange':
                case 'levelfchange':
                case 'loadeddata':
                case 'loadedmetadata':
                case 'obsolete':
                case 'offline':
                case 'online':
                case 'open':
                case 'orientatoinchange':
                case 'pause':
                case 'pointerlockchange':
                case 'pointerlockerror':
                case 'play':
                case 'playing':
                case 'ratechange':
                case 'readystatechange':
                case 'reset':
                case 'scroll':
                case 'seeked':
                case 'seeking':
                case 'stalled':
                case 'submit':
                case 'success':
                case 'suspend':
                case 'timeupdate':
                case 'updateready':
                case 'visibilitychange':
                case 'volumechange':
                case 'waiting':
                    serializedEvent = serializeGenericEvent(event);
                    break;
                case 'transitionend':
                    serializedEvent = serializeTransitionEvent(event);
                    break;
                default:
                    throw new BaseException$1(eventName + ' not supported on WebWorkers');
            }
            ObservableWrapper.callEmit(this._sink, {
                'element': this._serializer.serialize(element, RenderStoreObject),
                'eventName': eventName,
                'eventTarget': eventTarget,
                'event': serializedEvent
            });
            // TODO(kegluneq): Eventually, we want the user to indicate from the UI side whether the event
            // should be canceled, but for now just call `preventDefault` on the original DOM event.
            return false;
        };
        return EventDispatcher;
    }());
    var MessageBasedRenderer = (function () {
        function MessageBasedRenderer(_brokerFactory, _bus, _serializer, _renderStore, _rootRenderer) {
            this._brokerFactory = _brokerFactory;
            this._bus = _bus;
            this._serializer = _serializer;
            this._renderStore = _renderStore;
            this._rootRenderer = _rootRenderer;
        }
        MessageBasedRenderer.prototype.start = function () {
            var broker = this._brokerFactory.createMessageBroker(RENDERER_CHANNEL);
            this._bus.initChannel(EVENT_CHANNEL);
            this._eventDispatcher = new EventDispatcher(this._bus.to(EVENT_CHANNEL), this._serializer);
            broker.registerMethod('renderComponent', [_angular_core.RenderComponentType, PRIMITIVE], FunctionWrapper.bind(this._renderComponent, this));
            broker.registerMethod('selectRootElement', [RenderStoreObject, PRIMITIVE, PRIMITIVE], FunctionWrapper.bind(this._selectRootElement, this));
            broker.registerMethod('createElement', [RenderStoreObject, RenderStoreObject, PRIMITIVE, PRIMITIVE], FunctionWrapper.bind(this._createElement, this));
            broker.registerMethod('createViewRoot', [RenderStoreObject, RenderStoreObject, PRIMITIVE], FunctionWrapper.bind(this._createViewRoot, this));
            broker.registerMethod('createTemplateAnchor', [RenderStoreObject, RenderStoreObject, PRIMITIVE], FunctionWrapper.bind(this._createTemplateAnchor, this));
            broker.registerMethod('createText', [RenderStoreObject, RenderStoreObject, PRIMITIVE, PRIMITIVE], FunctionWrapper.bind(this._createText, this));
            broker.registerMethod('projectNodes', [RenderStoreObject, RenderStoreObject, RenderStoreObject], FunctionWrapper.bind(this._projectNodes, this));
            broker.registerMethod('attachViewAfter', [RenderStoreObject, RenderStoreObject, RenderStoreObject], FunctionWrapper.bind(this._attachViewAfter, this));
            broker.registerMethod('detachView', [RenderStoreObject, RenderStoreObject], FunctionWrapper.bind(this._detachView, this));
            broker.registerMethod('destroyView', [RenderStoreObject, RenderStoreObject, RenderStoreObject], FunctionWrapper.bind(this._destroyView, this));
            broker.registerMethod('setElementProperty', [RenderStoreObject, RenderStoreObject, PRIMITIVE, PRIMITIVE], FunctionWrapper.bind(this._setElementProperty, this));
            broker.registerMethod('setElementAttribute', [RenderStoreObject, RenderStoreObject, PRIMITIVE, PRIMITIVE], FunctionWrapper.bind(this._setElementAttribute, this));
            broker.registerMethod('setBindingDebugInfo', [RenderStoreObject, RenderStoreObject, PRIMITIVE, PRIMITIVE], FunctionWrapper.bind(this._setBindingDebugInfo, this));
            broker.registerMethod('setElementClass', [RenderStoreObject, RenderStoreObject, PRIMITIVE, PRIMITIVE], FunctionWrapper.bind(this._setElementClass, this));
            broker.registerMethod('setElementStyle', [RenderStoreObject, RenderStoreObject, PRIMITIVE, PRIMITIVE], FunctionWrapper.bind(this._setElementStyle, this));
            broker.registerMethod('invokeElementMethod', [RenderStoreObject, RenderStoreObject, PRIMITIVE, PRIMITIVE], FunctionWrapper.bind(this._invokeElementMethod, this));
            broker.registerMethod('setText', [RenderStoreObject, RenderStoreObject, PRIMITIVE], FunctionWrapper.bind(this._setText, this));
            broker.registerMethod('listen', [RenderStoreObject, RenderStoreObject, PRIMITIVE, PRIMITIVE], FunctionWrapper.bind(this._listen, this));
            broker.registerMethod('listenGlobal', [RenderStoreObject, PRIMITIVE, PRIMITIVE, PRIMITIVE], FunctionWrapper.bind(this._listenGlobal, this));
            broker.registerMethod('listenDone', [RenderStoreObject, RenderStoreObject], FunctionWrapper.bind(this._listenDone, this));
        };
        MessageBasedRenderer.prototype._renderComponent = function (renderComponentType, rendererId) {
            var renderer = this._rootRenderer.renderComponent(renderComponentType);
            this._renderStore.store(renderer, rendererId);
        };
        MessageBasedRenderer.prototype._selectRootElement = function (renderer, selector, elId) {
            this._renderStore.store(renderer.selectRootElement(selector, null), elId);
        };
        MessageBasedRenderer.prototype._createElement = function (renderer, parentElement, name, elId) {
            this._renderStore.store(renderer.createElement(parentElement, name, null), elId);
        };
        MessageBasedRenderer.prototype._createViewRoot = function (renderer, hostElement, elId) {
            var viewRoot = renderer.createViewRoot(hostElement);
            if (this._renderStore.serialize(hostElement) !== elId) {
                this._renderStore.store(viewRoot, elId);
            }
        };
        MessageBasedRenderer.prototype._createTemplateAnchor = function (renderer, parentElement, elId) {
            this._renderStore.store(renderer.createTemplateAnchor(parentElement, null), elId);
        };
        MessageBasedRenderer.prototype._createText = function (renderer, parentElement, value, elId) {
            this._renderStore.store(renderer.createText(parentElement, value, null), elId);
        };
        MessageBasedRenderer.prototype._projectNodes = function (renderer, parentElement, nodes) {
            renderer.projectNodes(parentElement, nodes);
        };
        MessageBasedRenderer.prototype._attachViewAfter = function (renderer, node, viewRootNodes) {
            renderer.attachViewAfter(node, viewRootNodes);
        };
        MessageBasedRenderer.prototype._detachView = function (renderer, viewRootNodes) {
            renderer.detachView(viewRootNodes);
        };
        MessageBasedRenderer.prototype._destroyView = function (renderer, hostElement, viewAllNodes) {
            renderer.destroyView(hostElement, viewAllNodes);
            for (var i = 0; i < viewAllNodes.length; i++) {
                this._renderStore.remove(viewAllNodes[i]);
            }
        };
        MessageBasedRenderer.prototype._setElementProperty = function (renderer, renderElement, propertyName, propertyValue) {
            renderer.setElementProperty(renderElement, propertyName, propertyValue);
        };
        MessageBasedRenderer.prototype._setElementAttribute = function (renderer, renderElement, attributeName, attributeValue) {
            renderer.setElementAttribute(renderElement, attributeName, attributeValue);
        };
        MessageBasedRenderer.prototype._setBindingDebugInfo = function (renderer, renderElement, propertyName, propertyValue) {
            renderer.setBindingDebugInfo(renderElement, propertyName, propertyValue);
        };
        MessageBasedRenderer.prototype._setElementClass = function (renderer, renderElement, className, isAdd) {
            renderer.setElementClass(renderElement, className, isAdd);
        };
        MessageBasedRenderer.prototype._setElementStyle = function (renderer, renderElement, styleName, styleValue) {
            renderer.setElementStyle(renderElement, styleName, styleValue);
        };
        MessageBasedRenderer.prototype._invokeElementMethod = function (renderer, renderElement, methodName, args) {
            renderer.invokeElementMethod(renderElement, methodName, args);
        };
        MessageBasedRenderer.prototype._setText = function (renderer, renderNode, text) {
            renderer.setText(renderNode, text);
        };
        MessageBasedRenderer.prototype._listen = function (renderer, renderElement, eventName, unlistenId) {
            var _this = this;
            var unregisterCallback = renderer.listen(renderElement, eventName, function (event /** TODO #9100 */) { return _this._eventDispatcher.dispatchRenderEvent(renderElement, null, eventName, event); });
            this._renderStore.store(unregisterCallback, unlistenId);
        };
        MessageBasedRenderer.prototype._listenGlobal = function (renderer, eventTarget, eventName, unlistenId) {
            var _this = this;
            var unregisterCallback = renderer.listenGlobal(eventTarget, eventName, function (event /** TODO #9100 */) { return _this._eventDispatcher.dispatchRenderEvent(null, eventTarget, eventName, event); });
            this._renderStore.store(unregisterCallback, unlistenId);
        };
        MessageBasedRenderer.prototype._listenDone = function (renderer, unlistenCallback) { unlistenCallback(); };
        return MessageBasedRenderer;
    }());
    /** @nocollapse */
    MessageBasedRenderer.decorators = [
        { type: _angular_core.Injectable },
    ];
    /** @nocollapse */
    MessageBasedRenderer.ctorParameters = [
        { type: ServiceMessageBrokerFactory, },
        { type: MessageBus, },
        { type: Serializer, },
        { type: RenderStore, },
        { type: _angular_core.RootRenderer, },
    ];
    var WORKER_RENDER_PLATFORM_MARKER = new _angular_core.OpaqueToken('WorkerRenderPlatformMarker');
    var WebWorkerInstance = (function () {
        function WebWorkerInstance() {
        }
        /** @internal */
        WebWorkerInstance.prototype.init = function (worker, bus) {
            this.worker = worker;
            this.bus = bus;
        };
        return WebWorkerInstance;
    }());
    /** @nocollapse */
    WebWorkerInstance.decorators = [
        { type: _angular_core.Injectable },
    ];
    /**
     * @experimental WebWorker support is currently experimental.
     */
    var WORKER_SCRIPT = new _angular_core.OpaqueToken('WebWorkerScript');
    /**
     * A multiple providers used to automatically call the `start()` method after the service is
     * created.
     *
     * TODO(vicb): create an interface for startable services to implement
     * @experimental WebWorker support is currently experimental.
     */
    var WORKER_UI_STARTABLE_MESSAGING_SERVICE = new _angular_core.OpaqueToken('WorkerRenderStartableMsgService');
    /**
     * @experimental WebWorker support is currently experimental.
     */
    var WORKER_UI_PLATFORM_PROVIDERS = [
        _angular_core.PLATFORM_COMMON_PROVIDERS, { provide: WORKER_RENDER_PLATFORM_MARKER, useValue: true },
        { provide: _angular_core.PLATFORM_INITIALIZER, useValue: initWebWorkerRenderPlatform, multi: true }
    ];
    /**
     * @experimental WebWorker support is currently experimental.
     */
    var WORKER_UI_APPLICATION_PROVIDERS = [
        _angular_core.APPLICATION_COMMON_PROVIDERS,
        MessageBasedRenderer,
        { provide: WORKER_UI_STARTABLE_MESSAGING_SERVICE, useExisting: MessageBasedRenderer, multi: true },
        BROWSER_SANITIZATION_PROVIDERS,
        { provide: _angular_core.ExceptionHandler, useFactory: _exceptionHandler$1, deps: [] },
        { provide: DOCUMENT, useFactory: _document$1, deps: [] },
        // TODO(jteplitz602): Investigate if we definitely need EVENT_MANAGER on the render thread
        // #5298
        { provide: EVENT_MANAGER_PLUGINS, useClass: DomEventsPlugin, multi: true },
        { provide: EVENT_MANAGER_PLUGINS, useClass: KeyEventsPlugin, multi: true },
        { provide: EVENT_MANAGER_PLUGINS, useClass: HammerGesturesPlugin, multi: true },
        { provide: HAMMER_GESTURE_CONFIG, useClass: HammerGestureConfig },
        { provide: DomRootRenderer, useClass: DomRootRenderer_ },
        { provide: _angular_core.RootRenderer, useExisting: DomRootRenderer },
        { provide: SharedStylesHost, useExisting: DomSharedStylesHost },
        { provide: ServiceMessageBrokerFactory, useClass: ServiceMessageBrokerFactory_ },
        { provide: ClientMessageBrokerFactory, useClass: ClientMessageBrokerFactory_ },
        { provide: AnimationDriver, useFactory: _resolveDefaultAnimationDriver$1 },
        Serializer,
        { provide: ON_WEB_WORKER, useValue: false },
        RenderStore,
        DomSharedStylesHost,
        _angular_core.Testability,
        EventManager,
        WebWorkerInstance,
        { provide: _angular_core.APP_INITIALIZER, useFactory: initWebWorkerAppFn, multi: true, deps: [_angular_core.Injector] },
        { provide: MessageBus, useFactory: messageBusFactory, deps: [WebWorkerInstance] }
    ];
    function initializeGenericWorkerRenderer(injector) {
        var bus = injector.get(MessageBus);
        var zone = injector.get(_angular_core.NgZone);
        bus.attachToZone(zone);
        // initialize message services after the bus has been created
        var services = injector.get(WORKER_UI_STARTABLE_MESSAGING_SERVICE);
        zone.runGuarded(function () { services.forEach(function (svc) { svc.start(); }); });
    }
    function messageBusFactory(instance) {
        return instance.bus;
    }
    function initWebWorkerRenderPlatform() {
        BrowserDomAdapter.makeCurrent();
        wtfInit();
        BrowserGetTestability.init();
    }
    /**
     * @experimental WebWorker support is currently experimental.
     */
    function workerUiPlatform() {
        if (isBlank(_angular_core.getPlatform())) {
            _angular_core.createPlatform(_angular_core.ReflectiveInjector.resolveAndCreate(WORKER_UI_PLATFORM_PROVIDERS));
        }
        return _angular_core.assertPlatform(WORKER_RENDER_PLATFORM_MARKER);
    }
    function _exceptionHandler$1() {
        return new _angular_core.ExceptionHandler(getDOM());
    }
    function _document$1() {
        return getDOM().defaultDoc();
    }
    function initWebWorkerAppFn(injector) {
        return function () {
            var scriptUri;
            try {
                scriptUri = injector.get(WORKER_SCRIPT);
            }
            catch (e) {
                throw new BaseException$1('You must provide your WebWorker\'s initialization script with the WORKER_SCRIPT token');
            }
            var instance = injector.get(WebWorkerInstance);
            spawnWebWorker(scriptUri, instance);
            initializeGenericWorkerRenderer(injector);
        };
    }
    /**
     * Spawns a new class and initializes the WebWorkerInstance
     */
    function spawnWebWorker(uri, instance) {
        var webWorker = new Worker(uri);
        var sink = new PostMessageBusSink(webWorker);
        var source = new PostMessageBusSource(webWorker);
        var bus = new PostMessageBus(sink, source);
        instance.init(webWorker, bus);
    }
    function _resolveDefaultAnimationDriver$1() {
        // web workers have not been tested or configured to
        // work with animations just yet...
        return new NoOpAnimationDriver();
    }
    var WebWorkerRootRenderer = (function () {
        function WebWorkerRootRenderer(messageBrokerFactory, bus, _serializer, _renderStore) {
            var _this = this;
            this._serializer = _serializer;
            this._renderStore = _renderStore;
            this.globalEvents = new NamedEventEmitter();
            this._componentRenderers = new Map();
            this._messageBroker = messageBrokerFactory.createMessageBroker(RENDERER_CHANNEL);
            bus.initChannel(EVENT_CHANNEL);
            var source = bus.from(EVENT_CHANNEL);
            ObservableWrapper.subscribe(source, function (message) { return _this._dispatchEvent(message); });
        }
        WebWorkerRootRenderer.prototype._dispatchEvent = function (message) {
            var eventName = message['eventName'];
            var target = message['eventTarget'];
            var event = deserializeGenericEvent(message['event']);
            if (isPresent(target)) {
                this.globalEvents.dispatchEvent(eventNameWithTarget(target, eventName), event);
            }
            else {
                var element = this._serializer.deserialize(message['element'], RenderStoreObject);
                element.events.dispatchEvent(eventName, event);
            }
        };
        WebWorkerRootRenderer.prototype.renderComponent = function (componentType) {
            var result = this._componentRenderers.get(componentType.id);
            if (isBlank(result)) {
                result = new WebWorkerRenderer(this, componentType);
                this._componentRenderers.set(componentType.id, result);
                var id = this._renderStore.allocateId();
                this._renderStore.store(result, id);
                this.runOnService('renderComponent', [
                    new FnArg(componentType, _angular_core.RenderComponentType),
                    new FnArg(result, RenderStoreObject),
                ]);
            }
            return result;
        };
        WebWorkerRootRenderer.prototype.runOnService = function (fnName, fnArgs) {
            var args = new UiArguments(fnName, fnArgs);
            this._messageBroker.runOnService(args, null);
        };
        WebWorkerRootRenderer.prototype.allocateNode = function () {
            var result = new WebWorkerRenderNode();
            var id = this._renderStore.allocateId();
            this._renderStore.store(result, id);
            return result;
        };
        WebWorkerRootRenderer.prototype.allocateId = function () { return this._renderStore.allocateId(); };
        WebWorkerRootRenderer.prototype.destroyNodes = function (nodes) {
            for (var i = 0; i < nodes.length; i++) {
                this._renderStore.remove(nodes[i]);
            }
        };
        return WebWorkerRootRenderer;
    }());
    /** @nocollapse */
    WebWorkerRootRenderer.decorators = [
        { type: _angular_core.Injectable },
    ];
    /** @nocollapse */
    WebWorkerRootRenderer.ctorParameters = [
        { type: ClientMessageBrokerFactory, },
        { type: MessageBus, },
        { type: Serializer, },
        { type: RenderStore, },
    ];
    var WebWorkerRenderer = (function () {
        function WebWorkerRenderer(_rootRenderer, _componentType) {
            this._rootRenderer = _rootRenderer;
            this._componentType = _componentType;
        }
        WebWorkerRenderer.prototype._runOnService = function (fnName, fnArgs) {
            var fnArgsWithRenderer = [new FnArg(this, RenderStoreObject)].concat(fnArgs);
            this._rootRenderer.runOnService(fnName, fnArgsWithRenderer);
        };
        WebWorkerRenderer.prototype.selectRootElement = function (selectorOrNode, debugInfo) {
            var node = this._rootRenderer.allocateNode();
            this._runOnService('selectRootElement', [new FnArg(selectorOrNode, null), new FnArg(node, RenderStoreObject)]);
            return node;
        };
        WebWorkerRenderer.prototype.createElement = function (parentElement, name, debugInfo) {
            var node = this._rootRenderer.allocateNode();
            this._runOnService('createElement', [
                new FnArg(parentElement, RenderStoreObject), new FnArg(name, null),
                new FnArg(node, RenderStoreObject)
            ]);
            return node;
        };
        WebWorkerRenderer.prototype.createViewRoot = function (hostElement) {
            var viewRoot = this._componentType.encapsulation === _angular_core.ViewEncapsulation.Native ?
                this._rootRenderer.allocateNode() :
                hostElement;
            this._runOnService('createViewRoot', [new FnArg(hostElement, RenderStoreObject), new FnArg(viewRoot, RenderStoreObject)]);
            return viewRoot;
        };
        WebWorkerRenderer.prototype.createTemplateAnchor = function (parentElement, debugInfo) {
            var node = this._rootRenderer.allocateNode();
            this._runOnService('createTemplateAnchor', [new FnArg(parentElement, RenderStoreObject), new FnArg(node, RenderStoreObject)]);
            return node;
        };
        WebWorkerRenderer.prototype.createText = function (parentElement, value, debugInfo) {
            var node = this._rootRenderer.allocateNode();
            this._runOnService('createText', [
                new FnArg(parentElement, RenderStoreObject), new FnArg(value, null),
                new FnArg(node, RenderStoreObject)
            ]);
            return node;
        };
        WebWorkerRenderer.prototype.projectNodes = function (parentElement, nodes) {
            this._runOnService('projectNodes', [new FnArg(parentElement, RenderStoreObject), new FnArg(nodes, RenderStoreObject)]);
        };
        WebWorkerRenderer.prototype.attachViewAfter = function (node, viewRootNodes) {
            this._runOnService('attachViewAfter', [new FnArg(node, RenderStoreObject), new FnArg(viewRootNodes, RenderStoreObject)]);
        };
        WebWorkerRenderer.prototype.detachView = function (viewRootNodes) {
            this._runOnService('detachView', [new FnArg(viewRootNodes, RenderStoreObject)]);
        };
        WebWorkerRenderer.prototype.destroyView = function (hostElement, viewAllNodes) {
            this._runOnService('destroyView', [new FnArg(hostElement, RenderStoreObject), new FnArg(viewAllNodes, RenderStoreObject)]);
            this._rootRenderer.destroyNodes(viewAllNodes);
        };
        WebWorkerRenderer.prototype.setElementProperty = function (renderElement, propertyName, propertyValue) {
            this._runOnService('setElementProperty', [
                new FnArg(renderElement, RenderStoreObject), new FnArg(propertyName, null),
                new FnArg(propertyValue, null)
            ]);
        };
        WebWorkerRenderer.prototype.setElementAttribute = function (renderElement, attributeName, attributeValue) {
            this._runOnService('setElementAttribute', [
                new FnArg(renderElement, RenderStoreObject), new FnArg(attributeName, null),
                new FnArg(attributeValue, null)
            ]);
        };
        WebWorkerRenderer.prototype.setBindingDebugInfo = function (renderElement, propertyName, propertyValue) {
            this._runOnService('setBindingDebugInfo', [
                new FnArg(renderElement, RenderStoreObject), new FnArg(propertyName, null),
                new FnArg(propertyValue, null)
            ]);
        };
        WebWorkerRenderer.prototype.setElementClass = function (renderElement, className, isAdd) {
            this._runOnService('setElementClass', [
                new FnArg(renderElement, RenderStoreObject), new FnArg(className, null),
                new FnArg(isAdd, null)
            ]);
        };
        WebWorkerRenderer.prototype.setElementStyle = function (renderElement, styleName, styleValue) {
            this._runOnService('setElementStyle', [
                new FnArg(renderElement, RenderStoreObject), new FnArg(styleName, null),
                new FnArg(styleValue, null)
            ]);
        };
        WebWorkerRenderer.prototype.invokeElementMethod = function (renderElement, methodName, args) {
            this._runOnService('invokeElementMethod', [
                new FnArg(renderElement, RenderStoreObject), new FnArg(methodName, null),
                new FnArg(args, null)
            ]);
        };
        WebWorkerRenderer.prototype.setText = function (renderNode, text) {
            this._runOnService('setText', [new FnArg(renderNode, RenderStoreObject), new FnArg(text, null)]);
        };
        WebWorkerRenderer.prototype.listen = function (renderElement, name, callback) {
            var _this = this;
            renderElement.events.listen(name, callback);
            var unlistenCallbackId = this._rootRenderer.allocateId();
            this._runOnService('listen', [
                new FnArg(renderElement, RenderStoreObject), new FnArg(name, null),
                new FnArg(unlistenCallbackId, null)
            ]);
            return function () {
                renderElement.events.unlisten(name, callback);
                _this._runOnService('listenDone', [new FnArg(unlistenCallbackId, null)]);
            };
        };
        WebWorkerRenderer.prototype.listenGlobal = function (target, name, callback) {
            var _this = this;
            this._rootRenderer.globalEvents.listen(eventNameWithTarget(target, name), callback);
            var unlistenCallbackId = this._rootRenderer.allocateId();
            this._runOnService('listenGlobal', [new FnArg(target, null), new FnArg(name, null), new FnArg(unlistenCallbackId, null)]);
            return function () {
                _this._rootRenderer.globalEvents.unlisten(eventNameWithTarget(target, name), callback);
                _this._runOnService('listenDone', [new FnArg(unlistenCallbackId, null)]);
            };
        };
        WebWorkerRenderer.prototype.animate = function (element, startingStyles, keyframes, duration, delay, easing) {
            // TODO
            return null;
        };
        return WebWorkerRenderer;
    }());
    var NamedEventEmitter = (function () {
        function NamedEventEmitter() {
        }
        NamedEventEmitter.prototype._getListeners = function (eventName) {
            if (isBlank(this._listeners)) {
                this._listeners = new Map();
            }
            var listeners = this._listeners.get(eventName);
            if (isBlank(listeners)) {
                listeners = [];
                this._listeners.set(eventName, listeners);
            }
            return listeners;
        };
        NamedEventEmitter.prototype.listen = function (eventName, callback) { this._getListeners(eventName).push(callback); };
        NamedEventEmitter.prototype.unlisten = function (eventName, callback) {
            ListWrapper.remove(this._getListeners(eventName), callback);
        };
        NamedEventEmitter.prototype.dispatchEvent = function (eventName, event) {
            var listeners = this._getListeners(eventName);
            for (var i = 0; i < listeners.length; i++) {
                listeners[i](event);
            }
        };
        return NamedEventEmitter;
    }());
    function eventNameWithTarget(target, eventName) {
        return target + ":" + eventName;
    }
    var WebWorkerRenderNode = (function () {
        function WebWorkerRenderNode() {
            this.events = new NamedEventEmitter();
        }
        return WebWorkerRenderNode;
    }());
    /**
     * This adapter is required to log error messages.
     *
     * Note: other methods all throw as the DOM is not accessible directly in web worker context.
     */
    var WorkerDomAdapter = (function (_super) {
        __extends(WorkerDomAdapter, _super);
        function WorkerDomAdapter() {
            _super.apply(this, arguments);
        }
        WorkerDomAdapter.makeCurrent = function () { setRootDomAdapter(new WorkerDomAdapter()); };
        WorkerDomAdapter.prototype.logError = function (error /** TODO #9100 */) {
            if (console.error) {
                console.error(error);
            }
            else {
                console.log(error);
            }
        };
        WorkerDomAdapter.prototype.log = function (error /** TODO #9100 */) { console.log(error); };
        WorkerDomAdapter.prototype.logGroup = function (error /** TODO #9100 */) {
            if (console.group) {
                console.group(error);
                this.logError(error);
            }
            else {
                console.log(error);
            }
        };
        WorkerDomAdapter.prototype.logGroupEnd = function () {
            if (console.groupEnd) {
                console.groupEnd();
            }
        };
        WorkerDomAdapter.prototype.hasProperty = function (element /** TODO #9100 */, name) { throw 'not implemented'; };
        WorkerDomAdapter.prototype.setProperty = function (el, name, value) { throw 'not implemented'; };
        WorkerDomAdapter.prototype.getProperty = function (el, name) { throw 'not implemented'; };
        WorkerDomAdapter.prototype.invoke = function (el, methodName, args) { throw 'not implemented'; };
        WorkerDomAdapter.prototype.getXHR = function () { throw 'not implemented'; };
        Object.defineProperty(WorkerDomAdapter.prototype, "attrToPropMap", {
            get: function () { throw 'not implemented'; },
            set: function (value) { throw 'not implemented'; },
            enumerable: true,
            configurable: true
        });
        WorkerDomAdapter.prototype.parse = function (templateHtml) { throw 'not implemented'; };
        WorkerDomAdapter.prototype.query = function (selector) { throw 'not implemented'; };
        WorkerDomAdapter.prototype.querySelector = function (el /** TODO #9100 */, selector) {
            throw 'not implemented';
        };
        WorkerDomAdapter.prototype.querySelectorAll = function (el /** TODO #9100 */, selector) { throw 'not implemented'; };
        WorkerDomAdapter.prototype.on = function (el /** TODO #9100 */, evt /** TODO #9100 */, listener /** TODO #9100 */) {
            throw 'not implemented';
        };
        WorkerDomAdapter.prototype.onAndCancel = function (el /** TODO #9100 */, evt /** TODO #9100 */, listener /** TODO #9100 */) {
            throw 'not implemented';
        };
        WorkerDomAdapter.prototype.dispatchEvent = function (el /** TODO #9100 */, evt /** TODO #9100 */) { throw 'not implemented'; };
        WorkerDomAdapter.prototype.createMouseEvent = function (eventType /** TODO #9100 */) { throw 'not implemented'; };
        WorkerDomAdapter.prototype.createEvent = function (eventType) { throw 'not implemented'; };
        WorkerDomAdapter.prototype.preventDefault = function (evt /** TODO #9100 */) { throw 'not implemented'; };
        WorkerDomAdapter.prototype.isPrevented = function (evt /** TODO #9100 */) { throw 'not implemented'; };
        WorkerDomAdapter.prototype.getInnerHTML = function (el /** TODO #9100 */) { throw 'not implemented'; };
        WorkerDomAdapter.prototype.getTemplateContent = function (el /** TODO #9100 */) { throw 'not implemented'; };
        WorkerDomAdapter.prototype.getOuterHTML = function (el /** TODO #9100 */) { throw 'not implemented'; };
        WorkerDomAdapter.prototype.nodeName = function (node /** TODO #9100 */) { throw 'not implemented'; };
        WorkerDomAdapter.prototype.nodeValue = function (node /** TODO #9100 */) { throw 'not implemented'; };
        WorkerDomAdapter.prototype.type = function (node /** TODO #9100 */) { throw 'not implemented'; };
        WorkerDomAdapter.prototype.content = function (node /** TODO #9100 */) { throw 'not implemented'; };
        WorkerDomAdapter.prototype.firstChild = function (el /** TODO #9100 */) { throw 'not implemented'; };
        WorkerDomAdapter.prototype.nextSibling = function (el /** TODO #9100 */) { throw 'not implemented'; };
        WorkerDomAdapter.prototype.parentElement = function (el /** TODO #9100 */) { throw 'not implemented'; };
        WorkerDomAdapter.prototype.childNodes = function (el /** TODO #9100 */) { throw 'not implemented'; };
        WorkerDomAdapter.prototype.childNodesAsList = function (el /** TODO #9100 */) { throw 'not implemented'; };
        WorkerDomAdapter.prototype.clearNodes = function (el /** TODO #9100 */) { throw 'not implemented'; };
        WorkerDomAdapter.prototype.appendChild = function (el /** TODO #9100 */, node /** TODO #9100 */) { throw 'not implemented'; };
        WorkerDomAdapter.prototype.removeChild = function (el /** TODO #9100 */, node /** TODO #9100 */) { throw 'not implemented'; };
        WorkerDomAdapter.prototype.replaceChild = function (el /** TODO #9100 */, newNode /** TODO #9100 */, oldNode /** TODO #9100 */) {
            throw 'not implemented';
        };
        WorkerDomAdapter.prototype.remove = function (el /** TODO #9100 */) { throw 'not implemented'; };
        WorkerDomAdapter.prototype.insertBefore = function (el /** TODO #9100 */, node /** TODO #9100 */) { throw 'not implemented'; };
        WorkerDomAdapter.prototype.insertAllBefore = function (el /** TODO #9100 */, nodes /** TODO #9100 */) {
            throw 'not implemented';
        };
        WorkerDomAdapter.prototype.insertAfter = function (el /** TODO #9100 */, node /** TODO #9100 */) { throw 'not implemented'; };
        WorkerDomAdapter.prototype.setInnerHTML = function (el /** TODO #9100 */, value /** TODO #9100 */) { throw 'not implemented'; };
        WorkerDomAdapter.prototype.getText = function (el /** TODO #9100 */) { throw 'not implemented'; };
        WorkerDomAdapter.prototype.setText = function (el /** TODO #9100 */, value) { throw 'not implemented'; };
        WorkerDomAdapter.prototype.getValue = function (el /** TODO #9100 */) { throw 'not implemented'; };
        WorkerDomAdapter.prototype.setValue = function (el /** TODO #9100 */, value) { throw 'not implemented'; };
        WorkerDomAdapter.prototype.getChecked = function (el /** TODO #9100 */) { throw 'not implemented'; };
        WorkerDomAdapter.prototype.setChecked = function (el /** TODO #9100 */, value) { throw 'not implemented'; };
        WorkerDomAdapter.prototype.createComment = function (text) { throw 'not implemented'; };
        WorkerDomAdapter.prototype.createTemplate = function (html /** TODO #9100 */) { throw 'not implemented'; };
        WorkerDomAdapter.prototype.createElement = function (tagName /** TODO #9100 */, doc /** TODO #9100 */) {
            throw 'not implemented';
        };
        WorkerDomAdapter.prototype.createElementNS = function (ns, tagName, doc /** TODO #9100 */) {
            throw 'not implemented';
        };
        WorkerDomAdapter.prototype.createTextNode = function (text, doc /** TODO #9100 */) { throw 'not implemented'; };
        WorkerDomAdapter.prototype.createScriptTag = function (attrName, attrValue, doc /** TODO #9100 */) {
            throw 'not implemented';
        };
        WorkerDomAdapter.prototype.createStyleElement = function (css, doc /** TODO #9100 */) {
            throw 'not implemented';
        };
        WorkerDomAdapter.prototype.createShadowRoot = function (el /** TODO #9100 */) { throw 'not implemented'; };
        WorkerDomAdapter.prototype.getShadowRoot = function (el /** TODO #9100 */) { throw 'not implemented'; };
        WorkerDomAdapter.prototype.getHost = function (el /** TODO #9100 */) { throw 'not implemented'; };
        WorkerDomAdapter.prototype.getDistributedNodes = function (el /** TODO #9100 */) { throw 'not implemented'; };
        WorkerDomAdapter.prototype.clone = function (node) { throw 'not implemented'; };
        WorkerDomAdapter.prototype.getElementsByClassName = function (element /** TODO #9100 */, name) {
            throw 'not implemented';
        };
        WorkerDomAdapter.prototype.getElementsByTagName = function (element /** TODO #9100 */, name) {
            throw 'not implemented';
        };
        WorkerDomAdapter.prototype.classList = function (element /** TODO #9100 */) { throw 'not implemented'; };
        WorkerDomAdapter.prototype.addClass = function (element /** TODO #9100 */, className) { throw 'not implemented'; };
        WorkerDomAdapter.prototype.removeClass = function (element /** TODO #9100 */, className) { throw 'not implemented'; };
        WorkerDomAdapter.prototype.hasClass = function (element /** TODO #9100 */, className) { throw 'not implemented'; };
        WorkerDomAdapter.prototype.setStyle = function (element /** TODO #9100 */, styleName, styleValue) {
            throw 'not implemented';
        };
        WorkerDomAdapter.prototype.removeStyle = function (element /** TODO #9100 */, styleName) { throw 'not implemented'; };
        WorkerDomAdapter.prototype.getStyle = function (element /** TODO #9100 */, styleName) { throw 'not implemented'; };
        WorkerDomAdapter.prototype.hasStyle = function (element /** TODO #9100 */, styleName, styleValue) {
            throw 'not implemented';
        };
        WorkerDomAdapter.prototype.tagName = function (element /** TODO #9100 */) { throw 'not implemented'; };
        WorkerDomAdapter.prototype.attributeMap = function (element /** TODO #9100 */) { throw 'not implemented'; };
        WorkerDomAdapter.prototype.hasAttribute = function (element /** TODO #9100 */, attribute) {
            throw 'not implemented';
        };
        WorkerDomAdapter.prototype.hasAttributeNS = function (element /** TODO #9100 */, ns, attribute) {
            throw 'not implemented';
        };
        WorkerDomAdapter.prototype.getAttribute = function (element /** TODO #9100 */, attribute) {
            throw 'not implemented';
        };
        WorkerDomAdapter.prototype.getAttributeNS = function (element /** TODO #9100 */, ns, attribute) {
            throw 'not implemented';
        };
        WorkerDomAdapter.prototype.setAttribute = function (element /** TODO #9100 */, name, value) {
            throw 'not implemented';
        };
        WorkerDomAdapter.prototype.setAttributeNS = function (element /** TODO #9100 */, ns, name, value) {
            throw 'not implemented';
        };
        WorkerDomAdapter.prototype.removeAttribute = function (element /** TODO #9100 */, attribute) { throw 'not implemented'; };
        WorkerDomAdapter.prototype.removeAttributeNS = function (element /** TODO #9100 */, ns, attribute) {
            throw 'not implemented';
        };
        WorkerDomAdapter.prototype.templateAwareRoot = function (el /** TODO #9100 */) { throw 'not implemented'; };
        WorkerDomAdapter.prototype.createHtmlDocument = function () { throw 'not implemented'; };
        WorkerDomAdapter.prototype.defaultDoc = function () { throw 'not implemented'; };
        WorkerDomAdapter.prototype.getBoundingClientRect = function (el /** TODO #9100 */) { throw 'not implemented'; };
        WorkerDomAdapter.prototype.getTitle = function () { throw 'not implemented'; };
        WorkerDomAdapter.prototype.setTitle = function (newTitle) { throw 'not implemented'; };
        WorkerDomAdapter.prototype.elementMatches = function (n /** TODO #9100 */, selector) { throw 'not implemented'; };
        WorkerDomAdapter.prototype.isTemplateElement = function (el) { throw 'not implemented'; };
        WorkerDomAdapter.prototype.isTextNode = function (node /** TODO #9100 */) { throw 'not implemented'; };
        WorkerDomAdapter.prototype.isCommentNode = function (node /** TODO #9100 */) { throw 'not implemented'; };
        WorkerDomAdapter.prototype.isElementNode = function (node /** TODO #9100 */) { throw 'not implemented'; };
        WorkerDomAdapter.prototype.hasShadowRoot = function (node /** TODO #9100 */) { throw 'not implemented'; };
        WorkerDomAdapter.prototype.isShadowRoot = function (node /** TODO #9100 */) { throw 'not implemented'; };
        WorkerDomAdapter.prototype.importIntoDoc = function (node) { throw 'not implemented'; };
        WorkerDomAdapter.prototype.adoptNode = function (node) { throw 'not implemented'; };
        WorkerDomAdapter.prototype.getHref = function (element /** TODO #9100 */) { throw 'not implemented'; };
        WorkerDomAdapter.prototype.getEventKey = function (event /** TODO #9100 */) { throw 'not implemented'; };
        WorkerDomAdapter.prototype.resolveAndSetHref = function (element /** TODO #9100 */, baseUrl, href) {
            throw 'not implemented';
        };
        WorkerDomAdapter.prototype.supportsDOMEvents = function () { throw 'not implemented'; };
        WorkerDomAdapter.prototype.supportsNativeShadowDOM = function () { throw 'not implemented'; };
        WorkerDomAdapter.prototype.getGlobalEventTarget = function (target) { throw 'not implemented'; };
        WorkerDomAdapter.prototype.getHistory = function () { throw 'not implemented'; };
        WorkerDomAdapter.prototype.getLocation = function () { throw 'not implemented'; };
        WorkerDomAdapter.prototype.getBaseHref = function () { throw 'not implemented'; };
        WorkerDomAdapter.prototype.resetBaseElement = function () { throw 'not implemented'; };
        WorkerDomAdapter.prototype.getUserAgent = function () { throw 'not implemented'; };
        WorkerDomAdapter.prototype.setData = function (element /** TODO #9100 */, name, value) { throw 'not implemented'; };
        WorkerDomAdapter.prototype.getComputedStyle = function (element /** TODO #9100 */) { throw 'not implemented'; };
        WorkerDomAdapter.prototype.getData = function (element /** TODO #9100 */, name) { throw 'not implemented'; };
        WorkerDomAdapter.prototype.setGlobalVar = function (name, value) { throw 'not implemented'; };
        WorkerDomAdapter.prototype.requestAnimationFrame = function (callback /** TODO #9100 */) { throw 'not implemented'; };
        WorkerDomAdapter.prototype.cancelAnimationFrame = function (id /** TODO #9100 */) { throw 'not implemented'; };
        WorkerDomAdapter.prototype.performanceNow = function () { throw 'not implemented'; };
        WorkerDomAdapter.prototype.getAnimationPrefix = function () { throw 'not implemented'; };
        WorkerDomAdapter.prototype.getTransitionEnd = function () { throw 'not implemented'; };
        WorkerDomAdapter.prototype.supportsAnimation = function () { throw 'not implemented'; };
        WorkerDomAdapter.prototype.supportsWebAnimation = function () { throw 'not implemented'; };
        WorkerDomAdapter.prototype.supportsCookies = function () { return false; };
        WorkerDomAdapter.prototype.getCookie = function (name) { throw 'not implemented'; };
        WorkerDomAdapter.prototype.setCookie = function (name, value) { throw 'not implemented'; };
        return WorkerDomAdapter;
    }(DomAdapter));
    var PrintLogger = (function () {
        function PrintLogger() {
            this.log = print;
            this.logError = print;
            this.logGroup = print;
        }
        PrintLogger.prototype.logGroupEnd = function () { };
        return PrintLogger;
    }());
    var WORKER_APP_PLATFORM_MARKER = new _angular_core.OpaqueToken('WorkerAppPlatformMarker');
    /**
     * @experimental
     */
    var WORKER_APP_PLATFORM_PROVIDERS = [_angular_core.PLATFORM_COMMON_PROVIDERS, { provide: WORKER_APP_PLATFORM_MARKER, useValue: true }];
    /**
     * @experimental
     */
    var WORKER_APP_APPLICATION_PROVIDERS = [
        _angular_core.APPLICATION_COMMON_PROVIDERS, _angular_common.FORM_PROVIDERS, BROWSER_SANITIZATION_PROVIDERS, Serializer,
        { provide: ClientMessageBrokerFactory, useClass: ClientMessageBrokerFactory_ },
        { provide: ServiceMessageBrokerFactory, useClass: ServiceMessageBrokerFactory_ },
        WebWorkerRootRenderer, { provide: _angular_core.RootRenderer, useExisting: WebWorkerRootRenderer },
        { provide: ON_WEB_WORKER, useValue: true }, RenderStore,
        { provide: _angular_core.ExceptionHandler, useFactory: _exceptionHandler$2, deps: [] },
        { provide: MessageBus, useFactory: createMessageBus, deps: [_angular_core.NgZone] },
        { provide: _angular_core.APP_INITIALIZER, useValue: setupWebWorker, multi: true }
    ];
    /**
     * @experimental
     */
    function workerAppPlatform() {
        if (isBlank(_angular_core.getPlatform())) {
            _angular_core.createPlatform(_angular_core.ReflectiveInjector.resolveAndCreate(WORKER_APP_PLATFORM_PROVIDERS));
        }
        return _angular_core.assertPlatform(WORKER_APP_PLATFORM_MARKER);
    }
    function _exceptionHandler$2() {
        return new _angular_core.ExceptionHandler(new PrintLogger());
    }
    // TODO(jteplitz602) remove this and compile with lib.webworker.d.ts (#3492)
    var _postMessage = {
        postMessage: function (message, transferrables) {
            postMessage(message, transferrables);
        }
    };
    function createMessageBus(zone) {
        var sink = new PostMessageBusSink(_postMessage);
        var source = new PostMessageBusSource();
        var bus = new PostMessageBus(sink, source);
        bus.attachToZone(zone);
        return bus;
    }
    function setupWebWorker() {
        WorkerDomAdapter.makeCurrent();
    }
    var __platform_browser_private__ = {
        DomAdapter: DomAdapter,
        getDOM: getDOM,
        setRootDomAdapter: setRootDomAdapter,
        DomRootRenderer: DomRootRenderer,
        DomRootRenderer_: DomRootRenderer_,
        DomSharedStylesHost: DomSharedStylesHost,
        SharedStylesHost: SharedStylesHost,
        ELEMENT_PROBE_PROVIDERS: ELEMENT_PROBE_PROVIDERS,
        DomEventsPlugin: DomEventsPlugin
    };
    exports.BrowserPlatformLocation = BrowserPlatformLocation;
    exports.Title = Title;
    exports.disableDebugTools = disableDebugTools;
    exports.enableDebugTools = enableDebugTools;
    exports.By = By;
    exports.DOCUMENT = DOCUMENT;
    exports.EVENT_MANAGER_PLUGINS = EVENT_MANAGER_PLUGINS;
    exports.EventManager = EventManager;
    exports.HAMMER_GESTURE_CONFIG = HAMMER_GESTURE_CONFIG;
    exports.HammerGestureConfig = HammerGestureConfig;
    exports.DomSanitizationService = DomSanitizationService;
    exports.SecurityContext = SecurityContext;
    exports.ClientMessageBroker = ClientMessageBroker;
    exports.ClientMessageBrokerFactory = ClientMessageBrokerFactory;
    exports.FnArg = FnArg;
    exports.UiArguments = UiArguments;
    exports.ReceivedMessage = ReceivedMessage;
    exports.ServiceMessageBroker = ServiceMessageBroker;
    exports.ServiceMessageBrokerFactory = ServiceMessageBrokerFactory;
    exports.PRIMITIVE = PRIMITIVE;
    exports.WORKER_APP_LOCATION_PROVIDERS = WORKER_APP_LOCATION_PROVIDERS;
    exports.WORKER_UI_LOCATION_PROVIDERS = WORKER_UI_LOCATION_PROVIDERS;
    exports.BROWSER_PLATFORM_PROVIDERS = BROWSER_PLATFORM_PROVIDERS;
    exports.BROWSER_SANITIZATION_PROVIDERS = BROWSER_SANITIZATION_PROVIDERS;
    exports.BROWSER_APP_PROVIDERS = BROWSER_APP_PROVIDERS;
    exports.browserPlatform = browserPlatform;
    exports.MessageBus = MessageBus;
    exports.WebWorkerInstance = WebWorkerInstance;
    exports.WORKER_SCRIPT = WORKER_SCRIPT;
    exports.WORKER_UI_STARTABLE_MESSAGING_SERVICE = WORKER_UI_STARTABLE_MESSAGING_SERVICE;
    exports.WORKER_UI_PLATFORM_PROVIDERS = WORKER_UI_PLATFORM_PROVIDERS;
    exports.WORKER_UI_APPLICATION_PROVIDERS = WORKER_UI_APPLICATION_PROVIDERS;
    exports.workerUiPlatform = workerUiPlatform;
    exports.WORKER_APP_PLATFORM_PROVIDERS = WORKER_APP_PLATFORM_PROVIDERS;
    exports.WORKER_APP_APPLICATION_PROVIDERS = WORKER_APP_APPLICATION_PROVIDERS;
    exports.workerAppPlatform = workerAppPlatform;
    exports.__platform_browser_private__ = __platform_browser_private__;
}));
