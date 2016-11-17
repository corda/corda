/**
 * @license
 * Copyright Google Inc. All Rights Reserved.
 *
 * Use of this source code is governed by an MIT-style license that can be
 * found in the LICENSE file at https://angular.io/license
 */
"use strict";
var __extends = (this && this.__extends) || function (d, b) {
    for (var p in b) if (b.hasOwnProperty(p)) d[p] = b[p];
    function __() { this.constructor = d; }
    d.prototype = b === null ? Object.create(b) : (__.prototype = b.prototype, new __());
};
var core_1 = require('@angular/core');
var validators_1 = require('../validators');
var abstract_form_group_directive_1 = require('./abstract_form_group_directive');
var control_container_1 = require('./control_container');
exports.modelGroupProvider = 
/*@ts2dart_const*/ /* @ts2dart_Provider */ {
    provide: control_container_1.ControlContainer,
    useExisting: core_1.forwardRef(function () { return NgModelGroup; })
};
var NgModelGroup = (function (_super) {
    __extends(NgModelGroup, _super);
    function NgModelGroup(parent, validators, asyncValidators) {
        _super.call(this);
        this._parent = parent;
        this._validators = validators;
        this._asyncValidators = asyncValidators;
    }
    /** @nocollapse */
    NgModelGroup.decorators = [
        { type: core_1.Directive, args: [{ selector: '[ngModelGroup]', providers: [exports.modelGroupProvider], exportAs: 'ngModelGroup' },] },
    ];
    /** @nocollapse */
    NgModelGroup.ctorParameters = [
        { type: control_container_1.ControlContainer, decorators: [{ type: core_1.Host }, { type: core_1.SkipSelf },] },
        { type: Array, decorators: [{ type: core_1.Optional }, { type: core_1.Self }, { type: core_1.Inject, args: [validators_1.NG_VALIDATORS,] },] },
        { type: Array, decorators: [{ type: core_1.Optional }, { type: core_1.Self }, { type: core_1.Inject, args: [validators_1.NG_ASYNC_VALIDATORS,] },] },
    ];
    /** @nocollapse */
    NgModelGroup.propDecorators = {
        'name': [{ type: core_1.Input, args: ['ngModelGroup',] },],
    };
    return NgModelGroup;
}(abstract_form_group_directive_1.AbstractFormGroupDirective));
exports.NgModelGroup = NgModelGroup;
//# sourceMappingURL=ng_model_group.js.map