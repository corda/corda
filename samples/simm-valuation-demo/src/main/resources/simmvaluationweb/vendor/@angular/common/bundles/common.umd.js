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
    typeof exports === 'object' && typeof module !== 'undefined' ? factory(exports, require('@angular/core'), require('rxjs/Subject'), require('rxjs/observable/PromiseObservable'), require('rxjs/operator/toPromise'), require('rxjs/Observable')) :
        typeof define === 'function' && define.amd ? define(['exports', '@angular/core', 'rxjs/Subject', 'rxjs/observable/PromiseObservable', 'rxjs/operator/toPromise', 'rxjs/Observable'], factory) :
            (factory((global.ng = global.ng || {}, global.ng.common = global.ng.common || {}), global.ng.core, global.Rx, global.Rx, global.Rx.Observable.prototype, global.Rx));
}(this, function (exports, _angular_core, rxjs_Subject, rxjs_observable_PromiseObservable, rxjs_operator_toPromise, rxjs_Observable) {
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
    function getTypeNameForDebugging(type) {
        if (type['name']) {
            return type['name'];
        }
        return typeof type;
    }
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
    function isStringMap(obj) {
        return typeof obj === 'object' && obj !== null;
    }
    function isPromise(obj) {
        return obj instanceof global$1.Promise;
    }
    function isArray(obj) {
        return Array.isArray(obj);
    }
    function isDate(obj) {
        return obj instanceof Date && !isNaN(obj.valueOf());
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
    // JS has NaN !== NaN
    function looseIdentical(a, b) {
        return a === b || typeof a === 'number' && typeof b === 'number' && isNaN(a) && isNaN(b);
    }
    function normalizeBlank(obj) {
        return isBlank(obj) ? null : obj;
    }
    function normalizeBool(obj) {
        return isBlank(obj) ? false : obj;
    }
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
    function isPrimitive(obj) {
        return !isJsObject(obj);
    }
    function hasConstructor(value, type) {
        return value.constructor === type;
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
    var EventEmitter$1 = (function (_super) {
        __extends(EventEmitter$1, _super);
        /**
         * Creates an instance of [EventEmitter], which depending on [isAsync],
         * delivers events synchronously or asynchronously.
         */
        function EventEmitter$1(isAsync) {
            if (isAsync === void 0) { isAsync = false; }
            _super.call(this);
            this.__isAsync = isAsync;
        }
        EventEmitter$1.prototype.emit = function (value) { _super.prototype.next.call(this, value); };
        /**
         * @deprecated - use .emit(value) instead
         */
        EventEmitter$1.prototype.next = function (value) { _super.prototype.next.call(this, value); };
        EventEmitter$1.prototype.subscribe = function (generatorOrNext, error, complete) {
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
        return EventEmitter$1;
    }(rxjs_Subject.Subject));
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
    function unimplemented() {
        throw new BaseException('unimplemented');
    }
    var InvalidPipeArgumentException = (function (_super) {
        __extends(InvalidPipeArgumentException, _super);
        function InvalidPipeArgumentException(type, value) {
            _super.call(this, "Invalid argument '" + value + "' for pipe '" + stringify(type) + "'");
        }
        return InvalidPipeArgumentException;
    }(BaseException));
    var ObservableStrategy = (function () {
        function ObservableStrategy() {
        }
        ObservableStrategy.prototype.createSubscription = function (async, updateLatestValue) {
            return ObservableWrapper.subscribe(async, updateLatestValue, function (e) { throw e; });
        };
        ObservableStrategy.prototype.dispose = function (subscription) { ObservableWrapper.dispose(subscription); };
        ObservableStrategy.prototype.onDestroy = function (subscription) { ObservableWrapper.dispose(subscription); };
        return ObservableStrategy;
    }());
    var PromiseStrategy = (function () {
        function PromiseStrategy() {
        }
        PromiseStrategy.prototype.createSubscription = function (async, updateLatestValue) {
            return async.then(updateLatestValue, function (e) { throw e; });
        };
        PromiseStrategy.prototype.dispose = function (subscription) { };
        PromiseStrategy.prototype.onDestroy = function (subscription) { };
        return PromiseStrategy;
    }());
    var _promiseStrategy = new PromiseStrategy();
    var _observableStrategy = new ObservableStrategy();
    var AsyncPipe = (function () {
        function AsyncPipe(_ref) {
            /** @internal */
            this._latestValue = null;
            /** @internal */
            this._latestReturnedValue = null;
            /** @internal */
            this._subscription = null;
            /** @internal */
            this._obj = null;
            this._strategy = null;
            this._ref = _ref;
        }
        AsyncPipe.prototype.ngOnDestroy = function () {
            if (isPresent(this._subscription)) {
                this._dispose();
            }
        };
        AsyncPipe.prototype.transform = function (obj) {
            if (isBlank(this._obj)) {
                if (isPresent(obj)) {
                    this._subscribe(obj);
                }
                this._latestReturnedValue = this._latestValue;
                return this._latestValue;
            }
            if (obj !== this._obj) {
                this._dispose();
                return this.transform(obj);
            }
            if (this._latestValue === this._latestReturnedValue) {
                return this._latestReturnedValue;
            }
            else {
                this._latestReturnedValue = this._latestValue;
                return _angular_core.WrappedValue.wrap(this._latestValue);
            }
        };
        /** @internal */
        AsyncPipe.prototype._subscribe = function (obj) {
            var _this = this;
            this._obj = obj;
            this._strategy = this._selectStrategy(obj);
            this._subscription = this._strategy.createSubscription(obj, function (value) { return _this._updateLatestValue(obj, value); });
        };
        /** @internal */
        AsyncPipe.prototype._selectStrategy = function (obj) {
            if (isPromise(obj)) {
                return _promiseStrategy;
            }
            else if (ObservableWrapper.isObservable(obj)) {
                return _observableStrategy;
            }
            else {
                throw new InvalidPipeArgumentException(AsyncPipe, obj);
            }
        };
        /** @internal */
        AsyncPipe.prototype._dispose = function () {
            this._strategy.dispose(this._subscription);
            this._latestValue = null;
            this._latestReturnedValue = null;
            this._subscription = null;
            this._obj = null;
        };
        /** @internal */
        AsyncPipe.prototype._updateLatestValue = function (async, value) {
            if (async === this._obj) {
                this._latestValue = value;
                this._ref.markForCheck();
            }
        };
        return AsyncPipe;
    }());
    /** @nocollapse */
    AsyncPipe.decorators = [
        { type: _angular_core.Pipe, args: [{ name: 'async', pure: false },] },
    ];
    /** @nocollapse */
    AsyncPipe.ctorParameters = [
        { type: _angular_core.ChangeDetectorRef, },
    ];
    /**
     * @license
     * Copyright Google Inc. All Rights Reserved.
     *
     * Use of this source code is governed by an MIT-style license that can be
     * found in the LICENSE file at https://angular.io/license
     */
    var NumberFormatStyle;
    (function (NumberFormatStyle) {
        NumberFormatStyle[NumberFormatStyle["Decimal"] = 0] = "Decimal";
        NumberFormatStyle[NumberFormatStyle["Percent"] = 1] = "Percent";
        NumberFormatStyle[NumberFormatStyle["Currency"] = 2] = "Currency";
    })(NumberFormatStyle || (NumberFormatStyle = {}));
    var NumberFormatter = (function () {
        function NumberFormatter() {
        }
        NumberFormatter.format = function (num, locale, style, _a) {
            var _b = _a === void 0 ? {} : _a, _c = _b.minimumIntegerDigits, minimumIntegerDigits = _c === void 0 ? 1 : _c, _d = _b.minimumFractionDigits, minimumFractionDigits = _d === void 0 ? 0 : _d, _e = _b.maximumFractionDigits, maximumFractionDigits = _e === void 0 ? 3 : _e, currency = _b.currency, _f = _b.currencyAsSymbol, currencyAsSymbol = _f === void 0 ? false : _f;
            var intlOptions = {
                minimumIntegerDigits: minimumIntegerDigits,
                minimumFractionDigits: minimumFractionDigits,
                maximumFractionDigits: maximumFractionDigits
            };
            intlOptions.style = NumberFormatStyle[style].toLowerCase();
            if (style == NumberFormatStyle.Currency) {
                intlOptions.currency = currency;
                intlOptions.currencyDisplay = currencyAsSymbol ? 'symbol' : 'code';
            }
            return new Intl.NumberFormat(locale, intlOptions).format(num);
        };
        return NumberFormatter;
    }());
    var DATE_FORMATS_SPLIT = /((?:[^yMLdHhmsaZEwGjJ']+)|(?:'(?:[^']|'')*')|(?:E+|y+|M+|L+|d+|H+|h+|J+|j+|m+|s+|a|Z|G+|w+))(.*)/;
    var PATTERN_ALIASES = {
        yMMMdjms: datePartGetterFactory(combine([
            digitCondition('year', 1),
            nameCondition('month', 3),
            digitCondition('day', 1),
            digitCondition('hour', 1),
            digitCondition('minute', 1),
            digitCondition('second', 1),
        ])),
        yMdjm: datePartGetterFactory(combine([
            digitCondition('year', 1), digitCondition('month', 1), digitCondition('day', 1),
            digitCondition('hour', 1), digitCondition('minute', 1)
        ])),
        yMMMMEEEEd: datePartGetterFactory(combine([
            digitCondition('year', 1), nameCondition('month', 4), nameCondition('weekday', 4),
            digitCondition('day', 1)
        ])),
        yMMMMd: datePartGetterFactory(combine([digitCondition('year', 1), nameCondition('month', 4), digitCondition('day', 1)])),
        yMMMd: datePartGetterFactory(combine([digitCondition('year', 1), nameCondition('month', 3), digitCondition('day', 1)])),
        yMd: datePartGetterFactory(combine([digitCondition('year', 1), digitCondition('month', 1), digitCondition('day', 1)])),
        jms: datePartGetterFactory(combine([digitCondition('hour', 1), digitCondition('second', 1), digitCondition('minute', 1)])),
        jm: datePartGetterFactory(combine([digitCondition('hour', 1), digitCondition('minute', 1)]))
    };
    var DATE_FORMATS = {
        yyyy: datePartGetterFactory(digitCondition('year', 4)),
        yy: datePartGetterFactory(digitCondition('year', 2)),
        y: datePartGetterFactory(digitCondition('year', 1)),
        MMMM: datePartGetterFactory(nameCondition('month', 4)),
        MMM: datePartGetterFactory(nameCondition('month', 3)),
        MM: datePartGetterFactory(digitCondition('month', 2)),
        M: datePartGetterFactory(digitCondition('month', 1)),
        LLLL: datePartGetterFactory(nameCondition('month', 4)),
        dd: datePartGetterFactory(digitCondition('day', 2)),
        d: datePartGetterFactory(digitCondition('day', 1)),
        HH: hourExtracter(datePartGetterFactory(hour12Modify(digitCondition('hour', 2), false))),
        H: hourExtracter(datePartGetterFactory(hour12Modify(digitCondition('hour', 1), false))),
        hh: hourExtracter(datePartGetterFactory(hour12Modify(digitCondition('hour', 2), true))),
        h: hourExtracter(datePartGetterFactory(hour12Modify(digitCondition('hour', 1), true))),
        jj: datePartGetterFactory(digitCondition('hour', 2)),
        j: datePartGetterFactory(digitCondition('hour', 1)),
        mm: digitModifier(datePartGetterFactory(digitCondition('minute', 2))),
        m: datePartGetterFactory(digitCondition('minute', 1)),
        ss: digitModifier(datePartGetterFactory(digitCondition('second', 2))),
        s: datePartGetterFactory(digitCondition('second', 1)),
        // while ISO 8601 requires fractions to be prefixed with `.` or `,`
        // we can be just safely rely on using `sss` since we currently don't support single or two digit
        // fractions
        sss: datePartGetterFactory(digitCondition('second', 3)),
        EEEE: datePartGetterFactory(nameCondition('weekday', 4)),
        EEE: datePartGetterFactory(nameCondition('weekday', 3)),
        EE: datePartGetterFactory(nameCondition('weekday', 2)),
        E: datePartGetterFactory(nameCondition('weekday', 1)),
        a: hourClockExtracter(datePartGetterFactory(hour12Modify(digitCondition('hour', 1), true))),
        Z: datePartGetterFactory({ timeZoneName: 'long' }),
        z: datePartGetterFactory({ timeZoneName: 'short' }),
        ww: datePartGetterFactory({}),
        // first Thursday of the year. not support ?
        w: datePartGetterFactory({}),
        // of the year not support ?
        G: datePartGetterFactory(nameCondition('era', 1)),
        GG: datePartGetterFactory(nameCondition('era', 2)),
        GGG: datePartGetterFactory(nameCondition('era', 3)),
        GGGG: datePartGetterFactory(nameCondition('era', 4))
    };
    function digitModifier(inner) {
        return function (date, locale) {
            var result = inner(date, locale);
            return result.length == 1 ? '0' + result : result;
        };
    }
    function hourClockExtracter(inner) {
        return function (date, locale) {
            var result = inner(date, locale);
            return result.split(' ')[1];
        };
    }
    function hourExtracter(inner) {
        return function (date, locale) {
            var result = inner(date, locale);
            return result.split(' ')[0];
        };
    }
    function hour12Modify(options, value) {
        options.hour12 = value;
        return options;
    }
    function digitCondition(prop, len) {
        var result = {};
        result[prop] = len == 2 ? '2-digit' : 'numeric';
        return result;
    }
    function nameCondition(prop, len) {
        var result = {};
        result[prop] = len < 4 ? 'short' : 'long';
        return result;
    }
    function combine(options) {
        var result = {};
        options.forEach(function (option) { Object.assign(result, option); });
        return result;
    }
    function datePartGetterFactory(ret) {
        return function (date, locale) {
            return new Intl.DateTimeFormat(locale, ret).format(date);
        };
    }
    var datePartsFormatterCache = new Map();
    function dateFormatter(format, date, locale) {
        var text = '';
        var match;
        var fn;
        var parts = [];
        if (PATTERN_ALIASES[format]) {
            return PATTERN_ALIASES[format](date, locale);
        }
        if (datePartsFormatterCache.has(format)) {
            parts = datePartsFormatterCache.get(format);
        }
        else {
            var matchs = DATE_FORMATS_SPLIT.exec(format);
            while (format) {
                match = DATE_FORMATS_SPLIT.exec(format);
                if (match) {
                    parts = concat(parts, match, 1);
                    format = parts.pop();
                }
                else {
                    parts.push(format);
                    format = null;
                }
            }
            datePartsFormatterCache.set(format, parts);
        }
        parts.forEach(function (part) {
            fn = DATE_FORMATS[part];
            text += fn ? fn(date, locale) :
                part === '\'\'' ? '\'' : part.replace(/(^'|'$)/g, '').replace(/''/g, '\'');
        });
        return text;
    }
    var slice = [].slice;
    function concat(array1 /** TODO #9100 */, array2 /** TODO #9100 */, index /** TODO #9100 */) {
        return array1.concat(slice.call(array2, index));
    }
    var DateFormatter = (function () {
        function DateFormatter() {
        }
        DateFormatter.format = function (date, locale, pattern) {
            return dateFormatter(pattern, date, locale);
        };
        return DateFormatter;
    }());
    // TODO: move to a global configurable location along with other i18n components.
    var defaultLocale = 'en-US';
    var DatePipe = (function () {
        function DatePipe() {
        }
        DatePipe.prototype.transform = function (value, pattern) {
            if (pattern === void 0) { pattern = 'mediumDate'; }
            if (isBlank(value))
                return null;
            if (!this.supports(value)) {
                throw new InvalidPipeArgumentException(DatePipe, value);
            }
            if (NumberWrapper.isNumeric(value)) {
                value = DateWrapper.fromMillis(NumberWrapper.parseInt(value, 10));
            }
            else if (isString(value)) {
                value = DateWrapper.fromISOString(value);
            }
            if (StringMapWrapper.contains(DatePipe._ALIASES, pattern)) {
                pattern = StringMapWrapper.get(DatePipe._ALIASES, pattern);
            }
            return DateFormatter.format(value, defaultLocale, pattern);
        };
        DatePipe.prototype.supports = function (obj) {
            if (isDate(obj) || NumberWrapper.isNumeric(obj)) {
                return true;
            }
            if (isString(obj) && isDate(DateWrapper.fromISOString(obj))) {
                return true;
            }
            return false;
        };
        return DatePipe;
    }());
    /** @internal */
    DatePipe._ALIASES = {
        'medium': 'yMMMdjms',
        'short': 'yMdjm',
        'fullDate': 'yMMMMEEEEd',
        'longDate': 'yMMMMd',
        'mediumDate': 'yMMMd',
        'shortDate': 'yMd',
        'mediumTime': 'jms',
        'shortTime': 'jm'
    };
    /** @nocollapse */
    DatePipe.decorators = [
        { type: _angular_core.Pipe, args: [{ name: 'date', pure: true },] },
    ];
    /**
     * @license
     * Copyright Google Inc. All Rights Reserved.
     *
     * Use of this source code is governed by an MIT-style license that can be
     * found in the LICENSE file at https://angular.io/license
     */
    /**
     * @experimental
     */
    var NgLocalization = (function () {
        function NgLocalization() {
        }
        return NgLocalization;
    }());
    /**
     * Returns the plural category for a given value.
     * - "=value" when the case exists,
     * - the plural category otherwise
     *
     * @internal
     */
    function getPluralCategory(value, cases, ngLocalization) {
        var nbCase = "=" + value;
        return cases.indexOf(nbCase) > -1 ? nbCase : ngLocalization.getPluralCategory(value);
    }
    var _INTERPOLATION_REGEXP = /#/g;
    var I18nPluralPipe = (function () {
        function I18nPluralPipe(_localization) {
            this._localization = _localization;
        }
        I18nPluralPipe.prototype.transform = function (value, pluralMap) {
            if (isBlank(value))
                return '';
            if (!isStringMap(pluralMap)) {
                throw new InvalidPipeArgumentException(I18nPluralPipe, pluralMap);
            }
            var key = getPluralCategory(value, Object.getOwnPropertyNames(pluralMap), this._localization);
            return StringWrapper.replaceAll(pluralMap[key], _INTERPOLATION_REGEXP, value.toString());
        };
        return I18nPluralPipe;
    }());
    /** @nocollapse */
    I18nPluralPipe.decorators = [
        { type: _angular_core.Pipe, args: [{ name: 'i18nPlural', pure: true },] },
    ];
    /** @nocollapse */
    I18nPluralPipe.ctorParameters = [
        { type: NgLocalization, },
    ];
    var I18nSelectPipe = (function () {
        function I18nSelectPipe() {
        }
        I18nSelectPipe.prototype.transform = function (value, mapping) {
            if (isBlank(value))
                return '';
            if (!isStringMap(mapping)) {
                throw new InvalidPipeArgumentException(I18nSelectPipe, mapping);
            }
            return mapping.hasOwnProperty(value) ? mapping[value] : '';
        };
        return I18nSelectPipe;
    }());
    /** @nocollapse */
    I18nSelectPipe.decorators = [
        { type: _angular_core.Pipe, args: [{ name: 'i18nSelect', pure: true },] },
    ];
    var JsonPipe = (function () {
        function JsonPipe() {
        }
        JsonPipe.prototype.transform = function (value) { return Json.stringify(value); };
        return JsonPipe;
    }());
    /** @nocollapse */
    JsonPipe.decorators = [
        { type: _angular_core.Pipe, args: [{ name: 'json', pure: false },] },
    ];
    var LowerCasePipe = (function () {
        function LowerCasePipe() {
        }
        LowerCasePipe.prototype.transform = function (value) {
            if (isBlank(value))
                return value;
            if (!isString(value)) {
                throw new InvalidPipeArgumentException(LowerCasePipe, value);
            }
            return value.toLowerCase();
        };
        return LowerCasePipe;
    }());
    /** @nocollapse */
    LowerCasePipe.decorators = [
        { type: _angular_core.Pipe, args: [{ name: 'lowercase' },] },
    ];
    var defaultLocale$1 = 'en-US';
    var _NUMBER_FORMAT_REGEXP = /^(\d+)?\.((\d+)(\-(\d+))?)?$/g;
    /**
     * Internal function to format numbers used by Decimal, Percent and Date pipes.
     */
    function formatNumber(pipe, value, style, digits, currency, currencyAsSymbol) {
        if (currency === void 0) { currency = null; }
        if (currencyAsSymbol === void 0) { currencyAsSymbol = false; }
        if (isBlank(value))
            return null;
        if (!isNumber(value)) {
            throw new InvalidPipeArgumentException(pipe, value);
        }
        var minInt = 1, minFraction = 0, maxFraction = 3;
        if (isPresent(digits)) {
            var parts = RegExpWrapper.firstMatch(_NUMBER_FORMAT_REGEXP, digits);
            if (isBlank(parts)) {
                throw new BaseException(digits + " is not a valid digit info for number pipes");
            }
            if (isPresent(parts[1])) {
                minInt = NumberWrapper.parseIntAutoRadix(parts[1]);
            }
            if (isPresent(parts[3])) {
                minFraction = NumberWrapper.parseIntAutoRadix(parts[3]);
            }
            if (isPresent(parts[5])) {
                maxFraction = NumberWrapper.parseIntAutoRadix(parts[5]);
            }
        }
        return NumberFormatter.format(value, defaultLocale$1, style, {
            minimumIntegerDigits: minInt,
            minimumFractionDigits: minFraction,
            maximumFractionDigits: maxFraction,
            currency: currency,
            currencyAsSymbol: currencyAsSymbol
        });
    }
    var DecimalPipe = (function () {
        function DecimalPipe() {
        }
        DecimalPipe.prototype.transform = function (value, digits) {
            if (digits === void 0) { digits = null; }
            return formatNumber(DecimalPipe, value, NumberFormatStyle.Decimal, digits);
        };
        return DecimalPipe;
    }());
    /** @nocollapse */
    DecimalPipe.decorators = [
        { type: _angular_core.Pipe, args: [{ name: 'number' },] },
    ];
    var PercentPipe = (function () {
        function PercentPipe() {
        }
        PercentPipe.prototype.transform = function (value, digits) {
            if (digits === void 0) { digits = null; }
            return formatNumber(PercentPipe, value, NumberFormatStyle.Percent, digits);
        };
        return PercentPipe;
    }());
    /** @nocollapse */
    PercentPipe.decorators = [
        { type: _angular_core.Pipe, args: [{ name: 'percent' },] },
    ];
    var CurrencyPipe = (function () {
        function CurrencyPipe() {
        }
        CurrencyPipe.prototype.transform = function (value, currencyCode, symbolDisplay, digits) {
            if (currencyCode === void 0) { currencyCode = 'USD'; }
            if (symbolDisplay === void 0) { symbolDisplay = false; }
            if (digits === void 0) { digits = null; }
            return formatNumber(CurrencyPipe, value, NumberFormatStyle.Currency, digits, currencyCode, symbolDisplay);
        };
        return CurrencyPipe;
    }());
    /** @nocollapse */
    CurrencyPipe.decorators = [
        { type: _angular_core.Pipe, args: [{ name: 'currency' },] },
    ];
    var ReplacePipe = (function () {
        function ReplacePipe() {
        }
        ReplacePipe.prototype.transform = function (value, pattern, replacement) {
            if (isBlank(value)) {
                return value;
            }
            if (!this._supportedInput(value)) {
                throw new InvalidPipeArgumentException(ReplacePipe, value);
            }
            var input = value.toString();
            if (!this._supportedPattern(pattern)) {
                throw new InvalidPipeArgumentException(ReplacePipe, pattern);
            }
            if (!this._supportedReplacement(replacement)) {
                throw new InvalidPipeArgumentException(ReplacePipe, replacement);
            }
            if (isFunction(replacement)) {
                var rgxPattern = isString(pattern) ? RegExpWrapper.create(pattern) : pattern;
                return StringWrapper.replaceAllMapped(input, rgxPattern, replacement);
            }
            if (pattern instanceof RegExp) {
                // use the replaceAll variant
                return StringWrapper.replaceAll(input, pattern, replacement);
            }
            return StringWrapper.replace(input, pattern, replacement);
        };
        ReplacePipe.prototype._supportedInput = function (input) { return isString(input) || isNumber(input); };
        ReplacePipe.prototype._supportedPattern = function (pattern) {
            return isString(pattern) || pattern instanceof RegExp;
        };
        ReplacePipe.prototype._supportedReplacement = function (replacement) {
            return isString(replacement) || isFunction(replacement);
        };
        return ReplacePipe;
    }());
    /** @nocollapse */
    ReplacePipe.decorators = [
        { type: _angular_core.Pipe, args: [{ name: 'replace' },] },
    ];
    var SlicePipe = (function () {
        function SlicePipe() {
        }
        SlicePipe.prototype.transform = function (value, start, end) {
            if (end === void 0) { end = null; }
            if (isBlank(value))
                return value;
            if (!this.supports(value)) {
                throw new InvalidPipeArgumentException(SlicePipe, value);
            }
            if (isString(value)) {
                return StringWrapper.slice(value, start, end);
            }
            return ListWrapper.slice(value, start, end);
        };
        SlicePipe.prototype.supports = function (obj) { return isString(obj) || isArray(obj); };
        return SlicePipe;
    }());
    /** @nocollapse */
    SlicePipe.decorators = [
        { type: _angular_core.Pipe, args: [{ name: 'slice', pure: false },] },
    ];
    var UpperCasePipe = (function () {
        function UpperCasePipe() {
        }
        UpperCasePipe.prototype.transform = function (value) {
            if (isBlank(value))
                return value;
            if (!isString(value)) {
                throw new InvalidPipeArgumentException(UpperCasePipe, value);
            }
            return value.toUpperCase();
        };
        return UpperCasePipe;
    }());
    /** @nocollapse */
    UpperCasePipe.decorators = [
        { type: _angular_core.Pipe, args: [{ name: 'uppercase' },] },
    ];
    /**
     * A collection of Angular core pipes that are likely to be used in each and every
     * application.
     *
     * This collection can be used to quickly enumerate all the built-in pipes in the `pipes`
     * property of the `@Component` decorator.
     *
     * @experimental Contains i18n pipes which are experimental
     */
    var COMMON_PIPES = [
        AsyncPipe,
        UpperCasePipe,
        LowerCasePipe,
        JsonPipe,
        SlicePipe,
        DecimalPipe,
        PercentPipe,
        CurrencyPipe,
        DatePipe,
        ReplacePipe,
        I18nPluralPipe,
        I18nSelectPipe,
    ];
    var NgClass = (function () {
        function NgClass(_iterableDiffers, _keyValueDiffers, _ngEl, _renderer) {
            this._iterableDiffers = _iterableDiffers;
            this._keyValueDiffers = _keyValueDiffers;
            this._ngEl = _ngEl;
            this._renderer = _renderer;
            this._initialClasses = [];
        }
        Object.defineProperty(NgClass.prototype, "initialClasses", {
            set: function (v) {
                this._applyInitialClasses(true);
                this._initialClasses = isPresent(v) && isString(v) ? v.split(' ') : [];
                this._applyInitialClasses(false);
                this._applyClasses(this._rawClass, false);
            },
            enumerable: true,
            configurable: true
        });
        Object.defineProperty(NgClass.prototype, "rawClass", {
            set: function (v) {
                this._cleanupClasses(this._rawClass);
                if (isString(v)) {
                    v = v.split(' ');
                }
                this._rawClass = v;
                this._iterableDiffer = null;
                this._keyValueDiffer = null;
                if (isPresent(v)) {
                    if (isListLikeIterable(v)) {
                        this._iterableDiffer = this._iterableDiffers.find(v).create(null);
                    }
                    else {
                        this._keyValueDiffer = this._keyValueDiffers.find(v).create(null);
                    }
                }
            },
            enumerable: true,
            configurable: true
        });
        NgClass.prototype.ngDoCheck = function () {
            if (isPresent(this._iterableDiffer)) {
                var changes = this._iterableDiffer.diff(this._rawClass);
                if (isPresent(changes)) {
                    this._applyIterableChanges(changes);
                }
            }
            if (isPresent(this._keyValueDiffer)) {
                var changes = this._keyValueDiffer.diff(this._rawClass);
                if (isPresent(changes)) {
                    this._applyKeyValueChanges(changes);
                }
            }
        };
        NgClass.prototype.ngOnDestroy = function () { this._cleanupClasses(this._rawClass); };
        NgClass.prototype._cleanupClasses = function (rawClassVal) {
            this._applyClasses(rawClassVal, true);
            this._applyInitialClasses(false);
        };
        NgClass.prototype._applyKeyValueChanges = function (changes) {
            var _this = this;
            changes.forEachAddedItem(function (record) { _this._toggleClass(record.key, record.currentValue); });
            changes.forEachChangedItem(function (record) { _this._toggleClass(record.key, record.currentValue); });
            changes.forEachRemovedItem(function (record) {
                if (record.previousValue) {
                    _this._toggleClass(record.key, false);
                }
            });
        };
        NgClass.prototype._applyIterableChanges = function (changes) {
            var _this = this;
            changes.forEachAddedItem(function (record) { _this._toggleClass(record.item, true); });
            changes.forEachRemovedItem(function (record) { _this._toggleClass(record.item, false); });
        };
        NgClass.prototype._applyInitialClasses = function (isCleanup) {
            var _this = this;
            this._initialClasses.forEach(function (className) { return _this._toggleClass(className, !isCleanup); });
        };
        NgClass.prototype._applyClasses = function (rawClassVal, isCleanup) {
            var _this = this;
            if (isPresent(rawClassVal)) {
                if (isArray(rawClassVal)) {
                    rawClassVal.forEach(function (className) { return _this._toggleClass(className, !isCleanup); });
                }
                else if (rawClassVal instanceof Set) {
                    rawClassVal.forEach(function (className) { return _this._toggleClass(className, !isCleanup); });
                }
                else {
                    StringMapWrapper.forEach(rawClassVal, function (expVal, className) {
                        if (isPresent(expVal))
                            _this._toggleClass(className, !isCleanup);
                    });
                }
            }
        };
        NgClass.prototype._toggleClass = function (className, enabled) {
            className = className.trim();
            if (className.length > 0) {
                if (className.indexOf(' ') > -1) {
                    var classes = className.split(/\s+/g);
                    for (var i = 0, len = classes.length; i < len; i++) {
                        this._renderer.setElementClass(this._ngEl.nativeElement, classes[i], enabled);
                    }
                }
                else {
                    this._renderer.setElementClass(this._ngEl.nativeElement, className, enabled);
                }
            }
        };
        return NgClass;
    }());
    /** @nocollapse */
    NgClass.decorators = [
        { type: _angular_core.Directive, args: [{ selector: '[ngClass]', inputs: ['rawClass: ngClass', 'initialClasses: class'] },] },
    ];
    /** @nocollapse */
    NgClass.ctorParameters = [
        { type: _angular_core.IterableDiffers, },
        { type: _angular_core.KeyValueDiffers, },
        { type: _angular_core.ElementRef, },
        { type: _angular_core.Renderer, },
    ];
    var NgForRow = (function () {
        function NgForRow($implicit, index, count) {
            this.$implicit = $implicit;
            this.index = index;
            this.count = count;
        }
        Object.defineProperty(NgForRow.prototype, "first", {
            get: function () { return this.index === 0; },
            enumerable: true,
            configurable: true
        });
        Object.defineProperty(NgForRow.prototype, "last", {
            get: function () { return this.index === this.count - 1; },
            enumerable: true,
            configurable: true
        });
        Object.defineProperty(NgForRow.prototype, "even", {
            get: function () { return this.index % 2 === 0; },
            enumerable: true,
            configurable: true
        });
        Object.defineProperty(NgForRow.prototype, "odd", {
            get: function () { return !this.even; },
            enumerable: true,
            configurable: true
        });
        return NgForRow;
    }());
    var NgFor = (function () {
        function NgFor(_viewContainer, _templateRef, _iterableDiffers, _cdr) {
            this._viewContainer = _viewContainer;
            this._templateRef = _templateRef;
            this._iterableDiffers = _iterableDiffers;
            this._cdr = _cdr;
        }
        Object.defineProperty(NgFor.prototype, "ngForOf", {
            set: function (value) {
                this._ngForOf = value;
                if (isBlank(this._differ) && isPresent(value)) {
                    try {
                        this._differ = this._iterableDiffers.find(value).create(this._cdr, this._ngForTrackBy);
                    }
                    catch (e) {
                        throw new BaseException("Cannot find a differ supporting object '" + value + "' of type '" + getTypeNameForDebugging(value) + "'. NgFor only supports binding to Iterables such as Arrays.");
                    }
                }
            },
            enumerable: true,
            configurable: true
        });
        Object.defineProperty(NgFor.prototype, "ngForTemplate", {
            set: function (value) {
                if (isPresent(value)) {
                    this._templateRef = value;
                }
            },
            enumerable: true,
            configurable: true
        });
        Object.defineProperty(NgFor.prototype, "ngForTrackBy", {
            set: function (value) { this._ngForTrackBy = value; },
            enumerable: true,
            configurable: true
        });
        NgFor.prototype.ngDoCheck = function () {
            if (isPresent(this._differ)) {
                var changes = this._differ.diff(this._ngForOf);
                if (isPresent(changes))
                    this._applyChanges(changes);
            }
        };
        NgFor.prototype._applyChanges = function (changes) {
            var _this = this;
            // TODO(rado): check if change detection can produce a change record that is
            // easier to consume than current.
            var recordViewTuples = [];
            changes.forEachRemovedItem(function (removedRecord) { return recordViewTuples.push(new RecordViewTuple(removedRecord, null)); });
            changes.forEachMovedItem(function (movedRecord) { return recordViewTuples.push(new RecordViewTuple(movedRecord, null)); });
            var insertTuples = this._bulkRemove(recordViewTuples);
            changes.forEachAddedItem(function (addedRecord) { return insertTuples.push(new RecordViewTuple(addedRecord, null)); });
            this._bulkInsert(insertTuples);
            for (var i = 0; i < insertTuples.length; i++) {
                this._perViewChange(insertTuples[i].view, insertTuples[i].record);
            }
            for (var i = 0, ilen = this._viewContainer.length; i < ilen; i++) {
                var viewRef = this._viewContainer.get(i);
                viewRef.context.index = i;
                viewRef.context.count = ilen;
            }
            changes.forEachIdentityChange(function (record /** TODO #9100 */) {
                var viewRef = _this._viewContainer.get(record.currentIndex);
                viewRef.context.$implicit = record.item;
            });
        };
        NgFor.prototype._perViewChange = function (view, record) {
            view.context.$implicit = record.item;
        };
        NgFor.prototype._bulkRemove = function (tuples) {
            tuples.sort(function (a, b) { return a.record.previousIndex - b.record.previousIndex; });
            var movedTuples = [];
            for (var i = tuples.length - 1; i >= 0; i--) {
                var tuple = tuples[i];
                // separate moved views from removed views.
                if (isPresent(tuple.record.currentIndex)) {
                    tuple.view =
                        this._viewContainer.detach(tuple.record.previousIndex);
                    movedTuples.push(tuple);
                }
                else {
                    this._viewContainer.remove(tuple.record.previousIndex);
                }
            }
            return movedTuples;
        };
        NgFor.prototype._bulkInsert = function (tuples) {
            tuples.sort(function (a, b) { return a.record.currentIndex - b.record.currentIndex; });
            for (var i = 0; i < tuples.length; i++) {
                var tuple = tuples[i];
                if (isPresent(tuple.view)) {
                    this._viewContainer.insert(tuple.view, tuple.record.currentIndex);
                }
                else {
                    tuple.view = this._viewContainer.createEmbeddedView(this._templateRef, new NgForRow(null, null, null), tuple.record.currentIndex);
                }
            }
            return tuples;
        };
        return NgFor;
    }());
    /** @nocollapse */
    NgFor.decorators = [
        { type: _angular_core.Directive, args: [{ selector: '[ngFor][ngForOf]', inputs: ['ngForTrackBy', 'ngForOf', 'ngForTemplate'] },] },
    ];
    /** @nocollapse */
    NgFor.ctorParameters = [
        { type: _angular_core.ViewContainerRef, },
        { type: _angular_core.TemplateRef, },
        { type: _angular_core.IterableDiffers, },
        { type: _angular_core.ChangeDetectorRef, },
    ];
    var RecordViewTuple = (function () {
        function RecordViewTuple(record, view) {
            this.record = record;
            this.view = view;
        }
        return RecordViewTuple;
    }());
    var NgIf = (function () {
        function NgIf(_viewContainer, _templateRef) {
            this._viewContainer = _viewContainer;
            this._templateRef = _templateRef;
            this._prevCondition = null;
        }
        Object.defineProperty(NgIf.prototype, "ngIf", {
            set: function (newCondition /* boolean */) {
                if (newCondition && (isBlank(this._prevCondition) || !this._prevCondition)) {
                    this._prevCondition = true;
                    this._viewContainer.createEmbeddedView(this._templateRef);
                }
                else if (!newCondition && (isBlank(this._prevCondition) || this._prevCondition)) {
                    this._prevCondition = false;
                    this._viewContainer.clear();
                }
            },
            enumerable: true,
            configurable: true
        });
        return NgIf;
    }());
    /** @nocollapse */
    NgIf.decorators = [
        { type: _angular_core.Directive, args: [{ selector: '[ngIf]', inputs: ['ngIf'] },] },
    ];
    /** @nocollapse */
    NgIf.ctorParameters = [
        { type: _angular_core.ViewContainerRef, },
        { type: _angular_core.TemplateRef, },
    ];
    var _CASE_DEFAULT = new Object();
    // TODO: remove when fully deprecated
    var _warned = false;
    var SwitchView = (function () {
        function SwitchView(_viewContainerRef, _templateRef) {
            this._viewContainerRef = _viewContainerRef;
            this._templateRef = _templateRef;
        }
        SwitchView.prototype.create = function () { this._viewContainerRef.createEmbeddedView(this._templateRef); };
        SwitchView.prototype.destroy = function () { this._viewContainerRef.clear(); };
        return SwitchView;
    }());
    var NgSwitch = (function () {
        function NgSwitch() {
            this._useDefault = false;
            this._valueViews = new Map$1();
            this._activeViews = [];
        }
        Object.defineProperty(NgSwitch.prototype, "ngSwitch", {
            set: function (value) {
                // Empty the currently active ViewContainers
                this._emptyAllActiveViews();
                // Add the ViewContainers matching the value (with a fallback to default)
                this._useDefault = false;
                var views = this._valueViews.get(value);
                if (isBlank(views)) {
                    this._useDefault = true;
                    views = normalizeBlank(this._valueViews.get(_CASE_DEFAULT));
                }
                this._activateViews(views);
                this._switchValue = value;
            },
            enumerable: true,
            configurable: true
        });
        /** @internal */
        NgSwitch.prototype._onCaseValueChanged = function (oldCase, newCase, view) {
            this._deregisterView(oldCase, view);
            this._registerView(newCase, view);
            if (oldCase === this._switchValue) {
                view.destroy();
                ListWrapper.remove(this._activeViews, view);
            }
            else if (newCase === this._switchValue) {
                if (this._useDefault) {
                    this._useDefault = false;
                    this._emptyAllActiveViews();
                }
                view.create();
                this._activeViews.push(view);
            }
            // Switch to default when there is no more active ViewContainers
            if (this._activeViews.length === 0 && !this._useDefault) {
                this._useDefault = true;
                this._activateViews(this._valueViews.get(_CASE_DEFAULT));
            }
        };
        /** @internal */
        NgSwitch.prototype._emptyAllActiveViews = function () {
            var activeContainers = this._activeViews;
            for (var i = 0; i < activeContainers.length; i++) {
                activeContainers[i].destroy();
            }
            this._activeViews = [];
        };
        /** @internal */
        NgSwitch.prototype._activateViews = function (views) {
            // TODO(vicb): assert(this._activeViews.length === 0);
            if (isPresent(views)) {
                for (var i = 0; i < views.length; i++) {
                    views[i].create();
                }
                this._activeViews = views;
            }
        };
        /** @internal */
        NgSwitch.prototype._registerView = function (value, view) {
            var views = this._valueViews.get(value);
            if (isBlank(views)) {
                views = [];
                this._valueViews.set(value, views);
            }
            views.push(view);
        };
        /** @internal */
        NgSwitch.prototype._deregisterView = function (value, view) {
            // `_CASE_DEFAULT` is used a marker for non-registered cases
            if (value === _CASE_DEFAULT)
                return;
            var views = this._valueViews.get(value);
            if (views.length == 1) {
                this._valueViews.delete(value);
            }
            else {
                ListWrapper.remove(views, view);
            }
        };
        return NgSwitch;
    }());
    /** @nocollapse */
    NgSwitch.decorators = [
        { type: _angular_core.Directive, args: [{ selector: '[ngSwitch]', inputs: ['ngSwitch'] },] },
    ];
    var NgSwitchCase = (function () {
        function NgSwitchCase(viewContainer, templateRef, ngSwitch) {
            // `_CASE_DEFAULT` is used as a marker for a not yet initialized value
            /** @internal */
            this._value = _CASE_DEFAULT;
            this._switch = ngSwitch;
            this._view = new SwitchView(viewContainer, templateRef);
        }
        Object.defineProperty(NgSwitchCase.prototype, "ngSwitchCase", {
            set: function (value) {
                this._switch._onCaseValueChanged(this._value, value, this._view);
                this._value = value;
            },
            enumerable: true,
            configurable: true
        });
        Object.defineProperty(NgSwitchCase.prototype, "ngSwitchWhen", {
            set: function (value) {
                if (!_warned) {
                    _warned = true;
                    console.warn('*ngSwitchWhen is deprecated and will be removed. Use *ngSwitchCase instead');
                }
                this._switch._onCaseValueChanged(this._value, value, this._view);
                this._value = value;
            },
            enumerable: true,
            configurable: true
        });
        return NgSwitchCase;
    }());
    /** @nocollapse */
    NgSwitchCase.decorators = [
        { type: _angular_core.Directive, args: [{ selector: '[ngSwitchCase],[ngSwitchWhen]', inputs: ['ngSwitchCase', 'ngSwitchWhen'] },] },
    ];
    /** @nocollapse */
    NgSwitchCase.ctorParameters = [
        { type: _angular_core.ViewContainerRef, },
        { type: _angular_core.TemplateRef, },
        { type: NgSwitch, decorators: [{ type: _angular_core.Host },] },
    ];
    var NgSwitchDefault = (function () {
        function NgSwitchDefault(viewContainer, templateRef, sswitch) {
            sswitch._registerView(_CASE_DEFAULT, new SwitchView(viewContainer, templateRef));
        }
        return NgSwitchDefault;
    }());
    /** @nocollapse */
    NgSwitchDefault.decorators = [
        { type: _angular_core.Directive, args: [{ selector: '[ngSwitchDefault]' },] },
    ];
    /** @nocollapse */
    NgSwitchDefault.ctorParameters = [
        { type: _angular_core.ViewContainerRef, },
        { type: _angular_core.TemplateRef, },
        { type: NgSwitch, decorators: [{ type: _angular_core.Host },] },
    ];
    var NgPluralCase = (function () {
        function NgPluralCase(value, template, viewContainer) {
            this.value = value;
            this._view = new SwitchView(viewContainer, template);
        }
        return NgPluralCase;
    }());
    /** @nocollapse */
    NgPluralCase.decorators = [
        { type: _angular_core.Directive, args: [{ selector: '[ngPluralCase]' },] },
    ];
    /** @nocollapse */
    NgPluralCase.ctorParameters = [
        { type: undefined, decorators: [{ type: _angular_core.Attribute, args: ['ngPluralCase',] },] },
        { type: _angular_core.TemplateRef, },
        { type: _angular_core.ViewContainerRef, },
    ];
    var NgPlural = (function () {
        function NgPlural(_localization) {
            this._localization = _localization;
            this._caseViews = {};
            this.cases = null;
        }
        Object.defineProperty(NgPlural.prototype, "ngPlural", {
            set: function (value) {
                this._switchValue = value;
                this._updateView();
            },
            enumerable: true,
            configurable: true
        });
        NgPlural.prototype.ngAfterContentInit = function () {
            var _this = this;
            this.cases.forEach(function (pluralCase) {
                _this._caseViews[pluralCase.value] = pluralCase._view;
            });
            this._updateView();
        };
        /** @internal */
        NgPlural.prototype._updateView = function () {
            this._clearViews();
            var key = getPluralCategory(this._switchValue, Object.getOwnPropertyNames(this._caseViews), this._localization);
            this._activateView(this._caseViews[key]);
        };
        /** @internal */
        NgPlural.prototype._clearViews = function () {
            if (isPresent(this._activeView))
                this._activeView.destroy();
        };
        /** @internal */
        NgPlural.prototype._activateView = function (view) {
            if (!isPresent(view))
                return;
            this._activeView = view;
            this._activeView.create();
        };
        return NgPlural;
    }());
    /** @nocollapse */
    NgPlural.decorators = [
        { type: _angular_core.Directive, args: [{ selector: '[ngPlural]' },] },
    ];
    /** @nocollapse */
    NgPlural.ctorParameters = [
        { type: NgLocalization, },
    ];
    /** @nocollapse */
    NgPlural.propDecorators = {
        'cases': [{ type: _angular_core.ContentChildren, args: [NgPluralCase,] },],
        'ngPlural': [{ type: _angular_core.Input },],
    };
    var NgStyle = (function () {
        function NgStyle(_differs, _ngEl, _renderer) {
            this._differs = _differs;
            this._ngEl = _ngEl;
            this._renderer = _renderer;
        }
        Object.defineProperty(NgStyle.prototype, "rawStyle", {
            set: function (v) {
                this._rawStyle = v;
                if (isBlank(this._differ) && isPresent(v)) {
                    this._differ = this._differs.find(this._rawStyle).create(null);
                }
            },
            enumerable: true,
            configurable: true
        });
        NgStyle.prototype.ngDoCheck = function () {
            if (isPresent(this._differ)) {
                var changes = this._differ.diff(this._rawStyle);
                if (isPresent(changes)) {
                    this._applyChanges(changes);
                }
            }
        };
        NgStyle.prototype._applyChanges = function (changes) {
            var _this = this;
            changes.forEachAddedItem(function (record) { _this._setStyle(record.key, record.currentValue); });
            changes.forEachChangedItem(function (record) { _this._setStyle(record.key, record.currentValue); });
            changes.forEachRemovedItem(function (record) { _this._setStyle(record.key, null); });
        };
        NgStyle.prototype._setStyle = function (name, val) {
            this._renderer.setElementStyle(this._ngEl.nativeElement, name, val);
        };
        return NgStyle;
    }());
    /** @nocollapse */
    NgStyle.decorators = [
        { type: _angular_core.Directive, args: [{ selector: '[ngStyle]', inputs: ['rawStyle: ngStyle'] },] },
    ];
    /** @nocollapse */
    NgStyle.ctorParameters = [
        { type: _angular_core.KeyValueDiffers, },
        { type: _angular_core.ElementRef, },
        { type: _angular_core.Renderer, },
    ];
    var NgTemplateOutlet = (function () {
        function NgTemplateOutlet(_viewContainerRef) {
            this._viewContainerRef = _viewContainerRef;
        }
        Object.defineProperty(NgTemplateOutlet.prototype, "ngOutletContext", {
            set: function (context) {
                if (this._context !== context) {
                    this._context = context;
                    if (isPresent(this._viewRef)) {
                        this.createView();
                    }
                }
            },
            enumerable: true,
            configurable: true
        });
        Object.defineProperty(NgTemplateOutlet.prototype, "ngTemplateOutlet", {
            set: function (templateRef) {
                if (this._templateRef !== templateRef) {
                    this._templateRef = templateRef;
                    this.createView();
                }
            },
            enumerable: true,
            configurable: true
        });
        NgTemplateOutlet.prototype.createView = function () {
            if (isPresent(this._viewRef)) {
                this._viewContainerRef.remove(this._viewContainerRef.indexOf(this._viewRef));
            }
            if (isPresent(this._templateRef)) {
                this._viewRef = this._viewContainerRef.createEmbeddedView(this._templateRef, this._context);
            }
        };
        return NgTemplateOutlet;
    }());
    /** @nocollapse */
    NgTemplateOutlet.decorators = [
        { type: _angular_core.Directive, args: [{ selector: '[ngTemplateOutlet]' },] },
    ];
    /** @nocollapse */
    NgTemplateOutlet.ctorParameters = [
        { type: _angular_core.ViewContainerRef, },
    ];
    /** @nocollapse */
    NgTemplateOutlet.propDecorators = {
        'ngOutletContext': [{ type: _angular_core.Input },],
        'ngTemplateOutlet': [{ type: _angular_core.Input },],
    };
    /**
     * A collection of Angular core directives that are likely to be used in each and every Angular
     * application.
     *
     * This collection can be used to quickly enumerate all the built-in directives in the `directives`
     * property of the `@Component` annotation.
     *
     * ### Example ([live demo](http://plnkr.co/edit/yakGwpCdUkg0qfzX5m8g?p=preview))
     *
     * Instead of writing:
     *
     * ```typescript
     * import {NgClass, NgIf, NgFor, NgSwitch, NgSwitchWhen, NgSwitchDefault} from '@angular/common';
     * import {OtherDirective} from './myDirectives';
     *
     * @Component({
     *   selector: 'my-component',
     *   templateUrl: 'myComponent.html',
     *   directives: [NgClass, NgIf, NgFor, NgSwitch, NgSwitchWhen, NgSwitchDefault, OtherDirective]
     * })
     * export class MyComponent {
     *   ...
     * }
     * ```
     * one could import all the core directives at once:
     *
     * ```typescript
     * import {CORE_DIRECTIVES} from '@angular/common';
     * import {OtherDirective} from './myDirectives';
     *
     * @Component({
     *   selector: 'my-component',
     *   templateUrl: 'myComponent.html',
     *   directives: [CORE_DIRECTIVES, OtherDirective]
     * })
     * export class MyComponent {
     *   ...
     * }
     * ```
     *
     * @stable
     */
    var CORE_DIRECTIVES = [
        NgClass,
        NgFor,
        NgIf,
        NgTemplateOutlet,
        NgStyle,
        NgSwitch,
        NgSwitchCase,
        NgSwitchDefault,
        NgPlural,
        NgPluralCase,
    ];
    /**
     * Used to provide a {@link ControlValueAccessor} for form controls.
     *
     * See {@link DefaultValueAccessor} for how to implement one.
     * @experimental
     */
    var NG_VALUE_ACCESSOR =
    /*@ts2dart_const*/ new _angular_core.OpaqueToken('NgValueAccessor');
    /**
     * Base class for control directives.
     *
     * Only used internally in the forms module.
     *
     * @experimental
     */
    var AbstractControlDirective = (function () {
        function AbstractControlDirective() {
        }
        Object.defineProperty(AbstractControlDirective.prototype, "control", {
            get: function () { return unimplemented(); },
            enumerable: true,
            configurable: true
        });
        Object.defineProperty(AbstractControlDirective.prototype, "value", {
            get: function () { return isPresent(this.control) ? this.control.value : null; },
            enumerable: true,
            configurable: true
        });
        Object.defineProperty(AbstractControlDirective.prototype, "valid", {
            get: function () { return isPresent(this.control) ? this.control.valid : null; },
            enumerable: true,
            configurable: true
        });
        Object.defineProperty(AbstractControlDirective.prototype, "errors", {
            get: function () {
                return isPresent(this.control) ? this.control.errors : null;
            },
            enumerable: true,
            configurable: true
        });
        Object.defineProperty(AbstractControlDirective.prototype, "pristine", {
            get: function () { return isPresent(this.control) ? this.control.pristine : null; },
            enumerable: true,
            configurable: true
        });
        Object.defineProperty(AbstractControlDirective.prototype, "dirty", {
            get: function () { return isPresent(this.control) ? this.control.dirty : null; },
            enumerable: true,
            configurable: true
        });
        Object.defineProperty(AbstractControlDirective.prototype, "touched", {
            get: function () { return isPresent(this.control) ? this.control.touched : null; },
            enumerable: true,
            configurable: true
        });
        Object.defineProperty(AbstractControlDirective.prototype, "untouched", {
            get: function () { return isPresent(this.control) ? this.control.untouched : null; },
            enumerable: true,
            configurable: true
        });
        Object.defineProperty(AbstractControlDirective.prototype, "path", {
            get: function () { return null; },
            enumerable: true,
            configurable: true
        });
        return AbstractControlDirective;
    }());
    /**
     * A base class that all control directive extend.
     * It binds a {@link Control} object to a DOM element.
     *
     * Used internally by Angular forms.
     *
     * @experimental
     */
    var NgControl = (function (_super) {
        __extends(NgControl, _super);
        function NgControl() {
            var args = [];
            for (var _i = 0; _i < arguments.length; _i++) {
                args[_i - 0] = arguments[_i];
            }
            _super.apply(this, args);
            this.name = null;
            this.valueAccessor = null;
        }
        Object.defineProperty(NgControl.prototype, "validator", {
            get: function () { return unimplemented(); },
            enumerable: true,
            configurable: true
        });
        Object.defineProperty(NgControl.prototype, "asyncValidator", {
            get: function () { return unimplemented(); },
            enumerable: true,
            configurable: true
        });
        return NgControl;
    }(AbstractControlDirective));
    var RADIO_VALUE_ACCESSOR = {
        provide: NG_VALUE_ACCESSOR,
        useExisting: _angular_core.forwardRef(function () { return RadioControlValueAccessor; }),
        multi: true
    };
    var RadioControlRegistry = (function () {
        function RadioControlRegistry() {
            this._accessors = [];
        }
        RadioControlRegistry.prototype.add = function (control, accessor) {
            this._accessors.push([control, accessor]);
        };
        RadioControlRegistry.prototype.remove = function (accessor) {
            var indexToRemove = -1;
            for (var i = 0; i < this._accessors.length; ++i) {
                if (this._accessors[i][1] === accessor) {
                    indexToRemove = i;
                }
            }
            ListWrapper.removeAt(this._accessors, indexToRemove);
        };
        RadioControlRegistry.prototype.select = function (accessor) {
            var _this = this;
            this._accessors.forEach(function (c) {
                if (_this._isSameGroup(c, accessor) && c[1] !== accessor) {
                    c[1].fireUncheck();
                }
            });
        };
        RadioControlRegistry.prototype._isSameGroup = function (controlPair, accessor) {
            return controlPair[0].control.root === accessor._control.control.root &&
                controlPair[1].name === accessor.name;
        };
        return RadioControlRegistry;
    }());
    /** @nocollapse */
    RadioControlRegistry.decorators = [
        { type: _angular_core.Injectable },
    ];
    /**
     * The value provided by the forms API for radio buttons.
     *
     * @experimental
     */
    var RadioButtonState = (function () {
        function RadioButtonState(checked, value) {
            this.checked = checked;
            this.value = value;
        }
        return RadioButtonState;
    }());
    var RadioControlValueAccessor = (function () {
        function RadioControlValueAccessor(_renderer, _elementRef, _registry, _injector) {
            this._renderer = _renderer;
            this._elementRef = _elementRef;
            this._registry = _registry;
            this._injector = _injector;
            this.onChange = function () { };
            this.onTouched = function () { };
        }
        RadioControlValueAccessor.prototype.ngOnInit = function () {
            this._control = this._injector.get(NgControl);
            this._registry.add(this._control, this);
        };
        RadioControlValueAccessor.prototype.ngOnDestroy = function () { this._registry.remove(this); };
        RadioControlValueAccessor.prototype.writeValue = function (value) {
            this._state = value;
            if (isPresent(value) && value.checked) {
                this._renderer.setElementProperty(this._elementRef.nativeElement, 'checked', true);
            }
        };
        RadioControlValueAccessor.prototype.registerOnChange = function (fn) {
            var _this = this;
            this._fn = fn;
            this.onChange = function () {
                fn(new RadioButtonState(true, _this._state.value));
                _this._registry.select(_this);
            };
        };
        RadioControlValueAccessor.prototype.fireUncheck = function () { this._fn(new RadioButtonState(false, this._state.value)); };
        RadioControlValueAccessor.prototype.registerOnTouched = function (fn) { this.onTouched = fn; };
        return RadioControlValueAccessor;
    }());
    /** @nocollapse */
    RadioControlValueAccessor.decorators = [
        { type: _angular_core.Directive, args: [{
                    selector: 'input[type=radio][ngControl],input[type=radio][ngFormControl],input[type=radio][ngModel]',
                    host: { '(change)': 'onChange()', '(blur)': 'onTouched()' },
                    providers: [RADIO_VALUE_ACCESSOR]
                },] },
    ];
    /** @nocollapse */
    RadioControlValueAccessor.ctorParameters = [
        { type: _angular_core.Renderer, },
        { type: _angular_core.ElementRef, },
        { type: RadioControlRegistry, },
        { type: _angular_core.Injector, },
    ];
    /** @nocollapse */
    RadioControlValueAccessor.propDecorators = {
        'name': [{ type: _angular_core.Input },],
    };
    /**
     * Indicates that a Control is valid, i.e. that no errors exist in the input value.
     */
    var VALID = 'VALID';
    /**
     * Indicates that a Control is invalid, i.e. that an error exists in the input value.
     */
    var INVALID = 'INVALID';
    /**
     * Indicates that a Control is pending, i.e. that async validation is occurring and
     * errors are not yet available for the input value.
     */
    var PENDING = 'PENDING';
    function _find(control, path) {
        if (isBlank(path))
            return null;
        if (!(path instanceof Array)) {
            path = path.split('/');
        }
        if (path instanceof Array && ListWrapper.isEmpty(path))
            return null;
        return path.reduce(function (v, name) {
            if (v instanceof ControlGroup) {
                return isPresent(v.controls[name]) ? v.controls[name] : null;
            }
            else if (v instanceof ControlArray) {
                var index = name;
                return isPresent(v.at(index)) ? v.at(index) : null;
            }
            else {
                return null;
            }
        }, control);
    }
    function toObservable(r) {
        return isPromise(r) ? ObservableWrapper.fromPromise(r) : r;
    }
    /**
     * @experimental
     */
    var AbstractControl = (function () {
        function AbstractControl(validator, asyncValidator) {
            this.validator = validator;
            this.asyncValidator = asyncValidator;
            this._pristine = true;
            this._touched = false;
        }
        Object.defineProperty(AbstractControl.prototype, "value", {
            get: function () { return this._value; },
            enumerable: true,
            configurable: true
        });
        Object.defineProperty(AbstractControl.prototype, "status", {
            get: function () { return this._status; },
            enumerable: true,
            configurable: true
        });
        Object.defineProperty(AbstractControl.prototype, "valid", {
            get: function () { return this._status === VALID; },
            enumerable: true,
            configurable: true
        });
        Object.defineProperty(AbstractControl.prototype, "errors", {
            /**
             * Returns the errors of this control.
             */
            get: function () { return this._errors; },
            enumerable: true,
            configurable: true
        });
        Object.defineProperty(AbstractControl.prototype, "pristine", {
            get: function () { return this._pristine; },
            enumerable: true,
            configurable: true
        });
        Object.defineProperty(AbstractControl.prototype, "dirty", {
            get: function () { return !this.pristine; },
            enumerable: true,
            configurable: true
        });
        Object.defineProperty(AbstractControl.prototype, "touched", {
            get: function () { return this._touched; },
            enumerable: true,
            configurable: true
        });
        Object.defineProperty(AbstractControl.prototype, "untouched", {
            get: function () { return !this._touched; },
            enumerable: true,
            configurable: true
        });
        Object.defineProperty(AbstractControl.prototype, "valueChanges", {
            get: function () { return this._valueChanges; },
            enumerable: true,
            configurable: true
        });
        Object.defineProperty(AbstractControl.prototype, "statusChanges", {
            get: function () { return this._statusChanges; },
            enumerable: true,
            configurable: true
        });
        Object.defineProperty(AbstractControl.prototype, "pending", {
            get: function () { return this._status == PENDING; },
            enumerable: true,
            configurable: true
        });
        AbstractControl.prototype.markAsTouched = function () { this._touched = true; };
        AbstractControl.prototype.markAsDirty = function (_a) {
            var onlySelf = (_a === void 0 ? {} : _a).onlySelf;
            onlySelf = normalizeBool(onlySelf);
            this._pristine = false;
            if (isPresent(this._parent) && !onlySelf) {
                this._parent.markAsDirty({ onlySelf: onlySelf });
            }
        };
        AbstractControl.prototype.markAsPending = function (_a) {
            var onlySelf = (_a === void 0 ? {} : _a).onlySelf;
            onlySelf = normalizeBool(onlySelf);
            this._status = PENDING;
            if (isPresent(this._parent) && !onlySelf) {
                this._parent.markAsPending({ onlySelf: onlySelf });
            }
        };
        AbstractControl.prototype.setParent = function (parent) { this._parent = parent; };
        AbstractControl.prototype.updateValueAndValidity = function (_a) {
            var _b = _a === void 0 ? {} : _a, onlySelf = _b.onlySelf, emitEvent = _b.emitEvent;
            onlySelf = normalizeBool(onlySelf);
            emitEvent = isPresent(emitEvent) ? emitEvent : true;
            this._updateValue();
            this._errors = this._runValidator();
            this._status = this._calculateStatus();
            if (this._status == VALID || this._status == PENDING) {
                this._runAsyncValidator(emitEvent);
            }
            if (emitEvent) {
                ObservableWrapper.callEmit(this._valueChanges, this._value);
                ObservableWrapper.callEmit(this._statusChanges, this._status);
            }
            if (isPresent(this._parent) && !onlySelf) {
                this._parent.updateValueAndValidity({ onlySelf: onlySelf, emitEvent: emitEvent });
            }
        };
        AbstractControl.prototype._runValidator = function () {
            return isPresent(this.validator) ? this.validator(this) : null;
        };
        AbstractControl.prototype._runAsyncValidator = function (emitEvent) {
            var _this = this;
            if (isPresent(this.asyncValidator)) {
                this._status = PENDING;
                this._cancelExistingSubscription();
                var obs = toObservable(this.asyncValidator(this));
                this._asyncValidationSubscription = ObservableWrapper.subscribe(obs, function (res) { return _this.setErrors(res, { emitEvent: emitEvent }); });
            }
        };
        AbstractControl.prototype._cancelExistingSubscription = function () {
            if (isPresent(this._asyncValidationSubscription)) {
                ObservableWrapper.dispose(this._asyncValidationSubscription);
            }
        };
        /**
         * Sets errors on a control.
         *
         * This is used when validations are run not automatically, but manually by the user.
         *
         * Calling `setErrors` will also update the validity of the parent control.
         *
         * ## Usage
         *
         * ```
         * var login = new Control("someLogin");
         * login.setErrors({
         *   "notUnique": true
         * });
         *
         * expect(login.valid).toEqual(false);
         * expect(login.errors).toEqual({"notUnique": true});
         *
         * login.updateValue("someOtherLogin");
         *
         * expect(login.valid).toEqual(true);
         * ```
         */
        AbstractControl.prototype.setErrors = function (errors, _a) {
            var emitEvent = (_a === void 0 ? {} : _a).emitEvent;
            emitEvent = isPresent(emitEvent) ? emitEvent : true;
            this._errors = errors;
            this._status = this._calculateStatus();
            if (emitEvent) {
                ObservableWrapper.callEmit(this._statusChanges, this._status);
            }
            if (isPresent(this._parent)) {
                this._parent._updateControlsErrors();
            }
        };
        AbstractControl.prototype.find = function (path) { return _find(this, path); };
        AbstractControl.prototype.getError = function (errorCode, path) {
            if (path === void 0) { path = null; }
            var control = isPresent(path) && !ListWrapper.isEmpty(path) ? this.find(path) : this;
            if (isPresent(control) && isPresent(control._errors)) {
                return StringMapWrapper.get(control._errors, errorCode);
            }
            else {
                return null;
            }
        };
        AbstractControl.prototype.hasError = function (errorCode, path) {
            if (path === void 0) { path = null; }
            return isPresent(this.getError(errorCode, path));
        };
        Object.defineProperty(AbstractControl.prototype, "root", {
            get: function () {
                var x = this;
                while (isPresent(x._parent)) {
                    x = x._parent;
                }
                return x;
            },
            enumerable: true,
            configurable: true
        });
        /** @internal */
        AbstractControl.prototype._updateControlsErrors = function () {
            this._status = this._calculateStatus();
            if (isPresent(this._parent)) {
                this._parent._updateControlsErrors();
            }
        };
        /** @internal */
        AbstractControl.prototype._initObservables = function () {
            this._valueChanges = new EventEmitter$1();
            this._statusChanges = new EventEmitter$1();
        };
        AbstractControl.prototype._calculateStatus = function () {
            if (isPresent(this._errors))
                return INVALID;
            if (this._anyControlsHaveStatus(PENDING))
                return PENDING;
            if (this._anyControlsHaveStatus(INVALID))
                return INVALID;
            return VALID;
        };
        return AbstractControl;
    }());
    /**
     * Defines a part of a form that cannot be divided into other controls. `Control`s have values and
     * validation state, which is determined by an optional validation function.
     *
     * `Control` is one of the three fundamental building blocks used to define forms in Angular, along
     * with {@link ControlGroup} and {@link ControlArray}.
     *
     * ## Usage
     *
     * By default, a `Control` is created for every `<input>` or other form component.
     * With {@link NgFormControl} or {@link NgFormModel} an existing {@link Control} can be
     * bound to a DOM element instead. This `Control` can be configured with a custom
     * validation function.
     *
     * ### Example ([live demo](http://plnkr.co/edit/23DESOpbNnBpBHZt1BR4?p=preview))
     *
     * @experimental
     */
    var Control = (function (_super) {
        __extends(Control, _super);
        function Control(value, validator, asyncValidator) {
            if (value === void 0) { value = null; }
            if (validator === void 0) { validator = null; }
            if (asyncValidator === void 0) { asyncValidator = null; }
            _super.call(this, validator, asyncValidator);
            this._value = value;
            this.updateValueAndValidity({ onlySelf: true, emitEvent: false });
            this._initObservables();
        }
        /**
         * Set the value of the control to `value`.
         *
         * If `onlySelf` is `true`, this change will only affect the validation of this `Control`
         * and not its parent component. If `emitEvent` is `true`, this change will cause a
         * `valueChanges` event on the `Control` to be emitted. Both of these options default to
         * `false`.
         *
         * If `emitModelToViewChange` is `true`, the view will be notified about the new value
         * via an `onChange` event. This is the default behavior if `emitModelToViewChange` is not
         * specified.
         */
        Control.prototype.updateValue = function (value, _a) {
            var _b = _a === void 0 ? {} : _a, onlySelf = _b.onlySelf, emitEvent = _b.emitEvent, emitModelToViewChange = _b.emitModelToViewChange;
            emitModelToViewChange = isPresent(emitModelToViewChange) ? emitModelToViewChange : true;
            this._value = value;
            if (isPresent(this._onChange) && emitModelToViewChange)
                this._onChange(this._value);
            this.updateValueAndValidity({ onlySelf: onlySelf, emitEvent: emitEvent });
        };
        /**
         * @internal
         */
        Control.prototype._updateValue = function () { };
        /**
         * @internal
         */
        Control.prototype._anyControlsHaveStatus = function (status) { return false; };
        /**
         * Register a listener for change events.
         */
        Control.prototype.registerOnChange = function (fn) { this._onChange = fn; };
        return Control;
    }(AbstractControl));
    /**
     * Defines a part of a form, of fixed length, that can contain other controls.
     *
     * A `ControlGroup` aggregates the values of each {@link Control} in the group.
     * The status of a `ControlGroup` depends on the status of its children.
     * If one of the controls in a group is invalid, the entire group is invalid.
     * Similarly, if a control changes its value, the entire group changes as well.
     *
     * `ControlGroup` is one of the three fundamental building blocks used to define forms in Angular,
     * along with {@link Control} and {@link ControlArray}. {@link ControlArray} can also contain other
     * controls, but is of variable length.
     *
     * ### Example ([live demo](http://plnkr.co/edit/23DESOpbNnBpBHZt1BR4?p=preview))
     *
     * @experimental
     */
    var ControlGroup = (function (_super) {
        __extends(ControlGroup, _super);
        function ControlGroup(controls, optionals, validator, asyncValidator) {
            if (optionals === void 0) { optionals = null; }
            if (validator === void 0) { validator = null; }
            if (asyncValidator === void 0) { asyncValidator = null; }
            _super.call(this, validator, asyncValidator);
            this.controls = controls;
            this._optionals = isPresent(optionals) ? optionals : {};
            this._initObservables();
            this._setParentForControls();
            this.updateValueAndValidity({ onlySelf: true, emitEvent: false });
        }
        /**
         * Register a control with the group's list of controls.
         */
        ControlGroup.prototype.registerControl = function (name, control) {
            this.controls[name] = control;
            control.setParent(this);
        };
        /**
         * Add a control to this group.
         */
        ControlGroup.prototype.addControl = function (name, control) {
            this.registerControl(name, control);
            this.updateValueAndValidity();
        };
        /**
         * Remove a control from this group.
         */
        ControlGroup.prototype.removeControl = function (name) {
            StringMapWrapper.delete(this.controls, name);
            this.updateValueAndValidity();
        };
        /**
         * Mark the named control as non-optional.
         */
        ControlGroup.prototype.include = function (controlName) {
            StringMapWrapper.set(this._optionals, controlName, true);
            this.updateValueAndValidity();
        };
        /**
         * Mark the named control as optional.
         */
        ControlGroup.prototype.exclude = function (controlName) {
            StringMapWrapper.set(this._optionals, controlName, false);
            this.updateValueAndValidity();
        };
        /**
         * Check whether there is a control with the given name in the group.
         */
        ControlGroup.prototype.contains = function (controlName) {
            var c = StringMapWrapper.contains(this.controls, controlName);
            return c && this._included(controlName);
        };
        /** @internal */
        ControlGroup.prototype._setParentForControls = function () {
            var _this = this;
            StringMapWrapper.forEach(this.controls, function (control, name) { control.setParent(_this); });
        };
        /** @internal */
        ControlGroup.prototype._updateValue = function () { this._value = this._reduceValue(); };
        /** @internal */
        ControlGroup.prototype._anyControlsHaveStatus = function (status) {
            var _this = this;
            var res = false;
            StringMapWrapper.forEach(this.controls, function (control, name) {
                res = res || (_this.contains(name) && control.status == status);
            });
            return res;
        };
        /** @internal */
        ControlGroup.prototype._reduceValue = function () {
            return this._reduceChildren({}, function (acc, control, name) {
                acc[name] = control.value;
                return acc;
            });
        };
        /** @internal */
        ControlGroup.prototype._reduceChildren = function (initValue, fn) {
            var _this = this;
            var res = initValue;
            StringMapWrapper.forEach(this.controls, function (control, name) {
                if (_this._included(name)) {
                    res = fn(res, control, name);
                }
            });
            return res;
        };
        /** @internal */
        ControlGroup.prototype._included = function (controlName) {
            var isOptional = StringMapWrapper.contains(this._optionals, controlName);
            return !isOptional || StringMapWrapper.get(this._optionals, controlName);
        };
        return ControlGroup;
    }(AbstractControl));
    /**
     * Defines a part of a form, of variable length, that can contain other controls.
     *
     * A `ControlArray` aggregates the values of each {@link Control} in the group.
     * The status of a `ControlArray` depends on the status of its children.
     * If one of the controls in a group is invalid, the entire array is invalid.
     * Similarly, if a control changes its value, the entire array changes as well.
     *
     * `ControlArray` is one of the three fundamental building blocks used to define forms in Angular,
     * along with {@link Control} and {@link ControlGroup}. {@link ControlGroup} can also contain
     * other controls, but is of fixed length.
     *
     * ## Adding or removing controls
     *
     * To change the controls in the array, use the `push`, `insert`, or `removeAt` methods
     * in `ControlArray` itself. These methods ensure the controls are properly tracked in the
     * form's hierarchy. Do not modify the array of `AbstractControl`s used to instantiate
     * the `ControlArray` directly, as that will result in strange and unexpected behavior such
     * as broken change detection.
     *
     * ### Example ([live demo](http://plnkr.co/edit/23DESOpbNnBpBHZt1BR4?p=preview))
     *
     * @experimental
     */
    var ControlArray = (function (_super) {
        __extends(ControlArray, _super);
        function ControlArray(controls, validator, asyncValidator) {
            if (validator === void 0) { validator = null; }
            if (asyncValidator === void 0) { asyncValidator = null; }
            _super.call(this, validator, asyncValidator);
            this.controls = controls;
            this._initObservables();
            this._setParentForControls();
            this.updateValueAndValidity({ onlySelf: true, emitEvent: false });
        }
        /**
         * Get the {@link AbstractControl} at the given `index` in the array.
         */
        ControlArray.prototype.at = function (index) { return this.controls[index]; };
        /**
         * Insert a new {@link AbstractControl} at the end of the array.
         */
        ControlArray.prototype.push = function (control) {
            this.controls.push(control);
            control.setParent(this);
            this.updateValueAndValidity();
        };
        /**
         * Insert a new {@link AbstractControl} at the given `index` in the array.
         */
        ControlArray.prototype.insert = function (index, control) {
            ListWrapper.insert(this.controls, index, control);
            control.setParent(this);
            this.updateValueAndValidity();
        };
        /**
         * Remove the control at the given `index` in the array.
         */
        ControlArray.prototype.removeAt = function (index) {
            ListWrapper.removeAt(this.controls, index);
            this.updateValueAndValidity();
        };
        Object.defineProperty(ControlArray.prototype, "length", {
            /**
             * Length of the control array.
             */
            get: function () { return this.controls.length; },
            enumerable: true,
            configurable: true
        });
        /** @internal */
        ControlArray.prototype._updateValue = function () { this._value = this.controls.map(function (control) { return control.value; }); };
        /** @internal */
        ControlArray.prototype._anyControlsHaveStatus = function (status) {
            return this.controls.some(function (c) { return c.status == status; });
        };
        /** @internal */
        ControlArray.prototype._setParentForControls = function () {
            var _this = this;
            this.controls.forEach(function (control) { control.setParent(_this); });
        };
        return ControlArray;
    }(AbstractControl));
    var FormBuilder = (function () {
        function FormBuilder() {
        }
        /**
         * Construct a new {@link ControlGroup} with the given map of configuration.
         * Valid keys for the `extra` parameter map are `optionals` and `validator`.
         *
         * See the {@link ControlGroup} constructor for more details.
         */
        FormBuilder.prototype.group = function (controlsConfig, extra) {
            if (extra === void 0) { extra = null; }
            var controls = this._reduceControls(controlsConfig);
            var optionals = (isPresent(extra) ? StringMapWrapper.get(extra, 'optionals') : null);
            var validator = isPresent(extra) ? StringMapWrapper.get(extra, 'validator') : null;
            var asyncValidator = isPresent(extra) ? StringMapWrapper.get(extra, 'asyncValidator') : null;
            return new ControlGroup(controls, optionals, validator, asyncValidator);
        };
        /**
         * Construct a new {@link Control} with the given `value`,`validator`, and `asyncValidator`.
         */
        FormBuilder.prototype.control = function (value, validator, asyncValidator) {
            if (validator === void 0) { validator = null; }
            if (asyncValidator === void 0) { asyncValidator = null; }
            return new Control(value, validator, asyncValidator);
        };
        /**
         * Construct an array of {@link Control}s from the given `controlsConfig` array of
         * configuration, with the given optional `validator` and `asyncValidator`.
         */
        FormBuilder.prototype.array = function (controlsConfig, validator, asyncValidator) {
            var _this = this;
            if (validator === void 0) { validator = null; }
            if (asyncValidator === void 0) { asyncValidator = null; }
            var controls = controlsConfig.map(function (c) { return _this._createControl(c); });
            return new ControlArray(controls, validator, asyncValidator);
        };
        /** @internal */
        FormBuilder.prototype._reduceControls = function (controlsConfig) {
            var _this = this;
            var controls = {};
            StringMapWrapper.forEach(controlsConfig, function (controlConfig, controlName) {
                controls[controlName] = _this._createControl(controlConfig);
            });
            return controls;
        };
        /** @internal */
        FormBuilder.prototype._createControl = function (controlConfig) {
            if (controlConfig instanceof Control || controlConfig instanceof ControlGroup ||
                controlConfig instanceof ControlArray) {
                return controlConfig;
            }
            else if (isArray(controlConfig)) {
                var value = controlConfig[0];
                var validator = controlConfig.length > 1 ? controlConfig[1] : null;
                var asyncValidator = controlConfig.length > 2 ? controlConfig[2] : null;
                return this.control(value, validator, asyncValidator);
            }
            else {
                return this.control(controlConfig);
            }
        };
        return FormBuilder;
    }());
    /** @nocollapse */
    FormBuilder.decorators = [
        { type: _angular_core.Injectable },
    ];
    var CHECKBOX_VALUE_ACCESSOR = {
        provide: NG_VALUE_ACCESSOR,
        useExisting: _angular_core.forwardRef(function () { return CheckboxControlValueAccessor; }),
        multi: true
    };
    var CheckboxControlValueAccessor = (function () {
        function CheckboxControlValueAccessor(_renderer, _elementRef) {
            this._renderer = _renderer;
            this._elementRef = _elementRef;
            this.onChange = function (_) { };
            this.onTouched = function () { };
        }
        CheckboxControlValueAccessor.prototype.writeValue = function (value) {
            this._renderer.setElementProperty(this._elementRef.nativeElement, 'checked', value);
        };
        CheckboxControlValueAccessor.prototype.registerOnChange = function (fn) { this.onChange = fn; };
        CheckboxControlValueAccessor.prototype.registerOnTouched = function (fn) { this.onTouched = fn; };
        return CheckboxControlValueAccessor;
    }());
    /** @nocollapse */
    CheckboxControlValueAccessor.decorators = [
        { type: _angular_core.Directive, args: [{
                    selector: 'input[type=checkbox][ngControl],input[type=checkbox][ngFormControl],input[type=checkbox][ngModel]',
                    host: { '(change)': 'onChange($event.target.checked)', '(blur)': 'onTouched()' },
                    providers: [CHECKBOX_VALUE_ACCESSOR]
                },] },
    ];
    /** @nocollapse */
    CheckboxControlValueAccessor.ctorParameters = [
        { type: _angular_core.Renderer, },
        { type: _angular_core.ElementRef, },
    ];
    var DEFAULT_VALUE_ACCESSOR =
    /* @ts2dart_Provider */ {
        provide: NG_VALUE_ACCESSOR,
        useExisting: _angular_core.forwardRef(function () { return DefaultValueAccessor; }),
        multi: true
    };
    var DefaultValueAccessor = (function () {
        function DefaultValueAccessor(_renderer, _elementRef) {
            this._renderer = _renderer;
            this._elementRef = _elementRef;
            this.onChange = function (_) { };
            this.onTouched = function () { };
        }
        DefaultValueAccessor.prototype.writeValue = function (value) {
            var normalizedValue = isBlank(value) ? '' : value;
            this._renderer.setElementProperty(this._elementRef.nativeElement, 'value', normalizedValue);
        };
        DefaultValueAccessor.prototype.registerOnChange = function (fn) { this.onChange = fn; };
        DefaultValueAccessor.prototype.registerOnTouched = function (fn) { this.onTouched = fn; };
        return DefaultValueAccessor;
    }());
    /** @nocollapse */
    DefaultValueAccessor.decorators = [
        { type: _angular_core.Directive, args: [{
                    selector: 'input:not([type=checkbox])[ngControl],textarea[ngControl],input:not([type=checkbox])[ngFormControl],textarea[ngFormControl],input:not([type=checkbox])[ngModel],textarea[ngModel],[ngDefaultControl]',
                    // TODO: vsavkin replace the above selector with the one below it once
                    // https://github.com/angular/angular/issues/3011 is implemented
                    // selector: '[ngControl],[ngModel],[ngFormControl]',
                    host: { '(input)': 'onChange($event.target.value)', '(blur)': 'onTouched()' },
                    providers: [DEFAULT_VALUE_ACCESSOR]
                },] },
    ];
    /** @nocollapse */
    DefaultValueAccessor.ctorParameters = [
        { type: _angular_core.Renderer, },
        { type: _angular_core.ElementRef, },
    ];
    /**
     * Providers for validators to be used for {@link Control}s in a form.
     *
     * Provide this using `multi: true` to add validators.
     *
     * ### Example
     *
     * {@example core/forms/ts/ng_validators/ng_validators.ts region='ng_validators'}
     * @experimental
     */
    var NG_VALIDATORS = new _angular_core.OpaqueToken('NgValidators');
    /**
     * Providers for asynchronous validators to be used for {@link Control}s
     * in a form.
     *
     * Provide this using `multi: true` to add validators.
     *
     * See {@link NG_VALIDATORS} for more details.
     *
     * @experimental
     */
    var NG_ASYNC_VALIDATORS =
    /*@ts2dart_const*/ new _angular_core.OpaqueToken('NgAsyncValidators');
    /**
     * Provides a set of validators used by form controls.
     *
     * A validator is a function that processes a {@link Control} or collection of
     * controls and returns a map of errors. A null map means that validation has passed.
     *
     * ### Example
     *
     * ```typescript
     * var loginControl = new Control("", Validators.required)
     * ```
     *
     * @experimental
     */
    var Validators = (function () {
        function Validators() {
        }
        /**
         * Validator that requires controls to have a non-empty value.
         */
        Validators.required = function (control) {
            return isBlank(control.value) || (isString(control.value) && control.value == '') ?
                { 'required': true } :
                null;
        };
        /**
         * Validator that requires controls to have a value of a minimum length.
         */
        Validators.minLength = function (minLength) {
            return function (control) {
                if (isPresent(Validators.required(control)))
                    return null;
                var v = control.value;
                return v.length < minLength ?
                    { 'minlength': { 'requiredLength': minLength, 'actualLength': v.length } } :
                    null;
            };
        };
        /**
         * Validator that requires controls to have a value of a maximum length.
         */
        Validators.maxLength = function (maxLength) {
            return function (control) {
                if (isPresent(Validators.required(control)))
                    return null;
                var v = control.value;
                return v.length > maxLength ?
                    { 'maxlength': { 'requiredLength': maxLength, 'actualLength': v.length } } :
                    null;
            };
        };
        /**
         * Validator that requires a control to match a regex to its value.
         */
        Validators.pattern = function (pattern) {
            return function (control) {
                if (isPresent(Validators.required(control)))
                    return null;
                var regex = new RegExp("^" + pattern + "$");
                var v = control.value;
                return regex.test(v) ? null :
                    { 'pattern': { 'requiredPattern': "^" + pattern + "$", 'actualValue': v } };
            };
        };
        /**
         * No-op validator.
         */
        Validators.nullValidator = function (c) { return null; };
        /**
         * Compose multiple validators into a single function that returns the union
         * of the individual error maps.
         */
        Validators.compose = function (validators) {
            if (isBlank(validators))
                return null;
            var presentValidators = validators.filter(isPresent);
            if (presentValidators.length == 0)
                return null;
            return function (control) {
                return _mergeErrors(_executeValidators(control, presentValidators));
            };
        };
        Validators.composeAsync = function (validators) {
            if (isBlank(validators))
                return null;
            var presentValidators = validators.filter(isPresent);
            if (presentValidators.length == 0)
                return null;
            return function (control) {
                var promises = _executeAsyncValidators(control, presentValidators).map(_convertToPromise);
                return PromiseWrapper.all(promises).then(_mergeErrors);
            };
        };
        return Validators;
    }());
    function _convertToPromise(obj) {
        return isPromise(obj) ? obj : ObservableWrapper.toPromise(obj);
    }
    function _executeValidators(control, validators) {
        return validators.map(function (v) { return v(control); });
    }
    function _executeAsyncValidators(control, validators) {
        return validators.map(function (v) { return v(control); });
    }
    function _mergeErrors(arrayOfErrors) {
        var res = arrayOfErrors.reduce(function (res, errors) {
            return isPresent(errors) ? StringMapWrapper.merge(res, errors) : res;
        }, {});
        return StringMapWrapper.isEmpty(res) ? null : res;
    }
    /**
     * A directive that contains multiple {@link NgControl}s.
     *
     * Only used by the forms module.
     *
     * @experimental
     */
    var ControlContainer = (function (_super) {
        __extends(ControlContainer, _super);
        function ControlContainer() {
            _super.apply(this, arguments);
        }
        Object.defineProperty(ControlContainer.prototype, "formDirective", {
            /**
             * Get the form to which this container belongs.
             */
            get: function () { return null; },
            enumerable: true,
            configurable: true
        });
        Object.defineProperty(ControlContainer.prototype, "path", {
            /**
             * Get the path to this container.
             */
            get: function () { return null; },
            enumerable: true,
            configurable: true
        });
        return ControlContainer;
    }(AbstractControlDirective));
    /**
     * @license
     * Copyright Google Inc. All Rights Reserved.
     *
     * Use of this source code is governed by an MIT-style license that can be
     * found in the LICENSE file at https://angular.io/license
     */
    function normalizeValidator(validator) {
        if (validator.validate !== undefined) {
            return function (c) { return validator.validate(c); };
        }
        else {
            return validator;
        }
    }
    function normalizeAsyncValidator(validator) {
        if (validator.validate !== undefined) {
            return function (c) { return validator.validate(c); };
        }
        else {
            return validator;
        }
    }
    var NUMBER_VALUE_ACCESSOR = {
        provide: NG_VALUE_ACCESSOR,
        useExisting: _angular_core.forwardRef(function () { return NumberValueAccessor; }),
        multi: true
    };
    var NumberValueAccessor = (function () {
        function NumberValueAccessor(_renderer, _elementRef) {
            this._renderer = _renderer;
            this._elementRef = _elementRef;
            this.onChange = function (_) { };
            this.onTouched = function () { };
        }
        NumberValueAccessor.prototype.writeValue = function (value) {
            this._renderer.setElementProperty(this._elementRef.nativeElement, 'value', value);
        };
        NumberValueAccessor.prototype.registerOnChange = function (fn) {
            this.onChange = function (value) { fn(value == '' ? null : NumberWrapper.parseFloat(value)); };
        };
        NumberValueAccessor.prototype.registerOnTouched = function (fn) { this.onTouched = fn; };
        return NumberValueAccessor;
    }());
    /** @nocollapse */
    NumberValueAccessor.decorators = [
        { type: _angular_core.Directive, args: [{
                    selector: 'input[type=number][ngControl],input[type=number][ngFormControl],input[type=number][ngModel]',
                    host: {
                        '(change)': 'onChange($event.target.value)',
                        '(input)': 'onChange($event.target.value)',
                        '(blur)': 'onTouched()'
                    },
                    providers: [NUMBER_VALUE_ACCESSOR]
                },] },
    ];
    /** @nocollapse */
    NumberValueAccessor.ctorParameters = [
        { type: _angular_core.Renderer, },
        { type: _angular_core.ElementRef, },
    ];
    var SELECT_VALUE_ACCESSOR = {
        provide: NG_VALUE_ACCESSOR,
        useExisting: _angular_core.forwardRef(function () { return SelectControlValueAccessor; }),
        multi: true
    };
    function _buildValueString(id, value) {
        if (isBlank(id))
            return "" + value;
        if (!isPrimitive(value))
            value = 'Object';
        return StringWrapper.slice(id + ": " + value, 0, 50);
    }
    function _extractId(valueString) {
        return valueString.split(':')[0];
    }
    var SelectControlValueAccessor = (function () {
        function SelectControlValueAccessor(_renderer, _elementRef) {
            this._renderer = _renderer;
            this._elementRef = _elementRef;
            /** @internal */
            this._optionMap = new Map();
            /** @internal */
            this._idCounter = 0;
            this.onChange = function (_) { };
            this.onTouched = function () { };
        }
        SelectControlValueAccessor.prototype.writeValue = function (value) {
            this.value = value;
            var valueString = _buildValueString(this._getOptionId(value), value);
            this._renderer.setElementProperty(this._elementRef.nativeElement, 'value', valueString);
        };
        SelectControlValueAccessor.prototype.registerOnChange = function (fn) {
            var _this = this;
            this.onChange = function (valueString) {
                _this.value = valueString;
                fn(_this._getOptionValue(valueString));
            };
        };
        SelectControlValueAccessor.prototype.registerOnTouched = function (fn) { this.onTouched = fn; };
        /** @internal */
        SelectControlValueAccessor.prototype._registerOption = function () { return (this._idCounter++).toString(); };
        /** @internal */
        SelectControlValueAccessor.prototype._getOptionId = function (value) {
            for (var _i = 0, _a = MapWrapper.keys(this._optionMap); _i < _a.length; _i++) {
                var id = _a[_i];
                if (looseIdentical(this._optionMap.get(id), value))
                    return id;
            }
            return null;
        };
        /** @internal */
        SelectControlValueAccessor.prototype._getOptionValue = function (valueString) {
            var value = this._optionMap.get(_extractId(valueString));
            return isPresent(value) ? value : valueString;
        };
        return SelectControlValueAccessor;
    }());
    /** @nocollapse */
    SelectControlValueAccessor.decorators = [
        { type: _angular_core.Directive, args: [{
                    selector: 'select:not([multiple])[ngControl],select:not([multiple])[ngFormControl],select:not([multiple])[ngModel]',
                    host: { '(change)': 'onChange($event.target.value)', '(blur)': 'onTouched()' },
                    providers: [SELECT_VALUE_ACCESSOR]
                },] },
    ];
    /** @nocollapse */
    SelectControlValueAccessor.ctorParameters = [
        { type: _angular_core.Renderer, },
        { type: _angular_core.ElementRef, },
    ];
    var NgSelectOption = (function () {
        function NgSelectOption(_element, _renderer, _select) {
            this._element = _element;
            this._renderer = _renderer;
            this._select = _select;
            if (isPresent(this._select))
                this.id = this._select._registerOption();
        }
        Object.defineProperty(NgSelectOption.prototype, "ngValue", {
            set: function (value) {
                if (this._select == null)
                    return;
                this._select._optionMap.set(this.id, value);
                this._setElementValue(_buildValueString(this.id, value));
                this._select.writeValue(this._select.value);
            },
            enumerable: true,
            configurable: true
        });
        Object.defineProperty(NgSelectOption.prototype, "value", {
            set: function (value) {
                this._setElementValue(value);
                if (isPresent(this._select))
                    this._select.writeValue(this._select.value);
            },
            enumerable: true,
            configurable: true
        });
        /** @internal */
        NgSelectOption.prototype._setElementValue = function (value) {
            this._renderer.setElementProperty(this._element.nativeElement, 'value', value);
        };
        NgSelectOption.prototype.ngOnDestroy = function () {
            if (isPresent(this._select)) {
                this._select._optionMap.delete(this.id);
                this._select.writeValue(this._select.value);
            }
        };
        return NgSelectOption;
    }());
    /** @nocollapse */
    NgSelectOption.decorators = [
        { type: _angular_core.Directive, args: [{ selector: 'option' },] },
    ];
    /** @nocollapse */
    NgSelectOption.ctorParameters = [
        { type: _angular_core.ElementRef, },
        { type: _angular_core.Renderer, },
        { type: SelectControlValueAccessor, decorators: [{ type: _angular_core.Optional }, { type: _angular_core.Host },] },
    ];
    /** @nocollapse */
    NgSelectOption.propDecorators = {
        'ngValue': [{ type: _angular_core.Input, args: ['ngValue',] },],
        'value': [{ type: _angular_core.Input, args: ['value',] },],
    };
    var SELECT_MULTIPLE_VALUE_ACCESSOR = {
        provide: NG_VALUE_ACCESSOR,
        useExisting: _angular_core.forwardRef(function () { return SelectMultipleControlValueAccessor; }),
        multi: true
    };
    function _buildValueString$1(id, value) {
        if (isBlank(id))
            return "" + value;
        if (isString(value))
            value = "'" + value + "'";
        if (!isPrimitive(value))
            value = 'Object';
        return StringWrapper.slice(id + ": " + value, 0, 50);
    }
    function _extractId$1(valueString) {
        return valueString.split(':')[0];
    }
    var SelectMultipleControlValueAccessor = (function () {
        function SelectMultipleControlValueAccessor() {
            /** @internal */
            this._optionMap = new Map();
            /** @internal */
            this._idCounter = 0;
            this.onChange = function (_) { };
            this.onTouched = function () { };
        }
        SelectMultipleControlValueAccessor.prototype.writeValue = function (value) {
            var _this = this;
            this.value = value;
            if (value == null)
                return;
            var values = value;
            // convert values to ids
            var ids = values.map(function (v) { return _this._getOptionId(v); });
            this._optionMap.forEach(function (opt, o) { opt._setSelected(ids.indexOf(o.toString()) > -1); });
        };
        SelectMultipleControlValueAccessor.prototype.registerOnChange = function (fn) {
            var _this = this;
            this.onChange = function (_) {
                var selected = [];
                if (_.hasOwnProperty('selectedOptions')) {
                    var options = _.selectedOptions;
                    for (var i = 0; i < options.length; i++) {
                        var opt = options.item(i);
                        var val = _this._getOptionValue(opt.value);
                        selected.push(val);
                    }
                }
                else {
                    var options = _.options;
                    for (var i = 0; i < options.length; i++) {
                        var opt = options.item(i);
                        if (opt.selected) {
                            var val = _this._getOptionValue(opt.value);
                            selected.push(val);
                        }
                    }
                }
                fn(selected);
            };
        };
        SelectMultipleControlValueAccessor.prototype.registerOnTouched = function (fn) { this.onTouched = fn; };
        /** @internal */
        SelectMultipleControlValueAccessor.prototype._registerOption = function (value) {
            var id = (this._idCounter++).toString();
            this._optionMap.set(id, value);
            return id;
        };
        /** @internal */
        SelectMultipleControlValueAccessor.prototype._getOptionId = function (value) {
            for (var _i = 0, _a = MapWrapper.keys(this._optionMap); _i < _a.length; _i++) {
                var id = _a[_i];
                if (looseIdentical(this._optionMap.get(id)._value, value))
                    return id;
            }
            return null;
        };
        /** @internal */
        SelectMultipleControlValueAccessor.prototype._getOptionValue = function (valueString) {
            var opt = this._optionMap.get(_extractId$1(valueString));
            return isPresent(opt) ? opt._value : valueString;
        };
        return SelectMultipleControlValueAccessor;
    }());
    /** @nocollapse */
    SelectMultipleControlValueAccessor.decorators = [
        { type: _angular_core.Directive, args: [{
                    selector: 'select[multiple][ngControl],select[multiple][ngFormControl],select[multiple][ngModel]',
                    host: { '(input)': 'onChange($event.target)', '(blur)': 'onTouched()' },
                    providers: [SELECT_MULTIPLE_VALUE_ACCESSOR]
                },] },
    ];
    /** @nocollapse */
    SelectMultipleControlValueAccessor.ctorParameters = [];
    var NgSelectMultipleOption = (function () {
        function NgSelectMultipleOption(_element, _renderer, _select) {
            this._element = _element;
            this._renderer = _renderer;
            this._select = _select;
            if (isPresent(this._select)) {
                this.id = this._select._registerOption(this);
            }
        }
        Object.defineProperty(NgSelectMultipleOption.prototype, "ngValue", {
            set: function (value) {
                if (this._select == null)
                    return;
                this._value = value;
                this._setElementValue(_buildValueString$1(this.id, value));
                this._select.writeValue(this._select.value);
            },
            enumerable: true,
            configurable: true
        });
        Object.defineProperty(NgSelectMultipleOption.prototype, "value", {
            set: function (value) {
                if (isPresent(this._select)) {
                    this._value = value;
                    this._setElementValue(_buildValueString$1(this.id, value));
                    this._select.writeValue(this._select.value);
                }
                else {
                    this._setElementValue(value);
                }
            },
            enumerable: true,
            configurable: true
        });
        /** @internal */
        NgSelectMultipleOption.prototype._setElementValue = function (value) {
            this._renderer.setElementProperty(this._element.nativeElement, 'value', value);
        };
        /** @internal */
        NgSelectMultipleOption.prototype._setSelected = function (selected) {
            this._renderer.setElementProperty(this._element.nativeElement, 'selected', selected);
        };
        NgSelectMultipleOption.prototype.ngOnDestroy = function () {
            if (isPresent(this._select)) {
                this._select._optionMap.delete(this.id);
                this._select.writeValue(this._select.value);
            }
        };
        return NgSelectMultipleOption;
    }());
    /** @nocollapse */
    NgSelectMultipleOption.decorators = [
        { type: _angular_core.Directive, args: [{ selector: 'option' },] },
    ];
    /** @nocollapse */
    NgSelectMultipleOption.ctorParameters = [
        { type: _angular_core.ElementRef, },
        { type: _angular_core.Renderer, },
        { type: SelectMultipleControlValueAccessor, decorators: [{ type: _angular_core.Optional }, { type: _angular_core.Host },] },
    ];
    /** @nocollapse */
    NgSelectMultipleOption.propDecorators = {
        'ngValue': [{ type: _angular_core.Input, args: ['ngValue',] },],
        'value': [{ type: _angular_core.Input, args: ['value',] },],
    };
    function controlPath(name, parent) {
        var p = ListWrapper.clone(parent.path);
        p.push(name);
        return p;
    }
    function setUpControl(control, dir) {
        if (isBlank(control))
            _throwError(dir, 'Cannot find control');
        if (isBlank(dir.valueAccessor))
            _throwError(dir, 'No value accessor for');
        control.validator = Validators.compose([control.validator, dir.validator]);
        control.asyncValidator = Validators.composeAsync([control.asyncValidator, dir.asyncValidator]);
        dir.valueAccessor.writeValue(control.value);
        // view -> model
        dir.valueAccessor.registerOnChange(function (newValue) {
            dir.viewToModelUpdate(newValue);
            control.updateValue(newValue, { emitModelToViewChange: false });
            control.markAsDirty();
        });
        // model -> view
        control.registerOnChange(function (newValue) { return dir.valueAccessor.writeValue(newValue); });
        // touched
        dir.valueAccessor.registerOnTouched(function () { return control.markAsTouched(); });
    }
    function setUpControlGroup(control, dir) {
        if (isBlank(control))
            _throwError(dir, 'Cannot find control');
        control.validator = Validators.compose([control.validator, dir.validator]);
        control.asyncValidator = Validators.composeAsync([control.asyncValidator, dir.asyncValidator]);
    }
    function _throwError(dir, message) {
        var path = dir.path.join(' -> ');
        throw new BaseException(message + " '" + path + "'");
    }
    function composeValidators(validators) {
        return isPresent(validators) ? Validators.compose(validators.map(normalizeValidator)) : null;
    }
    function composeAsyncValidators(validators) {
        return isPresent(validators) ? Validators.composeAsync(validators.map(normalizeAsyncValidator)) :
            null;
    }
    function isPropertyUpdated(changes, viewModel) {
        if (!StringMapWrapper.contains(changes, 'model'))
            return false;
        var change = changes['model'];
        if (change.isFirstChange())
            return true;
        return !looseIdentical(viewModel, change.currentValue);
    }
    // TODO: vsavkin remove it once https://github.com/angular/angular/issues/3011 is implemented
    function selectValueAccessor(dir, valueAccessors) {
        if (isBlank(valueAccessors))
            return null;
        var defaultAccessor;
        var builtinAccessor;
        var customAccessor;
        valueAccessors.forEach(function (v) {
            if (hasConstructor(v, DefaultValueAccessor)) {
                defaultAccessor = v;
            }
            else if (hasConstructor(v, CheckboxControlValueAccessor) || hasConstructor(v, NumberValueAccessor) ||
                hasConstructor(v, SelectControlValueAccessor) ||
                hasConstructor(v, SelectMultipleControlValueAccessor) ||
                hasConstructor(v, RadioControlValueAccessor)) {
                if (isPresent(builtinAccessor))
                    _throwError(dir, 'More than one built-in value accessor matches');
                builtinAccessor = v;
            }
            else {
                if (isPresent(customAccessor))
                    _throwError(dir, 'More than one custom value accessor matches');
                customAccessor = v;
            }
        });
        if (isPresent(customAccessor))
            return customAccessor;
        if (isPresent(builtinAccessor))
            return builtinAccessor;
        if (isPresent(defaultAccessor))
            return defaultAccessor;
        _throwError(dir, 'No valid value accessor for');
        return null;
    }
    var controlGroupProvider =
    /*@ts2dart_const*/ /* @ts2dart_Provider */ {
        provide: ControlContainer,
        useExisting: _angular_core.forwardRef(function () { return NgControlGroup; })
    };
    var NgControlGroup = (function (_super) {
        __extends(NgControlGroup, _super);
        function NgControlGroup(parent, _validators, _asyncValidators) {
            _super.call(this);
            this._validators = _validators;
            this._asyncValidators = _asyncValidators;
            this._parent = parent;
        }
        NgControlGroup.prototype.ngOnInit = function () { this.formDirective.addControlGroup(this); };
        NgControlGroup.prototype.ngOnDestroy = function () { this.formDirective.removeControlGroup(this); };
        Object.defineProperty(NgControlGroup.prototype, "control", {
            /**
             * Get the {@link ControlGroup} backing this binding.
             */
            get: function () { return this.formDirective.getControlGroup(this); },
            enumerable: true,
            configurable: true
        });
        Object.defineProperty(NgControlGroup.prototype, "path", {
            /**
             * Get the path to this control group.
             */
            get: function () { return controlPath(this.name, this._parent); },
            enumerable: true,
            configurable: true
        });
        Object.defineProperty(NgControlGroup.prototype, "formDirective", {
            /**
             * Get the {@link Form} to which this group belongs.
             */
            get: function () { return this._parent.formDirective; },
            enumerable: true,
            configurable: true
        });
        Object.defineProperty(NgControlGroup.prototype, "validator", {
            get: function () { return composeValidators(this._validators); },
            enumerable: true,
            configurable: true
        });
        Object.defineProperty(NgControlGroup.prototype, "asyncValidator", {
            get: function () { return composeAsyncValidators(this._asyncValidators); },
            enumerable: true,
            configurable: true
        });
        return NgControlGroup;
    }(ControlContainer));
    /** @nocollapse */
    NgControlGroup.decorators = [
        { type: _angular_core.Directive, args: [{
                    selector: '[ngControlGroup]',
                    providers: [controlGroupProvider],
                    inputs: ['name: ngControlGroup'],
                    exportAs: 'ngForm'
                },] },
    ];
    /** @nocollapse */
    NgControlGroup.ctorParameters = [
        { type: ControlContainer, decorators: [{ type: _angular_core.Host }, { type: _angular_core.SkipSelf },] },
        { type: Array, decorators: [{ type: _angular_core.Optional }, { type: _angular_core.Self }, { type: _angular_core.Inject, args: [NG_VALIDATORS,] },] },
        { type: Array, decorators: [{ type: _angular_core.Optional }, { type: _angular_core.Self }, { type: _angular_core.Inject, args: [NG_ASYNC_VALIDATORS,] },] },
    ];
    var controlNameBinding =
    /*@ts2dart_const*/ /* @ts2dart_Provider */ {
        provide: NgControl,
        useExisting: _angular_core.forwardRef(function () { return NgControlName; })
    };
    var NgControlName = (function (_super) {
        __extends(NgControlName, _super);
        function NgControlName(_parent, _validators, _asyncValidators, valueAccessors) {
            _super.call(this);
            this._parent = _parent;
            this._validators = _validators;
            this._asyncValidators = _asyncValidators;
            /** @internal */
            this.update = new EventEmitter$1();
            this._added = false;
            this.valueAccessor = selectValueAccessor(this, valueAccessors);
        }
        NgControlName.prototype.ngOnChanges = function (changes) {
            if (!this._added) {
                this.formDirective.addControl(this);
                this._added = true;
            }
            if (isPropertyUpdated(changes, this.viewModel)) {
                this.viewModel = this.model;
                this.formDirective.updateModel(this, this.model);
            }
        };
        NgControlName.prototype.ngOnDestroy = function () { this.formDirective.removeControl(this); };
        NgControlName.prototype.viewToModelUpdate = function (newValue) {
            this.viewModel = newValue;
            ObservableWrapper.callEmit(this.update, newValue);
        };
        Object.defineProperty(NgControlName.prototype, "path", {
            get: function () { return controlPath(this.name, this._parent); },
            enumerable: true,
            configurable: true
        });
        Object.defineProperty(NgControlName.prototype, "formDirective", {
            get: function () { return this._parent.formDirective; },
            enumerable: true,
            configurable: true
        });
        Object.defineProperty(NgControlName.prototype, "validator", {
            get: function () { return composeValidators(this._validators); },
            enumerable: true,
            configurable: true
        });
        Object.defineProperty(NgControlName.prototype, "asyncValidator", {
            get: function () {
                return composeAsyncValidators(this._asyncValidators);
            },
            enumerable: true,
            configurable: true
        });
        Object.defineProperty(NgControlName.prototype, "control", {
            get: function () { return this.formDirective.getControl(this); },
            enumerable: true,
            configurable: true
        });
        return NgControlName;
    }(NgControl));
    /** @nocollapse */
    NgControlName.decorators = [
        { type: _angular_core.Directive, args: [{
                    selector: '[ngControl]',
                    providers: [controlNameBinding],
                    inputs: ['name: ngControl', 'model: ngModel'],
                    outputs: ['update: ngModelChange'],
                    exportAs: 'ngForm'
                },] },
    ];
    /** @nocollapse */
    NgControlName.ctorParameters = [
        { type: ControlContainer, decorators: [{ type: _angular_core.Host }, { type: _angular_core.SkipSelf },] },
        { type: Array, decorators: [{ type: _angular_core.Optional }, { type: _angular_core.Self }, { type: _angular_core.Inject, args: [NG_VALIDATORS,] },] },
        { type: Array, decorators: [{ type: _angular_core.Optional }, { type: _angular_core.Self }, { type: _angular_core.Inject, args: [NG_ASYNC_VALIDATORS,] },] },
        { type: Array, decorators: [{ type: _angular_core.Optional }, { type: _angular_core.Self }, { type: _angular_core.Inject, args: [NG_VALUE_ACCESSOR,] },] },
    ];
    var NgControlStatus = (function () {
        function NgControlStatus(cd) {
            this._cd = cd;
        }
        Object.defineProperty(NgControlStatus.prototype, "ngClassUntouched", {
            get: function () {
                return isPresent(this._cd.control) ? this._cd.control.untouched : false;
            },
            enumerable: true,
            configurable: true
        });
        Object.defineProperty(NgControlStatus.prototype, "ngClassTouched", {
            get: function () {
                return isPresent(this._cd.control) ? this._cd.control.touched : false;
            },
            enumerable: true,
            configurable: true
        });
        Object.defineProperty(NgControlStatus.prototype, "ngClassPristine", {
            get: function () {
                return isPresent(this._cd.control) ? this._cd.control.pristine : false;
            },
            enumerable: true,
            configurable: true
        });
        Object.defineProperty(NgControlStatus.prototype, "ngClassDirty", {
            get: function () {
                return isPresent(this._cd.control) ? this._cd.control.dirty : false;
            },
            enumerable: true,
            configurable: true
        });
        Object.defineProperty(NgControlStatus.prototype, "ngClassValid", {
            get: function () {
                return isPresent(this._cd.control) ? this._cd.control.valid : false;
            },
            enumerable: true,
            configurable: true
        });
        Object.defineProperty(NgControlStatus.prototype, "ngClassInvalid", {
            get: function () {
                return isPresent(this._cd.control) ? !this._cd.control.valid : false;
            },
            enumerable: true,
            configurable: true
        });
        return NgControlStatus;
    }());
    /** @nocollapse */
    NgControlStatus.decorators = [
        { type: _angular_core.Directive, args: [{
                    selector: '[ngControl],[ngModel],[ngFormControl]',
                    host: {
                        '[class.ng-untouched]': 'ngClassUntouched',
                        '[class.ng-touched]': 'ngClassTouched',
                        '[class.ng-pristine]': 'ngClassPristine',
                        '[class.ng-dirty]': 'ngClassDirty',
                        '[class.ng-valid]': 'ngClassValid',
                        '[class.ng-invalid]': 'ngClassInvalid'
                    }
                },] },
    ];
    /** @nocollapse */
    NgControlStatus.ctorParameters = [
        { type: NgControl, decorators: [{ type: _angular_core.Self },] },
    ];
    var formDirectiveProvider =
    /*@ts2dart_const*/ { provide: ControlContainer, useExisting: _angular_core.forwardRef(function () { return NgForm; }) };
    var _formWarningDisplayed = false;
    var NgForm = (function (_super) {
        __extends(NgForm, _super);
        function NgForm(validators, asyncValidators) {
            _super.call(this);
            this._submitted = false;
            this.ngSubmit = new EventEmitter$1();
            this._displayWarning();
            this.form = new ControlGroup({}, null, composeValidators(validators), composeAsyncValidators(asyncValidators));
        }
        NgForm.prototype._displayWarning = function () {
            // TODO(kara): Update this when the new forms module becomes the default
            if (!_formWarningDisplayed) {
                _formWarningDisplayed = true;
                console.warn("\n      *It looks like you're using the old forms module. This will be opt-in in the next RC, and\n      will eventually be removed in favor of the new forms module. For more information, see:\n      https://docs.google.com/document/u/1/d/1RIezQqE4aEhBRmArIAS1mRIZtWFf6JxN_7B4meyWK0Y/pub\n    ");
            }
        };
        Object.defineProperty(NgForm.prototype, "submitted", {
            get: function () { return this._submitted; },
            enumerable: true,
            configurable: true
        });
        Object.defineProperty(NgForm.prototype, "formDirective", {
            get: function () { return this; },
            enumerable: true,
            configurable: true
        });
        Object.defineProperty(NgForm.prototype, "control", {
            get: function () { return this.form; },
            enumerable: true,
            configurable: true
        });
        Object.defineProperty(NgForm.prototype, "path", {
            get: function () { return []; },
            enumerable: true,
            configurable: true
        });
        Object.defineProperty(NgForm.prototype, "controls", {
            get: function () { return this.form.controls; },
            enumerable: true,
            configurable: true
        });
        NgForm.prototype.addControl = function (dir) {
            var _this = this;
            PromiseWrapper.scheduleMicrotask(function () {
                var container = _this._findContainer(dir.path);
                var ctrl = new Control();
                setUpControl(ctrl, dir);
                container.registerControl(dir.name, ctrl);
                ctrl.updateValueAndValidity({ emitEvent: false });
            });
        };
        NgForm.prototype.getControl = function (dir) { return this.form.find(dir.path); };
        NgForm.prototype.removeControl = function (dir) {
            var _this = this;
            PromiseWrapper.scheduleMicrotask(function () {
                var container = _this._findContainer(dir.path);
                if (isPresent(container)) {
                    container.removeControl(dir.name);
                }
            });
        };
        NgForm.prototype.addControlGroup = function (dir) {
            var _this = this;
            PromiseWrapper.scheduleMicrotask(function () {
                var container = _this._findContainer(dir.path);
                var group = new ControlGroup({});
                setUpControlGroup(group, dir);
                container.registerControl(dir.name, group);
                group.updateValueAndValidity({ emitEvent: false });
            });
        };
        NgForm.prototype.removeControlGroup = function (dir) {
            var _this = this;
            PromiseWrapper.scheduleMicrotask(function () {
                var container = _this._findContainer(dir.path);
                if (isPresent(container)) {
                    container.removeControl(dir.name);
                }
            });
        };
        NgForm.prototype.getControlGroup = function (dir) {
            return this.form.find(dir.path);
        };
        NgForm.prototype.updateModel = function (dir, value) {
            var _this = this;
            PromiseWrapper.scheduleMicrotask(function () {
                var ctrl = _this.form.find(dir.path);
                ctrl.updateValue(value);
            });
        };
        NgForm.prototype.onSubmit = function () {
            this._submitted = true;
            ObservableWrapper.callEmit(this.ngSubmit, null);
            return false;
        };
        /** @internal */
        NgForm.prototype._findContainer = function (path) {
            path.pop();
            return ListWrapper.isEmpty(path) ? this.form : this.form.find(path);
        };
        return NgForm;
    }(ControlContainer));
    /** @nocollapse */
    NgForm.decorators = [
        { type: _angular_core.Directive, args: [{
                    selector: 'form:not([ngNoForm]):not([ngFormModel]),ngForm,[ngForm]',
                    providers: [formDirectiveProvider],
                    host: {
                        '(submit)': 'onSubmit()',
                    },
                    outputs: ['ngSubmit'],
                    exportAs: 'ngForm'
                },] },
    ];
    /** @nocollapse */
    NgForm.ctorParameters = [
        { type: Array, decorators: [{ type: _angular_core.Optional }, { type: _angular_core.Self }, { type: _angular_core.Inject, args: [NG_VALIDATORS,] },] },
        { type: Array, decorators: [{ type: _angular_core.Optional }, { type: _angular_core.Self }, { type: _angular_core.Inject, args: [NG_ASYNC_VALIDATORS,] },] },
    ];
    var formControlBinding =
    /*@ts2dart_const*/ /* @ts2dart_Provider */ {
        provide: NgControl,
        useExisting: _angular_core.forwardRef(function () { return NgFormControl; })
    };
    var NgFormControl = (function (_super) {
        __extends(NgFormControl, _super);
        function NgFormControl(_validators, _asyncValidators, valueAccessors) {
            _super.call(this);
            this._validators = _validators;
            this._asyncValidators = _asyncValidators;
            this.update = new EventEmitter$1();
            this.valueAccessor = selectValueAccessor(this, valueAccessors);
        }
        NgFormControl.prototype.ngOnChanges = function (changes) {
            if (this._isControlChanged(changes)) {
                setUpControl(this.form, this);
                this.form.updateValueAndValidity({ emitEvent: false });
            }
            if (isPropertyUpdated(changes, this.viewModel)) {
                this.form.updateValue(this.model);
                this.viewModel = this.model;
            }
        };
        Object.defineProperty(NgFormControl.prototype, "path", {
            get: function () { return []; },
            enumerable: true,
            configurable: true
        });
        Object.defineProperty(NgFormControl.prototype, "validator", {
            get: function () { return composeValidators(this._validators); },
            enumerable: true,
            configurable: true
        });
        Object.defineProperty(NgFormControl.prototype, "asyncValidator", {
            get: function () {
                return composeAsyncValidators(this._asyncValidators);
            },
            enumerable: true,
            configurable: true
        });
        Object.defineProperty(NgFormControl.prototype, "control", {
            get: function () { return this.form; },
            enumerable: true,
            configurable: true
        });
        NgFormControl.prototype.viewToModelUpdate = function (newValue) {
            this.viewModel = newValue;
            ObservableWrapper.callEmit(this.update, newValue);
        };
        NgFormControl.prototype._isControlChanged = function (changes) {
            return StringMapWrapper.contains(changes, 'form');
        };
        return NgFormControl;
    }(NgControl));
    /** @nocollapse */
    NgFormControl.decorators = [
        { type: _angular_core.Directive, args: [{
                    selector: '[ngFormControl]',
                    providers: [formControlBinding],
                    inputs: ['form: ngFormControl', 'model: ngModel'],
                    outputs: ['update: ngModelChange'],
                    exportAs: 'ngForm'
                },] },
    ];
    /** @nocollapse */
    NgFormControl.ctorParameters = [
        { type: Array, decorators: [{ type: _angular_core.Optional }, { type: _angular_core.Self }, { type: _angular_core.Inject, args: [NG_VALIDATORS,] },] },
        { type: Array, decorators: [{ type: _angular_core.Optional }, { type: _angular_core.Self }, { type: _angular_core.Inject, args: [NG_ASYNC_VALIDATORS,] },] },
        { type: Array, decorators: [{ type: _angular_core.Optional }, { type: _angular_core.Self }, { type: _angular_core.Inject, args: [NG_VALUE_ACCESSOR,] },] },
    ];
    var formDirectiveProvider$1 =
    /*@ts2dart_const*/ /* @ts2dart_Provider */ {
        provide: ControlContainer,
        useExisting: _angular_core.forwardRef(function () { return NgFormModel; })
    };
    var _formModelWarningDisplayed = false;
    var NgFormModel = (function (_super) {
        __extends(NgFormModel, _super);
        function NgFormModel(_validators, _asyncValidators) {
            _super.call(this);
            this._validators = _validators;
            this._asyncValidators = _asyncValidators;
            this._submitted = false;
            this.form = null;
            this.directives = [];
            this.ngSubmit = new EventEmitter$1();
            this._displayWarning();
        }
        NgFormModel.prototype._displayWarning = function () {
            // TODO(kara): Update this when the new forms module becomes the default
            if (!_formModelWarningDisplayed) {
                _formModelWarningDisplayed = true;
                console.warn("\n      *It looks like you're using the old forms module. This will be opt-in in the next RC, and\n      will eventually be removed in favor of the new forms module. For more information, see:\n      https://docs.google.com/document/u/1/d/1RIezQqE4aEhBRmArIAS1mRIZtWFf6JxN_7B4meyWK0Y/pub\n    ");
            }
        };
        NgFormModel.prototype.ngOnChanges = function (changes) {
            this._checkFormPresent();
            if (StringMapWrapper.contains(changes, 'form')) {
                var sync = composeValidators(this._validators);
                this.form.validator = Validators.compose([this.form.validator, sync]);
                var async = composeAsyncValidators(this._asyncValidators);
                this.form.asyncValidator = Validators.composeAsync([this.form.asyncValidator, async]);
                this.form.updateValueAndValidity({ onlySelf: true, emitEvent: false });
            }
            this._updateDomValue();
        };
        Object.defineProperty(NgFormModel.prototype, "submitted", {
            get: function () { return this._submitted; },
            enumerable: true,
            configurable: true
        });
        Object.defineProperty(NgFormModel.prototype, "formDirective", {
            get: function () { return this; },
            enumerable: true,
            configurable: true
        });
        Object.defineProperty(NgFormModel.prototype, "control", {
            get: function () { return this.form; },
            enumerable: true,
            configurable: true
        });
        Object.defineProperty(NgFormModel.prototype, "path", {
            get: function () { return []; },
            enumerable: true,
            configurable: true
        });
        NgFormModel.prototype.addControl = function (dir) {
            var ctrl = this.form.find(dir.path);
            setUpControl(ctrl, dir);
            ctrl.updateValueAndValidity({ emitEvent: false });
            this.directives.push(dir);
        };
        NgFormModel.prototype.getControl = function (dir) { return this.form.find(dir.path); };
        NgFormModel.prototype.removeControl = function (dir) { ListWrapper.remove(this.directives, dir); };
        NgFormModel.prototype.addControlGroup = function (dir) {
            var ctrl = this.form.find(dir.path);
            setUpControlGroup(ctrl, dir);
            ctrl.updateValueAndValidity({ emitEvent: false });
        };
        NgFormModel.prototype.removeControlGroup = function (dir) { };
        NgFormModel.prototype.getControlGroup = function (dir) {
            return this.form.find(dir.path);
        };
        NgFormModel.prototype.updateModel = function (dir, value) {
            var ctrl = this.form.find(dir.path);
            ctrl.updateValue(value);
        };
        NgFormModel.prototype.onSubmit = function () {
            this._submitted = true;
            ObservableWrapper.callEmit(this.ngSubmit, null);
            return false;
        };
        /** @internal */
        NgFormModel.prototype._updateDomValue = function () {
            var _this = this;
            this.directives.forEach(function (dir) {
                var ctrl = _this.form.find(dir.path);
                dir.valueAccessor.writeValue(ctrl.value);
            });
        };
        NgFormModel.prototype._checkFormPresent = function () {
            if (isBlank(this.form)) {
                throw new BaseException("ngFormModel expects a form. Please pass one in. Example: <form [ngFormModel]=\"myCoolForm\">");
            }
        };
        return NgFormModel;
    }(ControlContainer));
    /** @nocollapse */
    NgFormModel.decorators = [
        { type: _angular_core.Directive, args: [{
                    selector: '[ngFormModel]',
                    providers: [formDirectiveProvider$1],
                    inputs: ['form: ngFormModel'],
                    host: { '(submit)': 'onSubmit()' },
                    outputs: ['ngSubmit'],
                    exportAs: 'ngForm'
                },] },
    ];
    /** @nocollapse */
    NgFormModel.ctorParameters = [
        { type: Array, decorators: [{ type: _angular_core.Optional }, { type: _angular_core.Self }, { type: _angular_core.Inject, args: [NG_VALIDATORS,] },] },
        { type: Array, decorators: [{ type: _angular_core.Optional }, { type: _angular_core.Self }, { type: _angular_core.Inject, args: [NG_ASYNC_VALIDATORS,] },] },
    ];
    var formControlBinding$1 =
    /*@ts2dart_const*/ /* @ts2dart_Provider */ {
        provide: NgControl,
        useExisting: _angular_core.forwardRef(function () { return NgModel; })
    };
    var NgModel = (function (_super) {
        __extends(NgModel, _super);
        function NgModel(_validators, _asyncValidators, valueAccessors) {
            _super.call(this);
            this._validators = _validators;
            this._asyncValidators = _asyncValidators;
            /** @internal */
            this._control = new Control();
            /** @internal */
            this._added = false;
            this.update = new EventEmitter$1();
            this.valueAccessor = selectValueAccessor(this, valueAccessors);
        }
        NgModel.prototype.ngOnChanges = function (changes) {
            if (!this._added) {
                setUpControl(this._control, this);
                this._control.updateValueAndValidity({ emitEvent: false });
                this._added = true;
            }
            if (isPropertyUpdated(changes, this.viewModel)) {
                this._control.updateValue(this.model);
                this.viewModel = this.model;
            }
        };
        Object.defineProperty(NgModel.prototype, "control", {
            get: function () { return this._control; },
            enumerable: true,
            configurable: true
        });
        Object.defineProperty(NgModel.prototype, "path", {
            get: function () { return []; },
            enumerable: true,
            configurable: true
        });
        Object.defineProperty(NgModel.prototype, "validator", {
            get: function () { return composeValidators(this._validators); },
            enumerable: true,
            configurable: true
        });
        Object.defineProperty(NgModel.prototype, "asyncValidator", {
            get: function () {
                return composeAsyncValidators(this._asyncValidators);
            },
            enumerable: true,
            configurable: true
        });
        NgModel.prototype.viewToModelUpdate = function (newValue) {
            this.viewModel = newValue;
            ObservableWrapper.callEmit(this.update, newValue);
        };
        return NgModel;
    }(NgControl));
    /** @nocollapse */
    NgModel.decorators = [
        { type: _angular_core.Directive, args: [{
                    selector: '[ngModel]:not([ngControl]):not([ngFormControl])',
                    providers: [formControlBinding$1],
                    inputs: ['model: ngModel'],
                    outputs: ['update: ngModelChange'],
                    exportAs: 'ngForm'
                },] },
    ];
    /** @nocollapse */
    NgModel.ctorParameters = [
        { type: Array, decorators: [{ type: _angular_core.Optional }, { type: _angular_core.Self }, { type: _angular_core.Inject, args: [NG_VALIDATORS,] },] },
        { type: Array, decorators: [{ type: _angular_core.Optional }, { type: _angular_core.Self }, { type: _angular_core.Inject, args: [NG_ASYNC_VALIDATORS,] },] },
        { type: Array, decorators: [{ type: _angular_core.Optional }, { type: _angular_core.Self }, { type: _angular_core.Inject, args: [NG_VALUE_ACCESSOR,] },] },
    ];
    var REQUIRED = Validators.required;
    var REQUIRED_VALIDATOR = {
        provide: NG_VALIDATORS,
        useValue: REQUIRED,
        multi: true
    };
    var RequiredValidator = (function () {
        function RequiredValidator() {
        }
        return RequiredValidator;
    }());
    /** @nocollapse */
    RequiredValidator.decorators = [
        { type: _angular_core.Directive, args: [{
                    selector: '[required][ngControl],[required][ngFormControl],[required][ngModel]',
                    providers: [REQUIRED_VALIDATOR]
                },] },
    ];
    /**
     * Provivder which adds {@link MinLengthValidator} to {@link NG_VALIDATORS}.
     *
     * ## Example:
     *
     * {@example common/forms/ts/validators/validators.ts region='min'}
     */
    var MIN_LENGTH_VALIDATOR = {
        provide: NG_VALIDATORS,
        useExisting: _angular_core.forwardRef(function () { return MinLengthValidator; }),
        multi: true
    };
    var MinLengthValidator = (function () {
        function MinLengthValidator(minLength) {
            this._validator = Validators.minLength(NumberWrapper.parseInt(minLength, 10));
        }
        MinLengthValidator.prototype.validate = function (c) { return this._validator(c); };
        return MinLengthValidator;
    }());
    /** @nocollapse */
    MinLengthValidator.decorators = [
        { type: _angular_core.Directive, args: [{
                    selector: '[minlength][ngControl],[minlength][ngFormControl],[minlength][ngModel]',
                    providers: [MIN_LENGTH_VALIDATOR]
                },] },
    ];
    /** @nocollapse */
    MinLengthValidator.ctorParameters = [
        { type: undefined, decorators: [{ type: _angular_core.Attribute, args: ['minlength',] },] },
    ];
    /**
     * Provider which adds {@link MaxLengthValidator} to {@link NG_VALIDATORS}.
     *
     * ## Example:
     *
     * {@example common/forms/ts/validators/validators.ts region='max'}
     */
    var MAX_LENGTH_VALIDATOR = {
        provide: NG_VALIDATORS,
        useExisting: _angular_core.forwardRef(function () { return MaxLengthValidator; }),
        multi: true
    };
    var MaxLengthValidator = (function () {
        function MaxLengthValidator(maxLength) {
            this._validator = Validators.maxLength(NumberWrapper.parseInt(maxLength, 10));
        }
        MaxLengthValidator.prototype.validate = function (c) { return this._validator(c); };
        return MaxLengthValidator;
    }());
    /** @nocollapse */
    MaxLengthValidator.decorators = [
        { type: _angular_core.Directive, args: [{
                    selector: '[maxlength][ngControl],[maxlength][ngFormControl],[maxlength][ngModel]',
                    providers: [MAX_LENGTH_VALIDATOR]
                },] },
    ];
    /** @nocollapse */
    MaxLengthValidator.ctorParameters = [
        { type: undefined, decorators: [{ type: _angular_core.Attribute, args: ['maxlength',] },] },
    ];
    var PATTERN_VALIDATOR = {
        provide: NG_VALIDATORS,
        useExisting: _angular_core.forwardRef(function () { return PatternValidator; }),
        multi: true
    };
    var PatternValidator = (function () {
        function PatternValidator(pattern) {
            this._validator = Validators.pattern(pattern);
        }
        PatternValidator.prototype.validate = function (c) { return this._validator(c); };
        return PatternValidator;
    }());
    /** @nocollapse */
    PatternValidator.decorators = [
        { type: _angular_core.Directive, args: [{
                    selector: '[pattern][ngControl],[pattern][ngFormControl],[pattern][ngModel]',
                    providers: [PATTERN_VALIDATOR]
                },] },
    ];
    /** @nocollapse */
    PatternValidator.ctorParameters = [
        { type: undefined, decorators: [{ type: _angular_core.Attribute, args: ['pattern',] },] },
    ];
    /**
     *
     * A list of all the form directives used as part of a `@Component` annotation.
     *
     *  This is a shorthand for importing them each individually.
     *
     * ### Example
     *
     * ```typescript
     * @Component({
     *   selector: 'my-app',
     *   directives: [FORM_DIRECTIVES]
     * })
     * class MyApp {}
     * ```
     * @experimental
     */
    var FORM_DIRECTIVES = [
        NgControlName,
        NgControlGroup,
        NgFormControl,
        NgModel,
        NgFormModel,
        NgForm,
        NgSelectOption,
        NgSelectMultipleOption,
        DefaultValueAccessor,
        NumberValueAccessor,
        CheckboxControlValueAccessor,
        SelectControlValueAccessor,
        SelectMultipleControlValueAccessor,
        RadioControlValueAccessor,
        NgControlStatus,
        RequiredValidator,
        MinLengthValidator,
        MaxLengthValidator,
        PatternValidator,
    ];
    /**
     * Shorthand set of providers used for building Angular forms.
     *
     * ### Example
     *
     * ```typescript
     * bootstrap(MyApp, [FORM_PROVIDERS]);
     * ```
     *
     * @experimental
     */
    var FORM_PROVIDERS = [FormBuilder, RadioControlRegistry];
    /**
     * A collection of Angular core directives that are likely to be used in each and every Angular
     * application. This includes core directives (e.g., NgIf and NgFor), and forms directives (e.g.,
     * NgModel).
     *
     * This collection can be used to quickly enumerate all the built-in directives in the `directives`
     * property of the `@Component` decorator.
     *
     * ### Example
     *
     * Instead of writing:
     *
     * ```typescript
     * import {NgClass, NgIf, NgFor, NgSwitch, NgSwitchWhen, NgSwitchDefault, NgModel, NgForm} from
     * '@angular/common';
     * import {OtherDirective} from './myDirectives';
     *
     * @Component({
     *   selector: 'my-component',
     *   templateUrl: 'myComponent.html',
     *   directives: [NgClass, NgIf, NgFor, NgSwitch, NgSwitchWhen, NgSwitchDefault, NgModel, NgForm,
     * OtherDirective]
     * })
     * export class MyComponent {
     *   ...
     * }
     * ```
     * one could import all the common directives at once:
     *
     * ```typescript
     * import {COMMON_DIRECTIVES} from '@angular/common';
     * import {OtherDirective} from './myDirectives';
     *
     * @Component({
     *   selector: 'my-component',
     *   templateUrl: 'myComponent.html',
     *   directives: [COMMON_DIRECTIVES, OtherDirective]
     * })
     * export class MyComponent {
     *   ...
     * }
     * ```
     *
     * @experimental Contains forms which are experimental.
     */
    var COMMON_DIRECTIVES = [CORE_DIRECTIVES, FORM_DIRECTIVES];
    /**
     * @license
     * Copyright Google Inc. All Rights Reserved.
     *
     * Use of this source code is governed by an MIT-style license that can be
     * found in the LICENSE file at https://angular.io/license
     */
    /**
     * This class should not be used directly by an application developer. Instead, use
     * {@link Location}.
     *
     * `PlatformLocation` encapsulates all calls to DOM apis, which allows the Router to be platform
     * agnostic.
     * This means that we can have different implementation of `PlatformLocation` for the different
     * platforms
     * that angular supports. For example, the default `PlatformLocation` is {@link
     * BrowserPlatformLocation},
     * however when you run your app in a WebWorker you use {@link WebWorkerPlatformLocation}.
     *
     * The `PlatformLocation` class is used directly by all implementations of {@link LocationStrategy}
     * when
     * they need to interact with the DOM apis like pushState, popState, etc...
     *
     * {@link LocationStrategy} in turn is used by the {@link Location} service which is used directly
     * by
     * the {@link Router} in order to navigate between routes. Since all interactions between {@link
     * Router} /
     * {@link Location} / {@link LocationStrategy} and DOM apis flow through the `PlatformLocation`
     * class
     * they are all platform independent.
     *
     * @stable
     */
    var PlatformLocation = (function () {
        function PlatformLocation() {
        }
        Object.defineProperty(PlatformLocation.prototype, "pathname", {
            get: function () { return null; },
            enumerable: true,
            configurable: true
        });
        Object.defineProperty(PlatformLocation.prototype, "search", {
            get: function () { return null; },
            enumerable: true,
            configurable: true
        });
        Object.defineProperty(PlatformLocation.prototype, "hash", {
            get: function () { return null; },
            enumerable: true,
            configurable: true
        });
        return PlatformLocation;
    }());
    /**
     * `LocationStrategy` is responsible for representing and reading route state
     * from the browser's URL. Angular provides two strategies:
     * {@link HashLocationStrategy} and {@link PathLocationStrategy} (default).
     *
     * This is used under the hood of the {@link Location} service.
     *
     * Applications should use the {@link Router} or {@link Location} services to
     * interact with application route state.
     *
     * For instance, {@link HashLocationStrategy} produces URLs like
     * `http://example.com#/foo`, and {@link PathLocationStrategy} produces
     * `http://example.com/foo` as an equivalent URL.
     *
     * See these two classes for more.
     *
     * @stable
     */
    var LocationStrategy = (function () {
        function LocationStrategy() {
        }
        return LocationStrategy;
    }());
    /**
     * The `APP_BASE_HREF` token represents the base href to be used with the
     * {@link PathLocationStrategy}.
     *
     * If you're using {@link PathLocationStrategy}, you must provide a provider to a string
     * representing the URL prefix that should be preserved when generating and recognizing
     * URLs.
     *
     * ### Example
     *
     * ```
     * import {Component} from '@angular/core';
     * import {ROUTER_DIRECTIVES, ROUTER_PROVIDERS, RouteConfig} from '@angular/router';
     * import {APP_BASE_HREF} from '@angular/common';
     *
     * @Component({directives: [ROUTER_DIRECTIVES]})
     * @RouteConfig([
     *  {...},
     * ])
     * class AppCmp {
     *   // ...
     * }
     *
     * bootstrap(AppCmp, [
     *   ROUTER_PROVIDERS,
     *   {provide: APP_BASE_HREF, useValue: '/my/app'}
     * ]);
     * ```
     * @stable
     */
    var APP_BASE_HREF = new _angular_core.OpaqueToken('appBaseHref');
    var Location = (function () {
        function Location(platformStrategy) {
            var _this = this;
            /** @internal */
            this._subject = new _angular_core.EventEmitter();
            this._platformStrategy = platformStrategy;
            var browserBaseHref = this._platformStrategy.getBaseHref();
            this._baseHref = Location.stripTrailingSlash(_stripIndexHtml(browserBaseHref));
            this._platformStrategy.onPopState(function (ev) {
                ObservableWrapper.callEmit(_this._subject, { 'url': _this.path(true), 'pop': true, 'type': ev.type });
            });
        }
        /**
         * Returns the normalized URL path.
         */
        // TODO: vsavkin. Remove the boolean flag and always include hash once the deprecated router is
        // removed.
        Location.prototype.path = function (includeHash) {
            if (includeHash === void 0) { includeHash = false; }
            return this.normalize(this._platformStrategy.path(includeHash));
        };
        /**
         * Normalizes the given path and compares to the current normalized path.
         */
        Location.prototype.isCurrentPathEqualTo = function (path, query) {
            if (query === void 0) { query = ''; }
            return this.path() == this.normalize(path + Location.normalizeQueryParams(query));
        };
        /**
         * Given a string representing a URL, returns the normalized URL path without leading or
         * trailing slashes
         */
        Location.prototype.normalize = function (url) {
            return Location.stripTrailingSlash(_stripBaseHref(this._baseHref, _stripIndexHtml(url)));
        };
        /**
         * Given a string representing a URL, returns the platform-specific external URL path.
         * If the given URL doesn't begin with a leading slash (`'/'`), this method adds one
         * before normalizing. This method will also add a hash if `HashLocationStrategy` is
         * used, or the `APP_BASE_HREF` if the `PathLocationStrategy` is in use.
         */
        Location.prototype.prepareExternalUrl = function (url) {
            if (url.length > 0 && !url.startsWith('/')) {
                url = '/' + url;
            }
            return this._platformStrategy.prepareExternalUrl(url);
        };
        // TODO: rename this method to pushState
        /**
         * Changes the browsers URL to the normalized version of the given URL, and pushes a
         * new item onto the platform's history.
         */
        Location.prototype.go = function (path, query) {
            if (query === void 0) { query = ''; }
            this._platformStrategy.pushState(null, '', path, query);
        };
        /**
         * Changes the browsers URL to the normalized version of the given URL, and replaces
         * the top item on the platform's history stack.
         */
        Location.prototype.replaceState = function (path, query) {
            if (query === void 0) { query = ''; }
            this._platformStrategy.replaceState(null, '', path, query);
        };
        /**
         * Navigates forward in the platform's history.
         */
        Location.prototype.forward = function () { this._platformStrategy.forward(); };
        /**
         * Navigates back in the platform's history.
         */
        Location.prototype.back = function () { this._platformStrategy.back(); };
        /**
         * Subscribe to the platform's `popState` events.
         */
        Location.prototype.subscribe = function (onNext, onThrow, onReturn) {
            if (onThrow === void 0) { onThrow = null; }
            if (onReturn === void 0) { onReturn = null; }
            return ObservableWrapper.subscribe(this._subject, onNext, onThrow, onReturn);
        };
        /**
         * Given a string of url parameters, prepend with '?' if needed, otherwise return parameters as
         * is.
         */
        Location.normalizeQueryParams = function (params) {
            return (params.length > 0 && params.substring(0, 1) != '?') ? ('?' + params) : params;
        };
        /**
         * Given 2 parts of a url, join them with a slash if needed.
         */
        Location.joinWithSlash = function (start, end) {
            if (start.length == 0) {
                return end;
            }
            if (end.length == 0) {
                return start;
            }
            var slashes = 0;
            if (start.endsWith('/')) {
                slashes++;
            }
            if (end.startsWith('/')) {
                slashes++;
            }
            if (slashes == 2) {
                return start + end.substring(1);
            }
            if (slashes == 1) {
                return start + end;
            }
            return start + '/' + end;
        };
        /**
         * If url has a trailing slash, remove it, otherwise return url as is.
         */
        Location.stripTrailingSlash = function (url) {
            if (/\/$/g.test(url)) {
                url = url.substring(0, url.length - 1);
            }
            return url;
        };
        return Location;
    }());
    /** @nocollapse */
    Location.decorators = [
        { type: _angular_core.Injectable },
    ];
    /** @nocollapse */
    Location.ctorParameters = [
        { type: LocationStrategy, },
    ];
    function _stripBaseHref(baseHref, url) {
        if (baseHref.length > 0 && url.startsWith(baseHref)) {
            return url.substring(baseHref.length);
        }
        return url;
    }
    function _stripIndexHtml(url) {
        if (/\/index.html$/g.test(url)) {
            // '/index.html'.length == 11
            return url.substring(0, url.length - 11);
        }
        return url;
    }
    var HashLocationStrategy = (function (_super) {
        __extends(HashLocationStrategy, _super);
        function HashLocationStrategy(_platformLocation, _baseHref) {
            _super.call(this);
            this._platformLocation = _platformLocation;
            this._baseHref = '';
            if (isPresent(_baseHref)) {
                this._baseHref = _baseHref;
            }
        }
        HashLocationStrategy.prototype.onPopState = function (fn) {
            this._platformLocation.onPopState(fn);
            this._platformLocation.onHashChange(fn);
        };
        HashLocationStrategy.prototype.getBaseHref = function () { return this._baseHref; };
        HashLocationStrategy.prototype.path = function (includeHash) {
            if (includeHash === void 0) { includeHash = false; }
            // the hash value is always prefixed with a `#`
            // and if it is empty then it will stay empty
            var path = this._platformLocation.hash;
            if (!isPresent(path))
                path = '#';
            // Dart will complain if a call to substring is
            // executed with a position value that extends the
            // length of string.
            return (path.length > 0 ? path.substring(1) : path);
        };
        HashLocationStrategy.prototype.prepareExternalUrl = function (internal) {
            var url = Location.joinWithSlash(this._baseHref, internal);
            return url.length > 0 ? ('#' + url) : url;
        };
        HashLocationStrategy.prototype.pushState = function (state, title, path, queryParams) {
            var url = this.prepareExternalUrl(path + Location.normalizeQueryParams(queryParams));
            if (url.length == 0) {
                url = this._platformLocation.pathname;
            }
            this._platformLocation.pushState(state, title, url);
        };
        HashLocationStrategy.prototype.replaceState = function (state, title, path, queryParams) {
            var url = this.prepareExternalUrl(path + Location.normalizeQueryParams(queryParams));
            if (url.length == 0) {
                url = this._platformLocation.pathname;
            }
            this._platformLocation.replaceState(state, title, url);
        };
        HashLocationStrategy.prototype.forward = function () { this._platformLocation.forward(); };
        HashLocationStrategy.prototype.back = function () { this._platformLocation.back(); };
        return HashLocationStrategy;
    }(LocationStrategy));
    /** @nocollapse */
    HashLocationStrategy.decorators = [
        { type: _angular_core.Injectable },
    ];
    /** @nocollapse */
    HashLocationStrategy.ctorParameters = [
        { type: PlatformLocation, },
        { type: undefined, decorators: [{ type: _angular_core.Optional }, { type: _angular_core.Inject, args: [APP_BASE_HREF,] },] },
    ];
    var PathLocationStrategy = (function (_super) {
        __extends(PathLocationStrategy, _super);
        function PathLocationStrategy(_platformLocation, href) {
            _super.call(this);
            this._platformLocation = _platformLocation;
            if (isBlank(href)) {
                href = this._platformLocation.getBaseHrefFromDOM();
            }
            if (isBlank(href)) {
                throw new BaseException("No base href set. Please provide a value for the APP_BASE_HREF token or add a base element to the document.");
            }
            this._baseHref = href;
        }
        PathLocationStrategy.prototype.onPopState = function (fn) {
            this._platformLocation.onPopState(fn);
            this._platformLocation.onHashChange(fn);
        };
        PathLocationStrategy.prototype.getBaseHref = function () { return this._baseHref; };
        PathLocationStrategy.prototype.prepareExternalUrl = function (internal) {
            return Location.joinWithSlash(this._baseHref, internal);
        };
        PathLocationStrategy.prototype.path = function (includeHash) {
            if (includeHash === void 0) { includeHash = false; }
            var pathname = this._platformLocation.pathname +
                Location.normalizeQueryParams(this._platformLocation.search);
            var hash = this._platformLocation.hash;
            return hash && includeHash ? "" + pathname + hash : pathname;
        };
        PathLocationStrategy.prototype.pushState = function (state, title, url, queryParams) {
            var externalUrl = this.prepareExternalUrl(url + Location.normalizeQueryParams(queryParams));
            this._platformLocation.pushState(state, title, externalUrl);
        };
        PathLocationStrategy.prototype.replaceState = function (state, title, url, queryParams) {
            var externalUrl = this.prepareExternalUrl(url + Location.normalizeQueryParams(queryParams));
            this._platformLocation.replaceState(state, title, externalUrl);
        };
        PathLocationStrategy.prototype.forward = function () { this._platformLocation.forward(); };
        PathLocationStrategy.prototype.back = function () { this._platformLocation.back(); };
        return PathLocationStrategy;
    }(LocationStrategy));
    /** @nocollapse */
    PathLocationStrategy.decorators = [
        { type: _angular_core.Injectable },
    ];
    /** @nocollapse */
    PathLocationStrategy.ctorParameters = [
        { type: PlatformLocation, },
        { type: undefined, decorators: [{ type: _angular_core.Optional }, { type: _angular_core.Inject, args: [APP_BASE_HREF,] },] },
    ];
    exports.NgLocalization = NgLocalization;
    exports.AsyncPipe = AsyncPipe;
    exports.COMMON_PIPES = COMMON_PIPES;
    exports.DatePipe = DatePipe;
    exports.I18nPluralPipe = I18nPluralPipe;
    exports.I18nSelectPipe = I18nSelectPipe;
    exports.JsonPipe = JsonPipe;
    exports.LowerCasePipe = LowerCasePipe;
    exports.CurrencyPipe = CurrencyPipe;
    exports.DecimalPipe = DecimalPipe;
    exports.PercentPipe = PercentPipe;
    exports.ReplacePipe = ReplacePipe;
    exports.SlicePipe = SlicePipe;
    exports.UpperCasePipe = UpperCasePipe;
    exports.CORE_DIRECTIVES = CORE_DIRECTIVES;
    exports.NgClass = NgClass;
    exports.NgFor = NgFor;
    exports.NgIf = NgIf;
    exports.NgPlural = NgPlural;
    exports.NgPluralCase = NgPluralCase;
    exports.NgStyle = NgStyle;
    exports.NgSwitch = NgSwitch;
    exports.NgSwitchCase = NgSwitchCase;
    exports.NgSwitchDefault = NgSwitchDefault;
    exports.NgTemplateOutlet = NgTemplateOutlet;
    exports.FORM_PROVIDERS = FORM_PROVIDERS;
    exports.FORM_DIRECTIVES = FORM_DIRECTIVES;
    exports.RadioButtonState = RadioButtonState;
    exports.AbstractControlDirective = AbstractControlDirective;
    exports.CheckboxControlValueAccessor = CheckboxControlValueAccessor;
    exports.ControlContainer = ControlContainer;
    exports.NG_VALUE_ACCESSOR = NG_VALUE_ACCESSOR;
    exports.DefaultValueAccessor = DefaultValueAccessor;
    exports.NgControl = NgControl;
    exports.NgControlGroup = NgControlGroup;
    exports.NgControlName = NgControlName;
    exports.NgControlStatus = NgControlStatus;
    exports.NgForm = NgForm;
    exports.NgFormControl = NgFormControl;
    exports.NgFormModel = NgFormModel;
    exports.NgModel = NgModel;
    exports.NgSelectOption = NgSelectOption;
    exports.SelectControlValueAccessor = SelectControlValueAccessor;
    exports.MaxLengthValidator = MaxLengthValidator;
    exports.MinLengthValidator = MinLengthValidator;
    exports.PatternValidator = PatternValidator;
    exports.RequiredValidator = RequiredValidator;
    exports.FormBuilder = FormBuilder;
    exports.AbstractControl = AbstractControl;
    exports.Control = Control;
    exports.ControlArray = ControlArray;
    exports.ControlGroup = ControlGroup;
    exports.NG_ASYNC_VALIDATORS = NG_ASYNC_VALIDATORS;
    exports.NG_VALIDATORS = NG_VALIDATORS;
    exports.Validators = Validators;
    exports.COMMON_DIRECTIVES = COMMON_DIRECTIVES;
    exports.PlatformLocation = PlatformLocation;
    exports.LocationStrategy = LocationStrategy;
    exports.APP_BASE_HREF = APP_BASE_HREF;
    exports.HashLocationStrategy = HashLocationStrategy;
    exports.PathLocationStrategy = PathLocationStrategy;
    exports.Location = Location;
}));
