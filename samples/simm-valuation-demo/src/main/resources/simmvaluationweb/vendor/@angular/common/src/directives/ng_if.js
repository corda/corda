/**
 * @license
 * Copyright Google Inc. All Rights Reserved.
 *
 * Use of this source code is governed by an MIT-style license that can be
 * found in the LICENSE file at https://angular.io/license
 */
"use strict";
var core_1 = require('@angular/core');
var lang_1 = require('../facade/lang');
var NgIf = (function () {
    function NgIf(_viewContainer, _templateRef) {
        this._viewContainer = _viewContainer;
        this._templateRef = _templateRef;
        this._prevCondition = null;
    }
    Object.defineProperty(NgIf.prototype, "ngIf", {
        set: function (newCondition /* boolean */) {
            if (newCondition && (lang_1.isBlank(this._prevCondition) || !this._prevCondition)) {
                this._prevCondition = true;
                this._viewContainer.createEmbeddedView(this._templateRef);
            }
            else if (!newCondition && (lang_1.isBlank(this._prevCondition) || this._prevCondition)) {
                this._prevCondition = false;
                this._viewContainer.clear();
            }
        },
        enumerable: true,
        configurable: true
    });
    /** @nocollapse */
    NgIf.decorators = [
        { type: core_1.Directive, args: [{ selector: '[ngIf]', inputs: ['ngIf'] },] },
    ];
    /** @nocollapse */
    NgIf.ctorParameters = [
        { type: core_1.ViewContainerRef, },
        { type: core_1.TemplateRef, },
    ];
    return NgIf;
}());
exports.NgIf = NgIf;
//# sourceMappingURL=ng_if.js.map