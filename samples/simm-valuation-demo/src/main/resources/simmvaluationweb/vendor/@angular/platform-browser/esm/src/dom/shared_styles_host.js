/**
 * @license
 * Copyright Google Inc. All Rights Reserved.
 *
 * Use of this source code is governed by an MIT-style license that can be
 * found in the LICENSE file at https://angular.io/license
 */
import { Inject, Injectable } from '@angular/core';
import { SetWrapper } from '../facade/collection';
import { getDOM } from './dom_adapter';
import { DOCUMENT } from './dom_tokens';
export class SharedStylesHost {
    constructor() {
        /** @internal */
        this._styles = [];
        /** @internal */
        this._stylesSet = new Set();
    }
    addStyles(styles) {
        var additions = [];
        styles.forEach(style => {
            if (!SetWrapper.has(this._stylesSet, style)) {
                this._stylesSet.add(style);
                this._styles.push(style);
                additions.push(style);
            }
        });
        this.onStylesAdded(additions);
    }
    onStylesAdded(additions) { }
    getAllStyles() { return this._styles; }
}
/** @nocollapse */
SharedStylesHost.decorators = [
    { type: Injectable },
];
/** @nocollapse */
SharedStylesHost.ctorParameters = [];
export class DomSharedStylesHost extends SharedStylesHost {
    constructor(doc) {
        super();
        this._hostNodes = new Set();
        this._hostNodes.add(doc.head);
    }
    /** @internal */
    _addStylesToHost(styles, host) {
        for (var i = 0; i < styles.length; i++) {
            var style = styles[i];
            getDOM().appendChild(host, getDOM().createStyleElement(style));
        }
    }
    addHost(hostNode) {
        this._addStylesToHost(this._styles, hostNode);
        this._hostNodes.add(hostNode);
    }
    removeHost(hostNode) { SetWrapper.delete(this._hostNodes, hostNode); }
    onStylesAdded(additions) {
        this._hostNodes.forEach((hostNode) => { this._addStylesToHost(additions, hostNode); });
    }
}
/** @nocollapse */
DomSharedStylesHost.decorators = [
    { type: Injectable },
];
/** @nocollapse */
DomSharedStylesHost.ctorParameters = [
    { type: undefined, decorators: [{ type: Inject, args: [DOCUMENT,] },] },
];
//# sourceMappingURL=shared_styles_host.js.map