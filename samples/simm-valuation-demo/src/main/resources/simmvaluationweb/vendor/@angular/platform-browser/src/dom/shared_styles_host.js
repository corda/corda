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
var collection_1 = require('../facade/collection');
var dom_adapter_1 = require('./dom_adapter');
var dom_tokens_1 = require('./dom_tokens');
var SharedStylesHost = (function () {
    function SharedStylesHost() {
        /** @internal */
        this._styles = [];
        /** @internal */
        this._stylesSet = new Set();
    }
    SharedStylesHost.prototype.addStyles = function (styles) {
        var _this = this;
        var additions = [];
        styles.forEach(function (style) {
            if (!collection_1.SetWrapper.has(_this._stylesSet, style)) {
                _this._stylesSet.add(style);
                _this._styles.push(style);
                additions.push(style);
            }
        });
        this.onStylesAdded(additions);
    };
    SharedStylesHost.prototype.onStylesAdded = function (additions) { };
    SharedStylesHost.prototype.getAllStyles = function () { return this._styles; };
    /** @nocollapse */
    SharedStylesHost.decorators = [
        { type: core_1.Injectable },
    ];
    /** @nocollapse */
    SharedStylesHost.ctorParameters = [];
    return SharedStylesHost;
}());
exports.SharedStylesHost = SharedStylesHost;
var DomSharedStylesHost = (function (_super) {
    __extends(DomSharedStylesHost, _super);
    function DomSharedStylesHost(doc) {
        _super.call(this);
        this._hostNodes = new Set();
        this._hostNodes.add(doc.head);
    }
    /** @internal */
    DomSharedStylesHost.prototype._addStylesToHost = function (styles, host) {
        for (var i = 0; i < styles.length; i++) {
            var style = styles[i];
            dom_adapter_1.getDOM().appendChild(host, dom_adapter_1.getDOM().createStyleElement(style));
        }
    };
    DomSharedStylesHost.prototype.addHost = function (hostNode) {
        this._addStylesToHost(this._styles, hostNode);
        this._hostNodes.add(hostNode);
    };
    DomSharedStylesHost.prototype.removeHost = function (hostNode) { collection_1.SetWrapper.delete(this._hostNodes, hostNode); };
    DomSharedStylesHost.prototype.onStylesAdded = function (additions) {
        var _this = this;
        this._hostNodes.forEach(function (hostNode) { _this._addStylesToHost(additions, hostNode); });
    };
    /** @nocollapse */
    DomSharedStylesHost.decorators = [
        { type: core_1.Injectable },
    ];
    /** @nocollapse */
    DomSharedStylesHost.ctorParameters = [
        { type: undefined, decorators: [{ type: core_1.Inject, args: [dom_tokens_1.DOCUMENT,] },] },
    ];
    return DomSharedStylesHost;
}(SharedStylesHost));
exports.DomSharedStylesHost = DomSharedStylesHost;
//# sourceMappingURL=shared_styles_host.js.map