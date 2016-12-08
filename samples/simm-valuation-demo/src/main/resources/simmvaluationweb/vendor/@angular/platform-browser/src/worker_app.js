/**
 * @license
 * Copyright Google Inc. All Rights Reserved.
 *
 * Use of this source code is governed by an MIT-style license that can be
 * found in the LICENSE file at https://angular.io/license
 */
"use strict";
var common_1 = require('@angular/common');
var core_1 = require('@angular/core');
var browser_1 = require('./browser');
var lang_1 = require('./facade/lang');
var api_1 = require('./web_workers/shared/api');
var client_message_broker_1 = require('./web_workers/shared/client_message_broker');
var message_bus_1 = require('./web_workers/shared/message_bus');
var post_message_bus_1 = require('./web_workers/shared/post_message_bus');
var render_store_1 = require('./web_workers/shared/render_store');
var serializer_1 = require('./web_workers/shared/serializer');
var service_message_broker_1 = require('./web_workers/shared/service_message_broker');
var renderer_1 = require('./web_workers/worker/renderer');
var worker_adapter_1 = require('./web_workers/worker/worker_adapter');
var PrintLogger = (function () {
    function PrintLogger() {
        this.log = lang_1.print;
        this.logError = lang_1.print;
        this.logGroup = lang_1.print;
    }
    PrintLogger.prototype.logGroupEnd = function () { };
    return PrintLogger;
}());
var WORKER_APP_PLATFORM_MARKER = new core_1.OpaqueToken('WorkerAppPlatformMarker');
/**
 * @experimental
 */
exports.WORKER_APP_PLATFORM_PROVIDERS = [core_1.PLATFORM_COMMON_PROVIDERS, { provide: WORKER_APP_PLATFORM_MARKER, useValue: true }];
/**
 * @experimental
 */
exports.WORKER_APP_APPLICATION_PROVIDERS = [
    core_1.APPLICATION_COMMON_PROVIDERS, common_1.FORM_PROVIDERS, browser_1.BROWSER_SANITIZATION_PROVIDERS, serializer_1.Serializer,
    { provide: client_message_broker_1.ClientMessageBrokerFactory, useClass: client_message_broker_1.ClientMessageBrokerFactory_ },
    { provide: service_message_broker_1.ServiceMessageBrokerFactory, useClass: service_message_broker_1.ServiceMessageBrokerFactory_ },
    renderer_1.WebWorkerRootRenderer, { provide: core_1.RootRenderer, useExisting: renderer_1.WebWorkerRootRenderer },
    { provide: api_1.ON_WEB_WORKER, useValue: true }, render_store_1.RenderStore,
    { provide: core_1.ExceptionHandler, useFactory: _exceptionHandler, deps: [] },
    { provide: message_bus_1.MessageBus, useFactory: createMessageBus, deps: [core_1.NgZone] },
    { provide: core_1.APP_INITIALIZER, useValue: setupWebWorker, multi: true }
];
/**
 * @experimental
 */
function workerAppPlatform() {
    if (lang_1.isBlank(core_1.getPlatform())) {
        core_1.createPlatform(core_1.ReflectiveInjector.resolveAndCreate(exports.WORKER_APP_PLATFORM_PROVIDERS));
    }
    return core_1.assertPlatform(WORKER_APP_PLATFORM_MARKER);
}
exports.workerAppPlatform = workerAppPlatform;
function _exceptionHandler() {
    return new core_1.ExceptionHandler(new PrintLogger());
}
// TODO(jteplitz602) remove this and compile with lib.webworker.d.ts (#3492)
var _postMessage = {
    postMessage: function (message, transferrables) {
        postMessage(message, transferrables);
    }
};
function createMessageBus(zone) {
    var sink = new post_message_bus_1.PostMessageBusSink(_postMessage);
    var source = new post_message_bus_1.PostMessageBusSource();
    var bus = new post_message_bus_1.PostMessageBus(sink, source);
    bus.attachToZone(zone);
    return bus;
}
function setupWebWorker() {
    worker_adapter_1.WorkerDomAdapter.makeCurrent();
}
//# sourceMappingURL=worker_app.js.map