/**
 * @license
 * Copyright Google Inc. All Rights Reserved.
 *
 * Use of this source code is governed by an MIT-style license that can be
 * found in the LICENSE file at https://angular.io/license
 */
import { Injectable } from '@angular/core';
import { ObservableWrapper, PromiseWrapper } from '../../facade/async';
import { ListWrapper, Map } from '../../facade/collection';
import { FunctionWrapper, isPresent } from '../../facade/lang';
import { MessageBus } from '../shared/message_bus';
import { Serializer } from '../shared/serializer';
/**
 * @experimental WebWorker support in Angular is currently experimental.
 */
export class ServiceMessageBrokerFactory {
}
export class ServiceMessageBrokerFactory_ extends ServiceMessageBrokerFactory {
    constructor(_messageBus, _serializer) {
        super();
        this._messageBus = _messageBus;
        this._serializer = _serializer;
    }
    createMessageBroker(channel, runInZone = true) {
        this._messageBus.initChannel(channel, runInZone);
        return new ServiceMessageBroker_(this._messageBus, this._serializer, channel);
    }
}
/** @nocollapse */
ServiceMessageBrokerFactory_.decorators = [
    { type: Injectable },
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
export class ServiceMessageBroker {
}
export class ServiceMessageBroker_ extends ServiceMessageBroker {
    constructor(messageBus, _serializer, channel /** TODO #9100 */) {
        super();
        this._serializer = _serializer;
        this.channel = channel;
        this._methods = new Map();
        this._sink = messageBus.to(channel);
        var source = messageBus.from(channel);
        ObservableWrapper.subscribe(source, (message) => this._handleMessage(message));
    }
    registerMethod(methodName, signature, method, returnType) {
        this._methods.set(methodName, (message) => {
            var serializedArgs = message.args;
            let numArgs = signature === null ? 0 : signature.length;
            var deserializedArgs = ListWrapper.createFixedSize(numArgs);
            for (var i = 0; i < numArgs; i++) {
                var serializedArg = serializedArgs[i];
                deserializedArgs[i] = this._serializer.deserialize(serializedArg, signature[i]);
            }
            var promise = FunctionWrapper.apply(method, deserializedArgs);
            if (isPresent(returnType) && isPresent(promise)) {
                this._wrapWebWorkerPromise(message.id, promise, returnType);
            }
        });
    }
    _handleMessage(map) {
        var message = new ReceivedMessage(map);
        if (this._methods.has(message.method)) {
            this._methods.get(message.method)(message);
        }
    }
    _wrapWebWorkerPromise(id, promise, type) {
        PromiseWrapper.then(promise, (result) => {
            ObservableWrapper.callEmit(this._sink, { 'type': 'result', 'value': this._serializer.serialize(result, type), 'id': id });
        });
    }
}
/**
 * @experimental WebWorker support in Angular is currently experimental.
 */
export class ReceivedMessage {
    constructor(data) {
        this.method = data['method'];
        this.args = data['args'];
        this.id = data['id'];
        this.type = data['type'];
    }
}
//# sourceMappingURL=service_message_broker.js.map