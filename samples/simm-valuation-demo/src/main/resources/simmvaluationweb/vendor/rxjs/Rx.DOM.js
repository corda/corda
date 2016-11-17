"use strict";
function __export(m) {
    for (var p in m) if (!exports.hasOwnProperty(p)) exports[p] = m[p];
}
__export(require('./Rx'));
require('./add/observable/dom/ajax');
require('./add/observable/dom/webSocket');
var AjaxObservable_1 = require('./observable/dom/AjaxObservable');
exports.AjaxResponse = AjaxObservable_1.AjaxResponse;
exports.AjaxError = AjaxObservable_1.AjaxError;
exports.AjaxTimeoutError = AjaxObservable_1.AjaxTimeoutError;
// Rebuild `Scheduler` for Rx.DOM
var asap_1 = require('./scheduler/asap');
var async_1 = require('./scheduler/async');
var queue_1 = require('./scheduler/queue');
var animationFrame_1 = require('./scheduler/animationFrame');
/* tslint:enable:no-unused-variable */
exports.Scheduler = {
    asap: asap_1.asap,
    async: async_1.async,
    queue: queue_1.queue,
    animationFrame: animationFrame_1.animationFrame
};
//# sourceMappingURL=Rx.DOM.js.map