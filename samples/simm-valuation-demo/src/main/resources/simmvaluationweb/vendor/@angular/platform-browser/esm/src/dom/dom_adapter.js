/**
 * @license
 * Copyright Google Inc. All Rights Reserved.
 *
 * Use of this source code is governed by an MIT-style license that can be
 * found in the LICENSE file at https://angular.io/license
 */
import { isBlank } from '../facade/lang';
var _DOM = null;
export function getDOM() {
    return _DOM;
}
export function setDOM(adapter) {
    _DOM = adapter;
}
export function setRootDomAdapter(adapter) {
    if (isBlank(_DOM)) {
        _DOM = adapter;
    }
}
/* tslint:disable:requireParameterType */
/**
 * Provides DOM operations in an environment-agnostic way.
 */
export class DomAdapter {
    constructor() {
        this.xhrType = null;
    }
    /** @deprecated */
    getXHR() { return this.xhrType; }
    /**
     * Maps attribute names to their corresponding property names for cases
     * where attribute name doesn't match property name.
     */
    get attrToPropMap() { return this._attrToPropMap; }
    ;
    set attrToPropMap(value) { this._attrToPropMap = value; }
    ;
}
//# sourceMappingURL=dom_adapter.js.map