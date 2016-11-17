/**
 * @license
 * Copyright Google Inc. All Rights Reserved.
 *
 * Use of this source code is governed by an MIT-style license that can be
 * found in the LICENSE file at https://angular.io/license
 */
import { Directive, Input, ViewContainerRef } from '@angular/core';
import { isPresent } from '../facade/lang';
export class NgTemplateOutlet {
    constructor(_viewContainerRef) {
        this._viewContainerRef = _viewContainerRef;
    }
    set ngOutletContext(context) {
        if (this._context !== context) {
            this._context = context;
            if (isPresent(this._viewRef)) {
                this.createView();
            }
        }
    }
    set ngTemplateOutlet(templateRef) {
        if (this._templateRef !== templateRef) {
            this._templateRef = templateRef;
            this.createView();
        }
    }
    createView() {
        if (isPresent(this._viewRef)) {
            this._viewContainerRef.remove(this._viewContainerRef.indexOf(this._viewRef));
        }
        if (isPresent(this._templateRef)) {
            this._viewRef = this._viewContainerRef.createEmbeddedView(this._templateRef, this._context);
        }
    }
}
/** @nocollapse */
NgTemplateOutlet.decorators = [
    { type: Directive, args: [{ selector: '[ngTemplateOutlet]' },] },
];
/** @nocollapse */
NgTemplateOutlet.ctorParameters = [
    { type: ViewContainerRef, },
];
/** @nocollapse */
NgTemplateOutlet.propDecorators = {
    'ngOutletContext': [{ type: Input },],
    'ngTemplateOutlet': [{ type: Input },],
};
//# sourceMappingURL=ng_template_outlet.js.map