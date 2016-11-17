/**
 * @license
 * Copyright Google Inc. All Rights Reserved.
 *
 * Use of this source code is governed by an MIT-style license that can be
 * found in the LICENSE file at https://angular.io/license
 */
import { Directive, Host, TemplateRef, ViewContainerRef } from '@angular/core';
import { ListWrapper, Map } from '../facade/collection';
import { isBlank, isPresent, normalizeBlank } from '../facade/lang';
const _CASE_DEFAULT = new Object();
// TODO: remove when fully deprecated
let _warned = false;
export class SwitchView {
    constructor(_viewContainerRef, _templateRef) {
        this._viewContainerRef = _viewContainerRef;
        this._templateRef = _templateRef;
    }
    create() { this._viewContainerRef.createEmbeddedView(this._templateRef); }
    destroy() { this._viewContainerRef.clear(); }
}
export class NgSwitch {
    constructor() {
        this._useDefault = false;
        this._valueViews = new Map();
        this._activeViews = [];
    }
    set ngSwitch(value) {
        // Empty the currently active ViewContainers
        this._emptyAllActiveViews();
        // Add the ViewContainers matching the value (with a fallback to default)
        this._useDefault = false;
        var views = this._valueViews.get(value);
        if (isBlank(views)) {
            this._useDefault = true;
            views = normalizeBlank(this._valueViews.get(_CASE_DEFAULT));
        }
        this._activateViews(views);
        this._switchValue = value;
    }
    /** @internal */
    _onCaseValueChanged(oldCase, newCase, view) {
        this._deregisterView(oldCase, view);
        this._registerView(newCase, view);
        if (oldCase === this._switchValue) {
            view.destroy();
            ListWrapper.remove(this._activeViews, view);
        }
        else if (newCase === this._switchValue) {
            if (this._useDefault) {
                this._useDefault = false;
                this._emptyAllActiveViews();
            }
            view.create();
            this._activeViews.push(view);
        }
        // Switch to default when there is no more active ViewContainers
        if (this._activeViews.length === 0 && !this._useDefault) {
            this._useDefault = true;
            this._activateViews(this._valueViews.get(_CASE_DEFAULT));
        }
    }
    /** @internal */
    _emptyAllActiveViews() {
        var activeContainers = this._activeViews;
        for (var i = 0; i < activeContainers.length; i++) {
            activeContainers[i].destroy();
        }
        this._activeViews = [];
    }
    /** @internal */
    _activateViews(views) {
        // TODO(vicb): assert(this._activeViews.length === 0);
        if (isPresent(views)) {
            for (var i = 0; i < views.length; i++) {
                views[i].create();
            }
            this._activeViews = views;
        }
    }
    /** @internal */
    _registerView(value, view) {
        var views = this._valueViews.get(value);
        if (isBlank(views)) {
            views = [];
            this._valueViews.set(value, views);
        }
        views.push(view);
    }
    /** @internal */
    _deregisterView(value, view) {
        // `_CASE_DEFAULT` is used a marker for non-registered cases
        if (value === _CASE_DEFAULT)
            return;
        var views = this._valueViews.get(value);
        if (views.length == 1) {
            this._valueViews.delete(value);
        }
        else {
            ListWrapper.remove(views, view);
        }
    }
}
/** @nocollapse */
NgSwitch.decorators = [
    { type: Directive, args: [{ selector: '[ngSwitch]', inputs: ['ngSwitch'] },] },
];
export class NgSwitchCase {
    constructor(viewContainer, templateRef, ngSwitch) {
        // `_CASE_DEFAULT` is used as a marker for a not yet initialized value
        /** @internal */
        this._value = _CASE_DEFAULT;
        this._switch = ngSwitch;
        this._view = new SwitchView(viewContainer, templateRef);
    }
    set ngSwitchCase(value) {
        this._switch._onCaseValueChanged(this._value, value, this._view);
        this._value = value;
    }
    set ngSwitchWhen(value) {
        if (!_warned) {
            _warned = true;
            console.warn('*ngSwitchWhen is deprecated and will be removed. Use *ngSwitchCase instead');
        }
        this._switch._onCaseValueChanged(this._value, value, this._view);
        this._value = value;
    }
}
/** @nocollapse */
NgSwitchCase.decorators = [
    { type: Directive, args: [{ selector: '[ngSwitchCase],[ngSwitchWhen]', inputs: ['ngSwitchCase', 'ngSwitchWhen'] },] },
];
/** @nocollapse */
NgSwitchCase.ctorParameters = [
    { type: ViewContainerRef, },
    { type: TemplateRef, },
    { type: NgSwitch, decorators: [{ type: Host },] },
];
export class NgSwitchDefault {
    constructor(viewContainer, templateRef, sswitch) {
        sswitch._registerView(_CASE_DEFAULT, new SwitchView(viewContainer, templateRef));
    }
}
/** @nocollapse */
NgSwitchDefault.decorators = [
    { type: Directive, args: [{ selector: '[ngSwitchDefault]' },] },
];
/** @nocollapse */
NgSwitchDefault.ctorParameters = [
    { type: ViewContainerRef, },
    { type: TemplateRef, },
    { type: NgSwitch, decorators: [{ type: Host },] },
];
//# sourceMappingURL=ng_switch.js.map