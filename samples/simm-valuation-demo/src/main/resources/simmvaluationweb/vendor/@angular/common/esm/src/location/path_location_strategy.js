/**
 * @license
 * Copyright Google Inc. All Rights Reserved.
 *
 * Use of this source code is governed by an MIT-style license that can be
 * found in the LICENSE file at https://angular.io/license
 */
import { Inject, Injectable, Optional } from '@angular/core';
import { BaseException } from '../facade/exceptions';
import { isBlank } from '../facade/lang';
import { Location } from './location';
import { APP_BASE_HREF, LocationStrategy } from './location_strategy';
import { PlatformLocation } from './platform_location';
export class PathLocationStrategy extends LocationStrategy {
    constructor(_platformLocation, href) {
        super();
        this._platformLocation = _platformLocation;
        if (isBlank(href)) {
            href = this._platformLocation.getBaseHrefFromDOM();
        }
        if (isBlank(href)) {
            throw new BaseException(`No base href set. Please provide a value for the APP_BASE_HREF token or add a base element to the document.`);
        }
        this._baseHref = href;
    }
    onPopState(fn) {
        this._platformLocation.onPopState(fn);
        this._platformLocation.onHashChange(fn);
    }
    getBaseHref() { return this._baseHref; }
    prepareExternalUrl(internal) {
        return Location.joinWithSlash(this._baseHref, internal);
    }
    path(includeHash = false) {
        const pathname = this._platformLocation.pathname +
            Location.normalizeQueryParams(this._platformLocation.search);
        const hash = this._platformLocation.hash;
        return hash && includeHash ? `${pathname}${hash}` : pathname;
    }
    pushState(state, title, url, queryParams) {
        var externalUrl = this.prepareExternalUrl(url + Location.normalizeQueryParams(queryParams));
        this._platformLocation.pushState(state, title, externalUrl);
    }
    replaceState(state, title, url, queryParams) {
        var externalUrl = this.prepareExternalUrl(url + Location.normalizeQueryParams(queryParams));
        this._platformLocation.replaceState(state, title, externalUrl);
    }
    forward() { this._platformLocation.forward(); }
    back() { this._platformLocation.back(); }
}
/** @nocollapse */
PathLocationStrategy.decorators = [
    { type: Injectable },
];
/** @nocollapse */
PathLocationStrategy.ctorParameters = [
    { type: PlatformLocation, },
    { type: undefined, decorators: [{ type: Optional }, { type: Inject, args: [APP_BASE_HREF,] },] },
];
//# sourceMappingURL=path_location_strategy.js.map