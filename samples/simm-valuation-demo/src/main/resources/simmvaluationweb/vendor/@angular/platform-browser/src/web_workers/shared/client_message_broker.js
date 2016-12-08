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
var core_1 = require('@angular/core');
var async_1 = require('../../facade/async');
var collection_1 = require('../../facade/collection');
var lang_1 = require('../../facade/lang');
var message_bus_1 = require('./message_bus');
var serializer_1 = require('./serializer');
/**
 * @experimental WebWorker support in Angular is experimental.
 */
var ClientMessageBrokerFactory = (function () {
    function ClientMessageBrokerFactory() {
    }
    return ClientMessageBrokerFactory;
}());
exports.ClientMessageBrokerFactory = ClientMessageBrokerFactory;
var ClientMessageBrokerFactory_ = (function (_super) {
    __extends(ClientMessageBrokerFactory_, _super);
    function ClientMessageBrokerFactory_(_messageBus, _serializer) {
        _super.call(this);
        this._messageBus = _messageBus;
        this._serializer = _serializer;
    }
    /**
     * Initializes the given channel and attaches a new {@link ClientMessageBroker} to it.
     */
    ClientMessageBrokerFactory_.prototype.createMessageBroker = function (channel, runInZone) {
        if (runInZone === void 0) { runInZone = true; }
        this._messageBus.initChannel(channel, runInZone);
        return new ClientMessageBroker_(this._messageBus, this._serializer, channel);
    };
    /** @nocollapse */
    ClientMessageBrokerFactory_.decorators = [
        { type: core_1.Injectable },
    ];
    /** @nocollapse */
    ClientMessageBrokerFactory_.ctorParameters = [
        { type: message_bus_1.MessageBus, },
        { type: serializer_1.Serializer, },
    ];
    return ClientMessageBrokerFactory_;
}(ClientMessageBrokerFactory));
exports.ClientMessageBrokerFactory_ = ClientMessageBrokerFactory_;
/**
 * @experimental WebWorker support in Angular is experimental.
 */
var ClientMessageBroker = (function () {
    function ClientMessageBroker() {
    }
    return ClientMessageBroker;
}());
exports.ClientMessageBroker = ClientMessageBroker;
var ClientMessageBroker_ = (function (_super) {
    __extends(ClientMessageBroker_, _super);
    function ClientMessageBroker_(messageBus, _serializer, channel /** TODO #9100 */) {
        var _this = this;
        _super.call(this);
        this.channel = channel;
        this._pending = new Map();
        this._sink = messageBus.to(channel);
        this._serializer = _serializer;
        var source = messageBus.from(channel);
        async_1.ObservableWrapper.subscribe(source, function (message) { return _this._handleMessage(message); });
    }
    ClientMessageBroker_.prototype._generateMessageId = function (name) {
        var time = lang_1.stringify(lang_1.DateWrapper.toMillis(lang_1.DateWrapper.now()));
        var iteration = 0;
        var id = name + time + lang_1.stringify(iteration);
        while (lang_1.isPresent(this._pending[id])) {
            id = "" + name + time + iteration;
            iteration++;
        }
        return id;
    };
    ClientMessageBroker_.prototype.runOnService = function (args, returnType) {
        var _this = this;
        var fnArgs = [];
        if (lang_1.isPresent(args.args)) {
            args.args.forEach(function (argument) {
                if (argument.type != null) {
                    fnArgs.push(_this._serializer.serialize(argument.value, argument.type));
                }
                else {
                    fnArgs.push(argument.value);
                }
            });
        }
        var promise;
        var id = null;
        if (returnType != null) {
            var completer = async_1.PromiseWrapper.completer();
            id = this._generateMessageId(args.method);
            this._pending.set(id, completer);
            async_1.PromiseWrapper.catchError(completer.promise, function (err, stack) {
                lang_1.print(err);
                completer.reject(err, stack);
            });
            promise = async_1.PromiseWrapper.then(completer.promise, function (value) {
                if (_this._serializer == null) {
                    return value;
                }
                else {
                    return _this._serializer.deserialize(value, returnType);
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
        async_1.ObservableWrapper.callEmit(this._sink, message);
        return promise;
    };
    ClientMessageBroker_.prototype._handleMessage = function (message) {
        var data = new MessageData(message);
        // TODO(jteplitz602): replace these strings with messaging constants #3685
        if (lang_1.StringWrapper.equals(data.type, 'result') || lang_1.StringWrapper.equals(data.type, 'error')) {
            var id = data.id;
            if (this._pending.has(id)) {
                if (lang_1.StringWrapper.equals(data.type, 'result')) {
                    this._pending.get(id).resolve(data.value);
                }
                else {
                    this._pending.get(id).reject(data.value, null);
                }
                this._pending.delete(id);
            }
        }
    };
    return ClientMessageBroker_;
}(ClientMessageBroker));
exports.ClientMessageBroker_ = ClientMessageBroker_;
var MessageData = (function () {
    function MessageData(data) {
        this.type = collection_1.StringMapWrapper.get(data, 'type');
        this.id = this._getValueIfPresent(data, 'id');
        this.value = this._getValueIfPresent(data, 'value');
    }
    /**
     * Returns the value from the StringMap if present. Otherwise returns null
     * @internal
     */
    MessageData.prototype._getValueIfPresent = function (data, key) {
        if (collection_1.StringMapWrapper.contains(data, key)) {
            return collection_1.StringMapWrapper.get(data, key);
        }
        else {
            return null;
        }
    };
    return MessageData;
}());
/**
 * @experimental WebWorker support in Angular is experimental.
 */
var FnArg = (function () {
    function FnArg(value /** TODO #9100 */, type) {
        this.value = value;
        this.type = type;
    }
    return FnArg;
}());
exports.FnArg = FnArg;
/**
 * @experimental WebWorker support in Angular is experimental.
 */
var UiArguments = (function () {
    function UiArguments(method, args) {
        this.method = method;
        this.args = args;
    }
    return UiArguments;
}());
exports.UiArguments = UiArguments;
//# sourceMappingURL=client_message_broker.js.map