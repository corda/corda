/**
 * @license
 * Copyright Google Inc. All Rights Reserved.
 *
 * Use of this source code is governed by an MIT-style license that can be
 * found in the LICENSE file at https://angular.io/license
 */
"use strict";
var radio_control_value_accessor_1 = require('./forms-deprecated/directives/radio_control_value_accessor');
var form_builder_1 = require('./forms-deprecated/form_builder');
var directives_1 = require('./forms-deprecated/directives');
exports.FORM_DIRECTIVES = directives_1.FORM_DIRECTIVES;
exports.RadioButtonState = directives_1.RadioButtonState;
var abstract_control_directive_1 = require('./forms-deprecated/directives/abstract_control_directive');
exports.AbstractControlDirective = abstract_control_directive_1.AbstractControlDirective;
var checkbox_value_accessor_1 = require('./forms-deprecated/directives/checkbox_value_accessor');
exports.CheckboxControlValueAccessor = checkbox_value_accessor_1.CheckboxControlValueAccessor;
var control_container_1 = require('./forms-deprecated/directives/control_container');
exports.ControlContainer = control_container_1.ControlContainer;
var control_value_accessor_1 = require('./forms-deprecated/directives/control_value_accessor');
exports.NG_VALUE_ACCESSOR = control_value_accessor_1.NG_VALUE_ACCESSOR;
var default_value_accessor_1 = require('./forms-deprecated/directives/default_value_accessor');
exports.DefaultValueAccessor = default_value_accessor_1.DefaultValueAccessor;
var ng_control_1 = require('./forms-deprecated/directives/ng_control');
exports.NgControl = ng_control_1.NgControl;
var ng_control_group_1 = require('./forms-deprecated/directives/ng_control_group');
exports.NgControlGroup = ng_control_group_1.NgControlGroup;
var ng_control_name_1 = require('./forms-deprecated/directives/ng_control_name');
exports.NgControlName = ng_control_name_1.NgControlName;
var ng_control_status_1 = require('./forms-deprecated/directives/ng_control_status');
exports.NgControlStatus = ng_control_status_1.NgControlStatus;
var ng_form_1 = require('./forms-deprecated/directives/ng_form');
exports.NgForm = ng_form_1.NgForm;
var ng_form_control_1 = require('./forms-deprecated/directives/ng_form_control');
exports.NgFormControl = ng_form_control_1.NgFormControl;
var ng_form_model_1 = require('./forms-deprecated/directives/ng_form_model');
exports.NgFormModel = ng_form_model_1.NgFormModel;
var ng_model_1 = require('./forms-deprecated/directives/ng_model');
exports.NgModel = ng_model_1.NgModel;
var select_control_value_accessor_1 = require('./forms-deprecated/directives/select_control_value_accessor');
exports.NgSelectOption = select_control_value_accessor_1.NgSelectOption;
exports.SelectControlValueAccessor = select_control_value_accessor_1.SelectControlValueAccessor;
var validators_1 = require('./forms-deprecated/directives/validators');
exports.MaxLengthValidator = validators_1.MaxLengthValidator;
exports.MinLengthValidator = validators_1.MinLengthValidator;
exports.PatternValidator = validators_1.PatternValidator;
exports.RequiredValidator = validators_1.RequiredValidator;
var form_builder_2 = require('./forms-deprecated/form_builder');
exports.FormBuilder = form_builder_2.FormBuilder;
var model_1 = require('./forms-deprecated/model');
exports.AbstractControl = model_1.AbstractControl;
exports.Control = model_1.Control;
exports.ControlArray = model_1.ControlArray;
exports.ControlGroup = model_1.ControlGroup;
var validators_2 = require('./forms-deprecated/validators');
exports.NG_ASYNC_VALIDATORS = validators_2.NG_ASYNC_VALIDATORS;
exports.NG_VALIDATORS = validators_2.NG_VALIDATORS;
exports.Validators = validators_2.Validators;
/**
 * Shorthand set of providers used for building Angular forms.
 *
 * ### Example
 *
 * ```typescript
 * bootstrap(MyApp, [FORM_PROVIDERS]);
 * ```
 *
 * @experimental
 */
exports.FORM_PROVIDERS = [form_builder_1.FormBuilder, radio_control_value_accessor_1.RadioControlRegistry];
//# sourceMappingURL=forms-deprecated.js.map