/**
 * @license
 * Copyright Google Inc. All Rights Reserved.
 *
 * Use of this source code is governed by an MIT-style license that can be
 * found in the LICENSE file at https://angular.io/license
 */
"use strict";
var lang_1 = require('../facade/lang');
var _DOM = null;
function getDOM() {
    return _DOM;
}
exports.getDOM = getDOM;
function setDOM(adapter) {
    _DOM = adapter;
}
exports.setDOM = setDOM;
function setRootDomAdapter(adapter) {
    if (lang_1.isBlank(_DOM)) {
        _DOM = adapter;
    }
}
exports.setRootDomAdapter = setRootDomAdapter;
/* tslint:disable:requireParameterType */
/**
 * Provides DOM operations in an environment-agnostic way.
 */
var DomAdapter = (function () {
    function DomAdapter() {
        this.xhrType = null;
    }
    /** @deprecated */
    DomAdapter.prototype.getXHR = function () { return this.xhrType; };
    Object.defineProperty(DomAdapter.prototype, "attrToPropMap", {
        /**
         * Maps attribute names to their corresponding property names for cases
         * where attribute name doesn't match property name.
         */
        get: function () { return this._attrToPropMap; },
        set: function (value) { this._attrToPropMap = value; },
        enumerable: true,
        configurable: true
    });
    ;
    ;
    return DomAdapter;
}());
exports.DomAdapter = DomAdapter;
//# sourceMappingURL=dom_adapter.js.map