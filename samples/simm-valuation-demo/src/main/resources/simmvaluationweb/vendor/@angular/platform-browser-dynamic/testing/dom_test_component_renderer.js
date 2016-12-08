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
var testing_1 = require('@angular/compiler/testing');
var core_1 = require('@angular/core');
var platform_browser_1 = require('@angular/platform-browser');
var platform_browser_private_1 = require('../platform_browser_private');
var DOMTestComponentRenderer = (function (_super) {
    __extends(DOMTestComponentRenderer, _super);
    function DOMTestComponentRenderer(_doc /** TODO #9100 */) {
        _super.call(this);
        this._doc = _doc;
    }
    DOMTestComponentRenderer.prototype.insertRootElement = function (rootElId) {
        var rootEl = platform_browser_private_1.getDOM().firstChild(platform_browser_private_1.getDOM().content(platform_browser_private_1.getDOM().createTemplate("<div id=\"" + rootElId + "\"></div>")));
        // TODO(juliemr): can/should this be optional?
        var oldRoots = platform_browser_private_1.getDOM().querySelectorAll(this._doc, '[id^=root]');
        for (var i = 0; i < oldRoots.length; i++) {
            platform_browser_private_1.getDOM().remove(oldRoots[i]);
        }
        platform_browser_private_1.getDOM().appendChild(this._doc.body, rootEl);
    };
    /** @nocollapse */
    DOMTestComponentRenderer.decorators = [
        { type: core_1.Injectable },
    ];
    /** @nocollapse */
    DOMTestComponentRenderer.ctorParameters = [
        { type: undefined, decorators: [{ type: core_1.Inject, args: [platform_browser_1.DOCUMENT,] },] },
    ];
    return DOMTestComponentRenderer;
}(testing_1.TestComponentRenderer));
exports.DOMTestComponentRenderer = DOMTestComponentRenderer;
//# sourceMappingURL=dom_test_component_renderer.js.map