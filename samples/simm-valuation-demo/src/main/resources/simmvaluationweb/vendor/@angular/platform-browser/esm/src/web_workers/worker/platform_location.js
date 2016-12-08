/**
 * @license
 * Copyright Google Inc. All Rights Reserved.
 *
 * Use of this source code is governed by an MIT-style license that can be
 * found in the LICENSE file at https://angular.io/license
 */
import { PlatformLocation } from '@angular/common';
import { Injectable } from '@angular/core';
import { ObservableWrapper, PromiseWrapper } from '../../facade/async';
import { StringMapWrapper } from '../../facade/collection';
import { BaseException } from '../../facade/exceptions';
import { StringWrapper } from '../../facade/lang';
import { ClientMessageBrokerFactory, FnArg, UiArguments } from '../shared/client_message_broker';
import { MessageBus } from '../shared/message_bus';
import { ROUTER_CHANNEL } from '../shared/messaging_api';
import { LocationType } from '../shared/serialized_types';
import { PRIMITIVE, Serializer } from '../shared/serializer';
import { deserializeGenericEvent } from './event_deserializer';
export class WebWorkerPlatformLocation extends PlatformLocation {
    constructor(brokerFactory, bus, _serializer) {
        super();
        this._serializer = _serializer;
        this._popStateListeners = [];
        this._hashChangeListeners = [];
        this._location = null;
        this._broker = brokerFactory.createMessageBroker(ROUTER_CHANNEL);
        this._channelSource = bus.from(ROUTER_CHANNEL);
        ObservableWrapper.subscribe(this._channelSource, (msg) => {
            var listeners = null;
            if (StringMapWrapper.contains(msg, 'event')) {
                let type = msg['event']['type'];
                if (StringWrapper.equals(type, 'popstate')) {
                    listeners = this._popStateListeners;
                }
                else if (StringWrapper.equals(type, 'hashchange')) {
                    listeners = this._hashChangeListeners;
                }
                if (listeners !== null) {
                    let e = deserializeGenericEvent(msg['event']);
                    // There was a popState or hashChange event, so the location object thas been updated
                    this._location = this._serializer.deserialize(msg['location'], LocationType);
                    listeners.forEach((fn) => fn(e));
                }
            }
        });
    }
    /** @internal **/
    init() {
        var args = new UiArguments('getLocation');
        var locationPromise = this._broker.runOnService(args, LocationType);
        return PromiseWrapper.then(locationPromise, (val) => {
            this._location = val;
            return true;
        }, (err) => { throw new BaseException(err); });
    }
    getBaseHrefFromDOM() {
        throw new BaseException('Attempt to get base href from DOM from WebWorker. You must either provide a value for the APP_BASE_HREF token through DI or use the hash location strategy.');
    }
    onPopState(fn) { this._popStateListeners.push(fn); }
    onHashChange(fn) { this._hashChangeListeners.push(fn); }
    get pathname() {
        if (this._location === null) {
            return null;
        }
        return this._location.pathname;
    }
    get search() {
        if (this._location === null) {
            return null;
        }
        return this._location.search;
    }
    get hash() {
        if (this._location === null) {
            return null;
        }
        return this._location.hash;
    }
    set pathname(newPath) {
        if (this._location === null) {
            throw new BaseException('Attempt to set pathname before value is obtained from UI');
        }
        this._location.pathname = newPath;
        var fnArgs = [new FnArg(newPath, PRIMITIVE)];
        var args = new UiArguments('setPathname', fnArgs);
        this._broker.runOnService(args, null);
    }
    pushState(state, title, url) {
        var fnArgs = [new FnArg(state, PRIMITIVE), new FnArg(title, PRIMITIVE), new FnArg(url, PRIMITIVE)];
        var args = new UiArguments('pushState', fnArgs);
        this._broker.runOnService(args, null);
    }
    replaceState(state, title, url) {
        var fnArgs = [new FnArg(state, PRIMITIVE), new FnArg(title, PRIMITIVE), new FnArg(url, PRIMITIVE)];
        var args = new UiArguments('replaceState', fnArgs);
        this._broker.runOnService(args, null);
    }
    forward() {
        var args = new UiArguments('forward');
        this._broker.runOnService(args, null);
    }
    back() {
        var args = new UiArguments('back');
        this._broker.runOnService(args, null);
    }
}
/** @nocollapse */
WebWorkerPlatformLocation.decorators = [
    { type: Injectable },
];
/** @nocollapse */
WebWorkerPlatformLocation.ctorParameters = [
    { type: ClientMessageBrokerFactory, },
    { type: MessageBus, },
    { type: Serializer, },
];
//# sourceMappingURL=platform_location.js.map