/**
 * @license
 * Copyright Google Inc. All Rights Reserved.
 *
 * Use of this source code is governed by an MIT-style license that can be
 * found in the LICENSE file at https://angular.io/license
 */
"use strict";
var core_1 = require('@angular/core');
var lang_1 = require('../facade/lang');
var _nextRequestId = 0;
exports.JSONP_HOME = '__ng_jsonp__';
var _jsonpConnections = null;
function _getJsonpConnections() {
    if (_jsonpConnections === null) {
        _jsonpConnections = lang_1.global[exports.JSONP_HOME] = {};
    }
    return _jsonpConnections;
}
var BrowserJsonp = (function () {
    function BrowserJsonp() {
    }
    // Construct a <script> element with the specified URL
    BrowserJsonp.prototype.build = function (url) {
        var node = document.createElement('script');
        node.src = url;
        return node;
    };
    BrowserJsonp.prototype.nextRequestID = function () { return "__req" + _nextRequestId++; };
    BrowserJsonp.prototype.requestCallback = function (id) { return exports.JSONP_HOME + "." + id + ".finished"; };
    BrowserJsonp.prototype.exposeConnection = function (id, connection) {
        var connections = _getJsonpConnections();
        connections[id] = connection;
    };
    BrowserJsonp.prototype.removeConnection = function (id) {
        var connections = _getJsonpConnections();
        connections[id] = null;
    };
    // Attach the <script> element to the DOM
    BrowserJsonp.prototype.send = function (node) { document.body.appendChild((node)); };
    // Remove <script> element from the DOM
    BrowserJsonp.prototype.cleanup = function (node) {
        if (node.parentNode) {
            node.parentNode.removeChild((node));
        }
    };
    /** @nocollapse */
    BrowserJsonp.decorators = [
        { type: core_1.Injectable },
    ];
    return BrowserJsonp;
}());
exports.BrowserJsonp = BrowserJsonp;
//# sourceMappingURL=browser_jsonp.js.map