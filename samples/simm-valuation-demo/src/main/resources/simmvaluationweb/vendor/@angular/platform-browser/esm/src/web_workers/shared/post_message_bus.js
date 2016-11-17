/**
 * @license
 * Copyright Google Inc. All Rights Reserved.
 *
 * Use of this source code is governed by an MIT-style license that can be
 * found in the LICENSE file at https://angular.io/license
 */
import { Injectable } from '@angular/core';
import { EventEmitter, ObservableWrapper } from '../../facade/async';
import { StringMapWrapper } from '../../facade/collection';
import { BaseException } from '../../facade/exceptions';
export class PostMessageBusSink {
    constructor(_postMessageTarget) {
        this._postMessageTarget = _postMessageTarget;
        this._channels = StringMapWrapper.create();
        this._messageBuffer = [];
    }
    attachToZone(zone) {
        this._zone = zone;
        this._zone.runOutsideAngular(() => {
            ObservableWrapper.subscribe(this._zone.onStable, (_) => { this._handleOnEventDone(); });
        });
    }
    initChannel(channel, runInZone = true) {
        if (StringMapWrapper.contains(this._channels, channel)) {
            throw new BaseException(`${channel} has already been initialized`);
        }
        var emitter = new EventEmitter(false);
        var channelInfo = new _Channel(emitter, runInZone);
        this._channels[channel] = channelInfo;
        emitter.subscribe((data) => {
            var message = { channel: channel, message: data };
            if (runInZone) {
                this._messageBuffer.push(message);
            }
            else {
                this._sendMessages([message]);
            }
        });
    }
    to(channel) {
        if (StringMapWrapper.contains(this._channels, channel)) {
            return this._channels[channel].emitter;
        }
        else {
            throw new BaseException(`${channel} is not set up. Did you forget to call initChannel?`);
        }
    }
    _handleOnEventDone() {
        if (this._messageBuffer.length > 0) {
            this._sendMessages(this._messageBuffer);
            this._messageBuffer = [];
        }
    }
    _sendMessages(messages) { this._postMessageTarget.postMessage(messages); }
}
export class PostMessageBusSource {
    constructor(eventTarget) {
        this._channels = StringMapWrapper.create();
        if (eventTarget) {
            eventTarget.addEventListener('message', (ev) => this._handleMessages(ev));
        }
        else {
            // if no eventTarget is given we assume we're in a WebWorker and listen on the global scope
            const workerScope = self;
            workerScope.addEventListener('message', (ev) => this._handleMessages(ev));
        }
    }
    attachToZone(zone) { this._zone = zone; }
    initChannel(channel, runInZone = true) {
        if (StringMapWrapper.contains(this._channels, channel)) {
            throw new BaseException(`${channel} has already been initialized`);
        }
        var emitter = new EventEmitter(false);
        var channelInfo = new _Channel(emitter, runInZone);
        this._channels[channel] = channelInfo;
    }
    from(channel) {
        if (StringMapWrapper.contains(this._channels, channel)) {
            return this._channels[channel].emitter;
        }
        else {
            throw new BaseException(`${channel} is not set up. Did you forget to call initChannel?`);
        }
    }
    _handleMessages(ev) {
        var messages = ev.data;
        for (var i = 0; i < messages.length; i++) {
            this._handleMessage(messages[i]);
        }
    }
    _handleMessage(data) {
        var channel = data.channel;
        if (StringMapWrapper.contains(this._channels, channel)) {
            var channelInfo = this._channels[channel];
            if (channelInfo.runInZone) {
                this._zone.run(() => { channelInfo.emitter.emit(data.message); });
            }
            else {
                channelInfo.emitter.emit(data.message);
            }
        }
    }
}
export class PostMessageBus {
    constructor(sink, source) {
        this.sink = sink;
        this.source = source;
    }
    attachToZone(zone) {
        this.source.attachToZone(zone);
        this.sink.attachToZone(zone);
    }
    initChannel(channel, runInZone = true) {
        this.source.initChannel(channel, runInZone);
        this.sink.initChannel(channel, runInZone);
    }
    from(channel) { return this.source.from(channel); }
    to(channel) { return this.sink.to(channel); }
}
/** @nocollapse */
PostMessageBus.decorators = [
    { type: Injectable },
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
class _Channel {
    constructor(emitter, runInZone) {
        this.emitter = emitter;
        this.runInZone = runInZone;
    }
}
//# sourceMappingURL=post_message_bus.js.map