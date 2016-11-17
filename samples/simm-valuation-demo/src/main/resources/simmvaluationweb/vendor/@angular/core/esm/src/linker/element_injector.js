/**
 * @license
 * Copyright Google Inc. All Rights Reserved.
 *
 * Use of this source code is governed by an MIT-style license that can be
 * found in the LICENSE file at https://angular.io/license
 */
import { Injector, THROW_IF_NOT_FOUND } from '../di/injector';
const _UNDEFINED = new Object();
export class ElementInjector extends Injector {
    constructor(_view, _nodeIndex) {
        super();
        this._view = _view;
        this._nodeIndex = _nodeIndex;
    }
    get(token, notFoundValue = THROW_IF_NOT_FOUND) {
        var result = _UNDEFINED;
        if (result === _UNDEFINED) {
            result = this._view.injectorGet(token, this._nodeIndex, _UNDEFINED);
        }
        if (result === _UNDEFINED) {
            result = this._view.parentInjector.get(token, notFoundValue);
        }
        return result;
    }
}
//# sourceMappingURL=element_injector.js.map