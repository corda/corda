/**
 * @license
 * Copyright Google Inc. All Rights Reserved.
 *
 * Use of this source code is governed by an MIT-style license that can be
 * found in the LICENSE file at https://angular.io/license
 */
import { Directive, TemplateRef, ViewContainerRef } from '@angular/core';
import { isBlank } from '../facade/lang';
export class NgIf {
    constructor(_viewContainer, _templateRef) {
        this._viewContainer = _viewContainer;
        this._templateRef = _templateRef;
        this._prevCondition = null;
    }
    set ngIf(newCondition /* boolean */) {
        if (newCondition && (isBlank(this._prevCondition) || !this._prevCondition)) {
            this._prevCondition = true;
            this._viewContainer.createEmbeddedView(this._templateRef);
        }
        else if (!newCondition && (isBlank(this._prevCondition) || this._prevCondition)) {
            this._prevCondition = false;
            this._viewContainer.clear();
        }
    }
}
/** @nocollapse */
NgIf.decorators = [
    { type: Directive, args: [{ selector: '[ngIf]', inputs: ['ngIf'] },] },
];
/** @nocollapse */
NgIf.ctorParameters = [
    { type: ViewContainerRef, },
    { type: TemplateRef, },
];
//# sourceMappingURL=ng_if.js.map