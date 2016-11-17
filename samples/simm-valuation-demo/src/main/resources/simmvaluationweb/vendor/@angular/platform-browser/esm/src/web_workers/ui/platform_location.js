/**
 * @license
 * Copyright Google Inc. All Rights Reserved.
 *
 * Use of this source code is governed by an MIT-style license that can be
 * found in the LICENSE file at https://angular.io/license
 */
import { Injectable } from '@angular/core';
import { BrowserPlatformLocation } from '../../browser/location/browser_platform_location';
import { ObservableWrapper, PromiseWrapper } from '../../facade/async';
import { FunctionWrapper } from '../../facade/lang';
import { MessageBus } from '../shared/message_bus';
import { ROUTER_CHANNEL } from '../shared/messaging_api';
import { LocationType } from '../shared/serialized_types';
import { PRIMITIVE, Serializer } from '../shared/serializer';
import { ServiceMessageBrokerFactory } from '../shared/service_message_broker';
export class MessageBasedPlatformLocation {
    constructor(_brokerFactory, _platformLocation, bus, _serializer) {
        this._brokerFactory = _brokerFactory;
        this._platformLocation = _platformLocation;
        this._serializer = _serializer;
        this._platformLocation.onPopState(FunctionWrapper.bind(this._sendUrlChangeEvent, this));
        this._platformLocation.onHashChange(FunctionWrapper.bind(this._sendUrlChangeEvent, this));
        this._broker = this._brokerFactory.createMessageBroker(ROUTER_CHANNEL);
        this._channelSink = bus.to(ROUTER_CHANNEL);
    }
    start() {
        this._broker.registerMethod('getLocation', null, FunctionWrapper.bind(this._getLocation, this), LocationType);
        this._broker.registerMethod('setPathname', [PRIMITIVE], FunctionWrapper.bind(this._setPathname, this));
        this._broker.registerMethod('pushState', [PRIMITIVE, PRIMITIVE, PRIMITIVE], FunctionWrapper.bind(this._platformLocation.pushState, this._platformLocation));
        this._broker.registerMethod('replaceState', [PRIMITIVE, PRIMITIVE, PRIMITIVE], FunctionWrapper.bind(this._platformLocation.replaceState, this._platformLocation));
        this._broker.registerMethod('forward', null, FunctionWrapper.bind(this._platformLocation.forward, this._platformLocation));
        this._broker.registerMethod('back', null, FunctionWrapper.bind(this._platformLocation.back, this._platformLocation));
    }
    _getLocation() {
        return PromiseWrapper.resolve(this._platformLocation.location);
    }
    _sendUrlChangeEvent(e) {
        let loc = this._serializer.serialize(this._platformLocation.location, LocationType);
        let serializedEvent = { 'type': e.type };
        ObservableWrapper.callEmit(this._channelSink, { 'event': serializedEvent, 'location': loc });
    }
    _setPathname(pathname) { this._platformLocation.pathname = pathname; }
}
/** @nocollapse */
MessageBasedPlatformLocation.decorators = [
    { type: Injectable },
];
/** @nocollapse */
MessageBasedPlatformLocation.ctorParameters = [
    { type: ServiceMessageBrokerFactory, },
    { type: BrowserPlatformLocation, },
    { type: MessageBus, },
    { type: Serializer, },
];
//# sourceMappingURL=platform_location.js.map