"use strict";
var __extends = (this && this.__extends) || function (d, b) {
    for (var p in b) if (b.hasOwnProperty(p)) d[p] = b[p];
    function __() { this.constructor = d; }
    d.prototype = b === null ? Object.create(b) : (__.prototype = b.prototype, new __());
};
var Subscriber_1 = require('../Subscriber');
var async_1 = require('../scheduler/async');
/**
 * Buffers the source Observable values for a specific time period.
 *
 * <span class="informal">Collects values from the past as an array, and emits
 * those arrays periodically in time.</span>
 *
 * <img src="./img/bufferTime.png" width="100%">
 *
 * Buffers values from the source for a specific time duration `bufferTimeSpan`.
 * Unless the optional argument `bufferCreationInterval` is given, it emits and
 * resets the buffer every `bufferTimeSpan` milliseconds. If
 * `bufferCreationInterval` is given, this operator opens the buffer every
 * `bufferCreationInterval` milliseconds and closes (emits and resets) the
 * buffer every `bufferTimeSpan` milliseconds.
 *
 * @example <caption>Every second, emit an array of the recent click events</caption>
 * var clicks = Rx.Observable.fromEvent(document, 'click');
 * var buffered = clicks.bufferTime(1000);
 * buffered.subscribe(x => console.log(x));
 *
 * @example <caption>Every 5 seconds, emit the click events from the next 2 seconds</caption>
 * var clicks = Rx.Observable.fromEvent(document, 'click');
 * var buffered = clicks.bufferTime(2000, 5000);
 * buffered.subscribe(x => console.log(x));
 *
 * @see {@link buffer}
 * @see {@link bufferCount}
 * @see {@link bufferToggle}
 * @see {@link bufferWhen}
 * @see {@link windowTime}
 *
 * @param {number} bufferTimeSpan The amount of time to fill each buffer array.
 * @param {number} [bufferCreationInterval] The interval at which to start new
 * buffers.
 * @param {Scheduler} [scheduler=async] The scheduler on which to schedule the
 * intervals that determine buffer boundaries.
 * @return {Observable<T[]>} An observable of arrays of buffered values.
 * @method bufferTime
 * @owner Observable
 */
function bufferTime(bufferTimeSpan, bufferCreationInterval, scheduler) {
    if (bufferCreationInterval === void 0) { bufferCreationInterval = null; }
    if (scheduler === void 0) { scheduler = async_1.async; }
    return this.lift(new BufferTimeOperator(bufferTimeSpan, bufferCreationInterval, scheduler));
}
exports.bufferTime = bufferTime;
var BufferTimeOperator = (function () {
    function BufferTimeOperator(bufferTimeSpan, bufferCreationInterval, scheduler) {
        this.bufferTimeSpan = bufferTimeSpan;
        this.bufferCreationInterval = bufferCreationInterval;
        this.scheduler = scheduler;
    }
    BufferTimeOperator.prototype.call = function (subscriber, source) {
        return source._subscribe(new BufferTimeSubscriber(subscriber, this.bufferTimeSpan, this.bufferCreationInterval, this.scheduler));
    };
    return BufferTimeOperator;
}());
/**
 * We need this JSDoc comment for affecting ESDoc.
 * @ignore
 * @extends {Ignored}
 */
var BufferTimeSubscriber = (function (_super) {
    __extends(BufferTimeSubscriber, _super);
    function BufferTimeSubscriber(destination, bufferTimeSpan, bufferCreationInterval, scheduler) {
        _super.call(this, destination);
        this.bufferTimeSpan = bufferTimeSpan;
        this.bufferCreationInterval = bufferCreationInterval;
        this.scheduler = scheduler;
        this.buffers = [];
        var buffer = this.openBuffer();
        if (bufferCreationInterval !== null && bufferCreationInterval >= 0) {
            var closeState = { subscriber: this, buffer: buffer };
            var creationState = { bufferTimeSpan: bufferTimeSpan, bufferCreationInterval: bufferCreationInterval, subscriber: this, scheduler: scheduler };
            this.add(scheduler.schedule(dispatchBufferClose, bufferTimeSpan, closeState));
            this.add(scheduler.schedule(dispatchBufferCreation, bufferCreationInterval, creationState));
        }
        else {
            var timeSpanOnlyState = { subscriber: this, buffer: buffer, bufferTimeSpan: bufferTimeSpan };
            this.add(scheduler.schedule(dispatchBufferTimeSpanOnly, bufferTimeSpan, timeSpanOnlyState));
        }
    }
    BufferTimeSubscriber.prototype._next = function (value) {
        var buffers = this.buffers;
        var len = buffers.length;
        for (var i = 0; i < len; i++) {
            buffers[i].push(value);
        }
    };
    BufferTimeSubscriber.prototype._error = function (err) {
        this.buffers.length = 0;
        _super.prototype._error.call(this, err);
    };
    BufferTimeSubscriber.prototype._complete = function () {
        var _a = this, buffers = _a.buffers, destination = _a.destination;
        while (buffers.length > 0) {
            destination.next(buffers.shift());
        }
        _super.prototype._complete.call(this);
    };
    BufferTimeSubscriber.prototype._unsubscribe = function () {
        this.buffers = null;
    };
    BufferTimeSubscriber.prototype.openBuffer = function () {
        var buffer = [];
        this.buffers.push(buffer);
        return buffer;
    };
    BufferTimeSubscriber.prototype.closeBuffer = function (buffer) {
        this.destination.next(buffer);
        var buffers = this.buffers;
        buffers.splice(buffers.indexOf(buffer), 1);
    };
    return BufferTimeSubscriber;
}(Subscriber_1.Subscriber));
function dispatchBufferTimeSpanOnly(state) {
    var subscriber = state.subscriber;
    var prevBuffer = state.buffer;
    if (prevBuffer) {
        subscriber.closeBuffer(prevBuffer);
    }
    state.buffer = subscriber.openBuffer();
    if (!subscriber.isUnsubscribed) {
        this.schedule(state, state.bufferTimeSpan);
    }
}
function dispatchBufferCreation(state) {
    var bufferCreationInterval = state.bufferCreationInterval, bufferTimeSpan = state.bufferTimeSpan, subscriber = state.subscriber, scheduler = state.scheduler;
    var buffer = subscriber.openBuffer();
    var action = this;
    if (!subscriber.isUnsubscribed) {
        action.add(scheduler.schedule(dispatchBufferClose, bufferTimeSpan, { subscriber: subscriber, buffer: buffer }));
        action.schedule(state, bufferCreationInterval);
    }
}
function dispatchBufferClose(arg) {
    var subscriber = arg.subscriber, buffer = arg.buffer;
    subscriber.closeBuffer(buffer);
}
//# sourceMappingURL=bufferTime.js.map