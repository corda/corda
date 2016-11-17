/**
 * @license
 * Copyright Google Inc. All Rights Reserved.
 *
 * Use of this source code is governed by an MIT-style license that can be
 * found in the LICENSE file at https://angular.io/license
 */
import { PlatformLocation } from '@angular/common';
import { Injectable } from '@angular/core';
import { getDOM } from '../../dom/dom_adapter';
import { supportsState } from './history';
export class BrowserPlatformLocation extends PlatformLocation {
    constructor() {
        super();
        this._init();
    }
    // This is moved to its own method so that `MockPlatformLocationStrategy` can overwrite it
    /** @internal */
    _init() {
        this._location = getDOM().getLocation();
        this._history = getDOM().getHistory();
    }
    /** @internal */
    get location() { return this._location; }
    getBaseHrefFromDOM() { return getDOM().getBaseHref(); }
    onPopState(fn) {
        getDOM().getGlobalEventTarget('window').addEventListener('popstate', fn, false);
    }
    onHashChange(fn) {
        getDOM().getGlobalEventTarget('window').addEventListener('hashchange', fn, false);
    }
    get pathname() { return this._location.pathname; }
    get search() { return this._location.search; }
    get hash() { return this._location.hash; }
    set pathname(newPath) { this._location.pathname = newPath; }
    pushState(state, title, url) {
        if (supportsState()) {
            this._history.pushState(state, title, url);
        }
        else {
            this._location.hash = url;
        }
    }
    replaceState(state, title, url) {
        if (supportsState()) {
            this._history.replaceState(state, title, url);
        }
        else {
            this._location.hash = url;
        }
    }
    forward() { this._history.forward(); }
    back() { this._history.back(); }
}
/** @nocollapse */
BrowserPlatformLocation.decorators = [
    { type: Injectable },
];
/** @nocollapse */
BrowserPlatformLocation.ctorParameters = [];
//# sourceMappingURL=browser_platform_location.js.map