/**
 * @license
 * Copyright Google Inc. All Rights Reserved.
 *
 * Use of this source code is governed by an MIT-style license that can be
 * found in the LICENSE file at https://angular.io/license
 */
"use strict";
var core_1 = require('@angular/core');
var browser_platform_location_1 = require('../../browser/location/browser_platform_location');
var async_1 = require('../../facade/async');
var lang_1 = require('../../facade/lang');
var message_bus_1 = require('../shared/message_bus');
var messaging_api_1 = require('../shared/messaging_api');
var serialized_types_1 = require('../shared/serialized_types');
var serializer_1 = require('../shared/serializer');
var service_message_broker_1 = require('../shared/service_message_broker');
var MessageBasedPlatformLocation = (function () {
    function MessageBasedPlatformLocation(_brokerFactory, _platformLocation, bus, _serializer) {
        this._brokerFactory = _brokerFactory;
        this._platformLocation = _platformLocation;
        this._serializer = _serializer;
        this._platformLocation.onPopState(lang_1.FunctionWrapper.bind(this._sendUrlChangeEvent, this));
        this._platformLocation.onHashChange(lang_1.FunctionWrapper.bind(this._sendUrlChangeEvent, this));
        this._broker = this._brokerFactory.createMessageBroker(messaging_api_1.ROUTER_CHANNEL);
        this._channelSink = bus.to(messaging_api_1.ROUTER_CHANNEL);
    }
    MessageBasedPlatformLocation.prototype.start = function () {
        this._broker.registerMethod('getLocation', null, lang_1.FunctionWrapper.bind(this._getLocation, this), serialized_types_1.LocationType);
        this._broker.registerMethod('setPathname', [serializer_1.PRIMITIVE], lang_1.FunctionWrapper.bind(this._setPathname, this));
        this._broker.registerMethod('pushState', [serializer_1.PRIMITIVE, serializer_1.PRIMITIVE, serializer_1.PRIMITIVE], lang_1.FunctionWrapper.bind(this._platformLocation.pushState, this._platformLocation));
        this._broker.registerMethod('replaceState', [serializer_1.PRIMITIVE, serializer_1.PRIMITIVE, serializer_1.PRIMITIVE], lang_1.FunctionWrapper.bind(this._platformLocation.replaceState, this._platformLocation));
        this._broker.registerMethod('forward', null, lang_1.FunctionWrapper.bind(this._platformLocation.forward, this._platformLocation));
        this._broker.registerMethod('back', null, lang_1.FunctionWrapper.bind(this._platformLocation.back, this._platformLocation));
    };
    MessageBasedPlatformLocation.prototype._getLocation = function () {
        return async_1.PromiseWrapper.resolve(this._platformLocation.location);
    };
    MessageBasedPlatformLocation.prototype._sendUrlChangeEvent = function (e) {
        var loc = this._serializer.serialize(this._platformLocation.location, serialized_types_1.LocationType);
        var serializedEvent = { 'type': e.type };
        async_1.ObservableWrapper.callEmit(this._channelSink, { 'event': serializedEvent, 'location': loc });
    };
    MessageBasedPlatformLocation.prototype._setPathname = function (pathname) { this._platformLocation.pathname = pathname; };
    /** @nocollapse */
    MessageBasedPlatformLocation.decorators = [
        { type: core_1.Injectable },
    ];
    /** @nocollapse */
    MessageBasedPlatformLocation.ctorParameters = [
        { type: service_message_broker_1.ServiceMessageBrokerFactory, },
        { type: browser_platform_location_1.BrowserPlatformLocation, },
        { type: message_bus_1.MessageBus, },
        { type: serializer_1.Serializer, },
    ];
    return MessageBasedPlatformLocation;
}());
exports.MessageBasedPlatformLocation = MessageBasedPlatformLocation;
//# sourceMappingURL=platform_location.js.map