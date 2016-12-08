/**
 * @license
 * Copyright Google Inc. All Rights Reserved.
 *
 * Use of this source code is governed by an MIT-style license that can be
 * found in the LICENSE file at https://angular.io/license
 */
import { TestComponentRenderer } from '@angular/compiler/testing';
import { Inject, Injectable } from '@angular/core';
import { DOCUMENT } from '@angular/platform-browser';
import { getDOM } from '../platform_browser_private';
export class DOMTestComponentRenderer extends TestComponentRenderer {
    constructor(_doc /** TODO #9100 */) {
        super();
        this._doc = _doc;
    }
    insertRootElement(rootElId) {
        let rootEl = getDOM().firstChild(getDOM().content(getDOM().createTemplate(`<div id="${rootElId}"></div>`)));
        // TODO(juliemr): can/should this be optional?
        let oldRoots = getDOM().querySelectorAll(this._doc, '[id^=root]');
        for (let i = 0; i < oldRoots.length; i++) {
            getDOM().remove(oldRoots[i]);
        }
        getDOM().appendChild(this._doc.body, rootEl);
    }
}
/** @nocollapse */
DOMTestComponentRenderer.decorators = [
    { type: Injectable },
];
/** @nocollapse */
DOMTestComponentRenderer.ctorParameters = [
    { type: undefined, decorators: [{ type: Inject, args: [DOCUMENT,] },] },
];
//# sourceMappingURL=dom_test_component_renderer.js.map