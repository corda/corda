/**
 * @license
 * Copyright Google Inc. All Rights Reserved.
 *
 * Use of this source code is governed by an MIT-style license that can be
 * found in the LICENSE file at https://angular.io/license
 */
import { Attribute, ContentChildren, Directive, Input, TemplateRef, ViewContainerRef } from '@angular/core';
import { isPresent } from '../facade/lang';
import { NgLocalization, getPluralCategory } from '../localization';
import { SwitchView } from './ng_switch';
export class NgPluralCase {
    constructor(value, template, viewContainer) {
        this.value = value;
        this._view = new SwitchView(viewContainer, template);
    }
}
/** @nocollapse */
NgPluralCase.decorators = [
    { type: Directive, args: [{ selector: '[ngPluralCase]' },] },
];
/** @nocollapse */
NgPluralCase.ctorParameters = [
    { type: undefined, decorators: [{ type: Attribute, args: ['ngPluralCase',] },] },
    { type: TemplateRef, },
    { type: ViewContainerRef, },
];
export class NgPlural {
    constructor(_localization) {
        this._localization = _localization;
        this._caseViews = {};
        this.cases = null;
    }
    set ngPlural(value) {
        this._switchValue = value;
        this._updateView();
    }
    ngAfterContentInit() {
        this.cases.forEach((pluralCase) => {
            this._caseViews[pluralCase.value] = pluralCase._view;
        });
        this._updateView();
    }
    /** @internal */
    _updateView() {
        this._clearViews();
        var key = getPluralCategory(this._switchValue, Object.getOwnPropertyNames(this._caseViews), this._localization);
        this._activateView(this._caseViews[key]);
    }
    /** @internal */
    _clearViews() {
        if (isPresent(this._activeView))
            this._activeView.destroy();
    }
    /** @internal */
    _activateView(view) {
        if (!isPresent(view))
            return;
        this._activeView = view;
        this._activeView.create();
    }
}
/** @nocollapse */
NgPlural.decorators = [
    { type: Directive, args: [{ selector: '[ngPlural]' },] },
];
/** @nocollapse */
NgPlural.ctorParameters = [
    { type: NgLocalization, },
];
/** @nocollapse */
NgPlural.propDecorators = {
    'cases': [{ type: ContentChildren, args: [NgPluralCase,] },],
    'ngPlural': [{ type: Input },],
};
//# sourceMappingURL=ng_plural.js.map