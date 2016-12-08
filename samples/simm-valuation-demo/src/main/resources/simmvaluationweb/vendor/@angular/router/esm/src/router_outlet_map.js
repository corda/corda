/**
 * @license
 * Copyright Google Inc. All Rights Reserved.
 *
 * Use of this source code is governed by an MIT-style license that can be
 * found in the LICENSE file at https://angular.io/license
 */
/**
 * @stable
 */
export class RouterOutletMap {
    constructor() {
        /** @internal */
        this._outlets = {};
    }
    registerOutlet(name, outlet) { this._outlets[name] = outlet; }
}
//# sourceMappingURL=router_outlet_map.js.map