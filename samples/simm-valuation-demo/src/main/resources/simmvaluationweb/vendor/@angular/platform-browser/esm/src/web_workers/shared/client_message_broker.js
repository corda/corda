/**
 * @license
 * Copyright Google Inc. All Rights Reserved.
 *
 * Use of this source code is governed by an MIT-style license that can be
 * found in the LICENSE file at https://angular.io/license
 */
import { Injectable } from '@angular/core';
import { ObservableWrapper, PromiseWrapper } from '../../facade/async';
import { StringMapWrapper } from '../../facade/collection';
import { DateWrapper, StringWrapper, isPresent, print, stringify } from '../../facade/lang';
import { MessageBus } from './message_bus';
import { Serializer } from './serializer';
/**
 * @experimental WebWorker support in Angular is experimental.
 */
export class ClientMessageBrokerFactory {
}
export class ClientMessageBrokerFactory_ extends ClientMessageBrokerFactory {
    constructor(_messageBus, _serializer) {
        super();
        this._messageBus = _messageBus;
        this._serializer = _serializer;
    }
    /**
     * Initializes the given channel and attaches a new {@link ClientMessageBroker} to it.
     */
    createMessageBroker(channel, runInZone = true) {
        this._messageBus.initChannel(channel, runInZone);
        return new ClientMessageBroker_(this._messageBus, this._serializer, channel);
    }
}
/** @nocollapse */
ClientMessageBrokerFactory_.decorators = [
    { type: Injectable },
];
/** @nocollapse */
ClientMessageBrokerFactory_.ctorParameters = [
    { type: MessageBus, },
    { type: Serializer, },
];
/**
 * @experimental WebWorker support in Angular is experimental.
 */
export class ClientMessageBroker {
}
export class ClientMessageBroker_ extends ClientMessageBroker {
    constructor(messageBus, _serializer, channel /** TODO #9100 */) {
        super();
        this.channel = channel;
        this._pending = new Map();
        this._sink = messageBus.to(channel);
        this._serializer = _serializer;
        var source = messageBus.from(channel);
        ObservableWrapper.subscribe(source, (message) => this._handleMessage(message));
    }
    _generateMessageId(name) {
        var time = stringify(DateWrapper.toMillis(DateWrapper.now()));
        var iteration = 0;
        var id = name + time + stringify(iteration);
        while (isPresent(this._pending[id])) {
            id = `${name}${time}${iteration}`;
            iteration++;
        }
        return id;
    }
    runOnService(args, returnType) {
        var fnArgs = [];
        if (isPresent(args.args)) {
            args.args.forEach(argument => {
                if (argument.type != null) {
                    fnArgs.push(this._serializer.serialize(argument.value, argument.type));
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
            PromiseWrapper.catchError(completer.promise, (err, stack) => {
                print(err);
                completer.reject(err, stack);
            });
            promise = PromiseWrapper.then(completer.promise, (value) => {
                if (this._serializer == null) {
                    return value;
                }
                else {
                    return this._serializer.deserialize(value, returnType);
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
    }
    _handleMessage(message) {
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
    }
}
class MessageData {
    constructor(data) {
        this.type = StringMapWrapper.get(data, 'type');
        this.id = this._getValueIfPresent(data, 'id');
        this.value = this._getValueIfPresent(data, 'value');
    }
    /**
     * Returns the value from the StringMap if present. Otherwise returns null
     * @internal
     */
    _getValueIfPresent(data, key) {
        if (StringMapWrapper.contains(data, key)) {
            return StringMapWrapper.get(data, key);
        }
        else {
            return null;
        }
    }
}
/**
 * @experimental WebWorker support in Angular is experimental.
 */
export class FnArg {
    constructor(value /** TODO #9100 */, type) {
        this.value = value;
        this.type = type;
    }
}
/**
 * @experimental WebWorker support in Angular is experimental.
 */
export class UiArguments {
    constructor(method, args) {
        this.method = method;
        this.args = args;
    }
}
//# sourceMappingURL=client_message_broker.js.map