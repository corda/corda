/**
 * @license
 * Copyright Google Inc. All Rights Reserved.
 *
 * Use of this source code is governed by an MIT-style license that can be
 * found in the LICENSE file at https://angular.io/license
 */
"use strict";
function __export(m) {
    for (var p in m) if (!exports.hasOwnProperty(p)) exports[p] = m[p];
}
/**
 * @module
 * @description
 * This module is used for handling user input, by defining and building a {@link FormGroup} that
 * consists of
 * {@link FormControl} objects, and mapping them onto the DOM. {@link FormControl} objects can then
 * be used
 * to read information
 * from the form DOM elements.
 *
 * Forms providers are not included in default providers; you must import these providers
 * explicitly.
 */
var directives_1 = require('./directives');
exports.FORM_DIRECTIVES = directives_1.FORM_DIRECTIVES;
exports.REACTIVE_FORM_DIRECTIVES = directives_1.REACTIVE_FORM_DIRECTIVES;
var abstract_control_directive_1 = require('./directives/abstract_control_directive');
exports.AbstractControlDirective = abstract_control_directive_1.AbstractControlDirective;
var checkbox_value_accessor_1 = require('./directives/checkbox_value_accessor');
exports.CheckboxControlValueAccessor = checkbox_value_accessor_1.CheckboxControlValueAccessor;
var control_container_1 = require('./directives/control_container');
exports.ControlContainer = control_container_1.ControlContainer;
var control_value_accessor_1 = require('./directives/control_value_accessor');
exports.NG_VALUE_ACCESSOR = control_value_accessor_1.NG_VALUE_ACCESSOR;
var default_value_accessor_1 = require('./directives/default_value_accessor');
exports.DefaultValueAccessor = default_value_accessor_1.DefaultValueAccessor;
var ng_control_1 = require('./directives/ng_control');
exports.NgControl = ng_control_1.NgControl;
var ng_control_status_1 = require('./directives/ng_control_status');
exports.NgControlStatus = ng_control_status_1.NgControlStatus;
var ng_form_1 = require('./directives/ng_form');
exports.NgForm = ng_form_1.NgForm;
var ng_model_1 = require('./directives/ng_model');
exports.NgModel = ng_model_1.NgModel;
var ng_model_group_1 = require('./directives/ng_model_group');
exports.NgModelGroup = ng_model_group_1.NgModelGroup;
var form_array_name_1 = require('./directives/reactive_directives/form_array_name');
exports.FormArrayName = form_array_name_1.FormArrayName;
var form_control_directive_1 = require('./directives/reactive_directives/form_control_directive');
exports.FormControlDirective = form_control_directive_1.FormControlDirective;
var form_control_name_1 = require('./directives/reactive_directives/form_control_name');
exports.FormControlName = form_control_name_1.FormControlName;
var form_group_directive_1 = require('./directives/reactive_directives/form_group_directive');
exports.FormGroupDirective = form_group_directive_1.FormGroupDirective;
var form_group_name_1 = require('./directives/reactive_directives/form_group_name');
exports.FormGroupName = form_group_name_1.FormGroupName;
var select_control_value_accessor_1 = require('./directives/select_control_value_accessor');
exports.NgSelectOption = select_control_value_accessor_1.NgSelectOption;
exports.SelectControlValueAccessor = select_control_value_accessor_1.SelectControlValueAccessor;
var validators_1 = require('./directives/validators');
exports.MaxLengthValidator = validators_1.MaxLengthValidator;
exports.MinLengthValidator = validators_1.MinLengthValidator;
exports.PatternValidator = validators_1.PatternValidator;
exports.RequiredValidator = validators_1.RequiredValidator;
var form_builder_1 = require('./form_builder');
exports.FormBuilder = form_builder_1.FormBuilder;
var model_1 = require('./model');
exports.AbstractControl = model_1.AbstractControl;
exports.FormArray = model_1.FormArray;
exports.FormControl = model_1.FormControl;
exports.FormGroup = model_1.FormGroup;
var validators_2 = require('./validators');
exports.NG_ASYNC_VALIDATORS = validators_2.NG_ASYNC_VALIDATORS;
exports.NG_VALIDATORS = validators_2.NG_VALIDATORS;
exports.Validators = validators_2.Validators;
__export(require('./form_providers'));
//# sourceMappingURL=forms.js.map