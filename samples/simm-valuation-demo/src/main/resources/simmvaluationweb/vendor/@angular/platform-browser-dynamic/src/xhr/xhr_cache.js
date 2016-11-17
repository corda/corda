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
var compiler_1 = require('@angular/compiler');
var exceptions_1 = require('../facade/exceptions');
var lang_1 = require('../facade/lang');
var promise_1 = require('../facade/promise');
/**
 * An implementation of XHR that uses a template cache to avoid doing an actual
 * XHR.
 *
 * The template cache needs to be built and loaded into window.$templateCache
 * via a separate mechanism.
 */
var CachedXHR = (function (_super) {
    __extends(CachedXHR, _super);
    function CachedXHR() {
        _super.call(this);
        this._cache = lang_1.global.$templateCache;
        if (this._cache == null) {
            throw new exceptions_1.BaseException('CachedXHR: Template cache was not found in $templateCache.');
        }
    }
    CachedXHR.prototype.get = function (url) {
        if (this._cache.hasOwnProperty(url)) {
            return promise_1.PromiseWrapper.resolve(this._cache[url]);
        }
        else {
            return promise_1.PromiseWrapper.reject('CachedXHR: Did not find cached template for ' + url, null);
        }
    };
    return CachedXHR;
}(compiler_1.XHR));
exports.CachedXHR = CachedXHR;
//# sourceMappingURL=xhr_cache.js.map