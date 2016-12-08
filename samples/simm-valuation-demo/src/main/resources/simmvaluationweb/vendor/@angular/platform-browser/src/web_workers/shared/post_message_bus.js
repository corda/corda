/**
 * @license
 * Copyright Google Inc. All Rights Reserved.
 *
 * Use of this source code is governed by an MIT-style license that can be
 * found in the LICENSE file at https://angular.io/license
 */
"use strict";
var core_1 = require('@angular/core');
var async_1 = require('../../facade/async');
var collection_1 = require('../../facade/collection');
var exceptions_1 = require('../../facade/exceptions');
var PostMessageBusSink = (function () {
    function PostMessageBusSink(_postMessageTarget) {
        this._postMessageTarget = _postMessageTarget;
        this._channels = collection_1.StringMapWrapper.create();
        this._messageBuffer = [];
    }
    PostMessageBusSink.prototype.attachToZone = function (zone) {
        var _this = this;
        this._zone = zone;
        this._zone.runOutsideAngular(function () {
            async_1.ObservableWrapper.subscribe(_this._zone.onStable, function (_) { _this._handleOnEventDone(); });
        });
    };
    PostMessageBusSink.prototype.initChannel = function (channel, runInZone) {
        var _this = this;
        if (runInZone === void 0) { runInZone = true; }
        if (collection_1.StringMapWrapper.contains(this._channels, channel)) {
            throw new exceptions_1.BaseException(channel + " has already been initialized");
        }
        var emitter = new async_1.EventEmitter(false);
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
        if (collection_1.StringMapWrapper.contains(this._channels, channel)) {
            return this._channels[channel].emitter;
        }
        else {
            throw new exceptions_1.BaseException(channel + " is not set up. Did you forget to call initChannel?");
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
exports.PostMessageBusSink = PostMessageBusSink;
var PostMessageBusSource = (function () {
    function PostMessageBusSource(eventTarget) {
        var _this = this;
        this._channels = collection_1.StringMapWrapper.create();
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
        if (collection_1.StringMapWrapper.contains(this._channels, channel)) {
            throw new exceptions_1.BaseException(channel + " has already been initialized");
        }
        var emitter = new async_1.EventEmitter(false);
        var channelInfo = new _Channel(emitter, runInZone);
        this._channels[channel] = channelInfo;
    };
    PostMessageBusSource.prototype.from = function (channel) {
        if (collection_1.StringMapWrapper.contains(this._channels, channel)) {
            return this._channels[channel].emitter;
        }
        else {
            throw new exceptions_1.BaseException(channel + " is not set up. Did you forget to call initChannel?");
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
        if (collection_1.StringMapWrapper.contains(this._channels, channel)) {
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
exports.PostMessageBusSource = PostMessageBusSource;
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
    /** @nocollapse */
    PostMessageBus.decorators = [
        { type: core_1.Injectable },
    ];
    /** @nocollapse */
    PostMessageBus.ctorParameters = [
        { type: PostMessageBusSink, },
        { type: PostMessageBusSource, },
    ];
    return PostMessageBus;
}());
exports.PostMessageBus = PostMessageBus;
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
//# sourceMappingURL=post_message_bus.js.map