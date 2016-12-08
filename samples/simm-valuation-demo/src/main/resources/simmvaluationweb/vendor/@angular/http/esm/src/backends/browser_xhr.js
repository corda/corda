/**
 * @license
 * Copyright Google Inc. All Rights Reserved.
 *
 * Use of this source code is governed by an MIT-style license that can be
 * found in the LICENSE file at https://angular.io/license
 */
import { Injectable } from '@angular/core';
export class BrowserXhr {
    constructor() {
    }
    build() { return (new XMLHttpRequest()); }
}
/** @nocollapse */
BrowserXhr.decorators = [
    { type: Injectable },
];
/** @nocollapse */
BrowserXhr.ctorParameters = [];
//# sourceMappingURL=browser_xhr.js.map