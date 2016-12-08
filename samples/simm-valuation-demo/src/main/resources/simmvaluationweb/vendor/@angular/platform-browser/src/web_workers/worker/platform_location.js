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
var common_1 = require('@angular/common');
var core_1 = require('@angular/core');
var async_1 = require('../../facade/async');
var collection_1 = require('../../facade/collection');
var exceptions_1 = require('../../facade/exceptions');
var lang_1 = require('../../facade/lang');
var client_message_broker_1 = require('../shared/client_message_broker');
var message_bus_1 = require('../shared/message_bus');
var messaging_api_1 = require('../shared/messaging_api');
var serialized_types_1 = require('../shared/serialized_types');
var serializer_1 = require('../shared/serializer');
var event_deserializer_1 = require('./event_deserializer');
var WebWorkerPlatformLocation = (function (_super) {
    __extends(WebWorkerPlatformLocation, _super);
    function WebWorkerPlatformLocation(brokerFactory, bus, _serializer) {
        var _this = this;
        _super.call(this);
        this._serializer = _serializer;
        this._popStateListeners = [];
        this._hashChangeListeners = [];
        this._location = null;
        this._broker = brokerFactory.createMessageBroker(messaging_api_1.ROUTER_CHANNEL);
        this._channelSource = bus.from(messaging_api_1.ROUTER_CHANNEL);
        async_1.ObservableWrapper.subscribe(this._channelSource, function (msg) {
            var listeners = null;
            if (collection_1.StringMapWrapper.contains(msg, 'event')) {
                var type = msg['event']['type'];
                if (lang_1.StringWrapper.equals(type, 'popstate')) {
                    listeners = _this._popStateListeners;
                }
                else if (lang_1.StringWrapper.equals(type, 'hashchange')) {
                    listeners = _this._hashChangeListeners;
                }
                if (listeners !== null) {
                    var e_1 = event_deserializer_1.deserializeGenericEvent(msg['event']);
                    // There was a popState or hashChange event, so the location object thas been updated
                    _this._location = _this._serializer.deserialize(msg['location'], serialized_types_1.LocationType);
                    listeners.forEach(function (fn) { return fn(e_1); });
                }
            }
        });
    }
    /** @internal **/
    WebWorkerPlatformLocation.prototype.init = function () {
        var _this = this;
        var args = new client_message_broker_1.UiArguments('getLocation');
        var locationPromise = this._broker.runOnService(args, serialized_types_1.LocationType);
        return async_1.PromiseWrapper.then(locationPromise, function (val) {
            _this._location = val;
            return true;
        }, function (err) { throw new exceptions_1.BaseException(err); });
    };
    WebWorkerPlatformLocation.prototype.getBaseHrefFromDOM = function () {
        throw new exceptions_1.BaseException('Attempt to get base href from DOM from WebWorker. You must either provide a value for the APP_BASE_HREF token through DI or use the hash location strategy.');
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
                throw new exceptions_1.BaseException('Attempt to set pathname before value is obtained from UI');
            }
            this._location.pathname = newPath;
            var fnArgs = [new client_message_broker_1.FnArg(newPath, serializer_1.PRIMITIVE)];
            var args = new client_message_broker_1.UiArguments('setPathname', fnArgs);
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
        var fnArgs = [new client_message_broker_1.FnArg(state, serializer_1.PRIMITIVE), new client_message_broker_1.FnArg(title, serializer_1.PRIMITIVE), new client_message_broker_1.FnArg(url, serializer_1.PRIMITIVE)];
        var args = new client_message_broker_1.UiArguments('pushState', fnArgs);
        this._broker.runOnService(args, null);
    };
    WebWorkerPlatformLocation.prototype.replaceState = function (state, title, url) {
        var fnArgs = [new client_message_broker_1.FnArg(state, serializer_1.PRIMITIVE), new client_message_broker_1.FnArg(title, serializer_1.PRIMITIVE), new client_message_broker_1.FnArg(url, serializer_1.PRIMITIVE)];
        var args = new client_message_broker_1.UiArguments('replaceState', fnArgs);
        this._broker.runOnService(args, null);
    };
    WebWorkerPlatformLocation.prototype.forward = function () {
        var args = new client_message_broker_1.UiArguments('forward');
        this._broker.runOnService(args, null);
    };
    WebWorkerPlatformLocation.prototype.back = function () {
        var args = new client_message_broker_1.UiArguments('back');
        this._broker.runOnService(args, null);
    };
    /** @nocollapse */
    WebWorkerPlatformLocation.decorators = [
        { type: core_1.Injectable },
    ];
    /** @nocollapse */
    WebWorkerPlatformLocation.ctorParameters = [
        { type: client_message_broker_1.ClientMessageBrokerFactory, },
        { type: message_bus_1.MessageBus, },
        { type: serializer_1.Serializer, },
    ];
    return WebWorkerPlatformLocation;
}(common_1.PlatformLocation));
exports.WebWorkerPlatformLocation = WebWorkerPlatformLocation;
//# sourceMappingURL=platform_location.js.map