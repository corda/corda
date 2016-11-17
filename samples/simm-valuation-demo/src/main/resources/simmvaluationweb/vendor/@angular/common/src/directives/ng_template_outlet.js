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
var NgTemplateOutlet = (function () {
    function NgTemplateOutlet(_viewContainerRef) {
        this._viewContainerRef = _viewContainerRef;
    }
    Object.defineProperty(NgTemplateOutlet.prototype, "ngOutletContext", {
        set: function (context) {
            if (this._context !== context) {
                this._context = context;
                if (lang_1.isPresent(this._viewRef)) {
                    this.createView();
                }
            }
        },
        enumerable: true,
        configurable: true
    });
    Object.defineProperty(NgTemplateOutlet.prototype, "ngTemplateOutlet", {
        set: function (templateRef) {
            if (this._templateRef !== templateRef) {
                this._templateRef = templateRef;
                this.createView();
            }
        },
        enumerable: true,
        configurable: true
    });
    NgTemplateOutlet.prototype.createView = function () {
        if (lang_1.isPresent(this._viewRef)) {
            this._viewContainerRef.remove(this._viewContainerRef.indexOf(this._viewRef));
        }
        if (lang_1.isPresent(this._templateRef)) {
            this._viewRef = this._viewContainerRef.createEmbeddedView(this._templateRef, this._context);
        }
    };
    /** @nocollapse */
    NgTemplateOutlet.decorators = [
        { type: core_1.Directive, args: [{ selector: '[ngTemplateOutlet]' },] },
    ];
    /** @nocollapse */
    NgTemplateOutlet.ctorParameters = [
        { type: core_1.ViewContainerRef, },
    ];
    /** @nocollapse */
    NgTemplateOutlet.propDecorators = {
        'ngOutletContext': [{ type: core_1.Input },],
        'ngTemplateOutlet': [{ type: core_1.Input },],
    };
    return NgTemplateOutlet;
}());
exports.NgTemplateOutlet = NgTemplateOutlet;
//# sourceMappingURL=ng_template_outlet.js.map