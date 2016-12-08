/**
 * @license
 * Copyright Google Inc. All Rights Reserved.
 *
 * Use of this source code is governed by an MIT-style license that can be
 * found in the LICENSE file at https://angular.io/license
 */
"use strict";
var core_1 = require('@angular/core');
var BrowserXhr = (function () {
    function BrowserXhr() {
    }
    BrowserXhr.prototype.build = function () { return (new XMLHttpRequest()); };
    /** @nocollapse */
    BrowserXhr.decorators = [
        { type: core_1.Injectable },
    ];
    /** @nocollapse */
    BrowserXhr.ctorParameters = [];
    return BrowserXhr;
}());
exports.BrowserXhr = BrowserXhr;
//# sourceMappingURL=browser_xhr.js.map