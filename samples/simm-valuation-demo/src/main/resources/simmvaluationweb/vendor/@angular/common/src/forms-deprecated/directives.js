/**
 * @license
 * Copyright Google Inc. All Rights Reserved.
 *
 * Use of this source code is governed by an MIT-style license that can be
 * found in the LICENSE file at https://angular.io/license
 */
"use strict";
var checkbox_value_accessor_1 = require('./directives/checkbox_value_accessor');
var default_value_accessor_1 = require('./directives/default_value_accessor');
var ng_control_group_1 = require('./directives/ng_control_group');
var ng_control_name_1 = require('./directives/ng_control_name');
var ng_control_status_1 = require('./directives/ng_control_status');
var ng_form_1 = require('./directives/ng_form');
var ng_form_control_1 = require('./directives/ng_form_control');
var ng_form_model_1 = require('./directives/ng_form_model');
var ng_model_1 = require('./directives/ng_model');
var number_value_accessor_1 = require('./directives/number_value_accessor');
var radio_control_value_accessor_1 = require('./directives/radio_control_value_accessor');
var select_control_value_accessor_1 = require('./directives/select_control_value_accessor');
var select_multiple_control_value_accessor_1 = require('./directives/select_multiple_control_value_accessor');
var validators_1 = require('./directives/validators');
var checkbox_value_accessor_2 = require('./directives/checkbox_value_accessor');
exports.CheckboxControlValueAccessor = checkbox_value_accessor_2.CheckboxControlValueAccessor;
var default_value_accessor_2 = require('./directives/default_value_accessor');
exports.DefaultValueAccessor = default_value_accessor_2.DefaultValueAccessor;
var ng_control_1 = require('./directives/ng_control');
exports.NgControl = ng_control_1.NgControl;
var ng_control_group_2 = require('./directives/ng_control_group');
exports.NgControlGroup = ng_control_group_2.NgControlGroup;
var ng_control_name_2 = require('./directives/ng_control_name');
exports.NgControlName = ng_control_name_2.NgControlName;
var ng_control_status_2 = require('./directives/ng_control_status');
exports.NgControlStatus = ng_control_status_2.NgControlStatus;
var ng_form_2 = require('./directives/ng_form');
exports.NgForm = ng_form_2.NgForm;
var ng_form_control_2 = require('./directives/ng_form_control');
exports.NgFormControl = ng_form_control_2.NgFormControl;
var ng_form_model_2 = require('./directives/ng_form_model');
exports.NgFormModel = ng_form_model_2.NgFormModel;
var ng_model_2 = require('./directives/ng_model');
exports.NgModel = ng_model_2.NgModel;
var number_value_accessor_2 = require('./directives/number_value_accessor');
exports.NumberValueAccessor = number_value_accessor_2.NumberValueAccessor;
var radio_control_value_accessor_2 = require('./directives/radio_control_value_accessor');
exports.RadioButtonState = radio_control_value_accessor_2.RadioButtonState;
exports.RadioControlValueAccessor = radio_control_value_accessor_2.RadioControlValueAccessor;
var select_control_value_accessor_2 = require('./directives/select_control_value_accessor');
exports.NgSelectOption = select_control_value_accessor_2.NgSelectOption;
exports.SelectControlValueAccessor = select_control_value_accessor_2.SelectControlValueAccessor;
var select_multiple_control_value_accessor_2 = require('./directives/select_multiple_control_value_accessor');
exports.NgSelectMultipleOption = select_multiple_control_value_accessor_2.NgSelectMultipleOption;
exports.SelectMultipleControlValueAccessor = select_multiple_control_value_accessor_2.SelectMultipleControlValueAccessor;
var validators_2 = require('./directives/validators');
exports.MaxLengthValidator = validators_2.MaxLengthValidator;
exports.MinLengthValidator = validators_2.MinLengthValidator;
exports.PatternValidator = validators_2.PatternValidator;
exports.RequiredValidator = validators_2.RequiredValidator;
/**
 *
 * A list of all the form directives used as part of a `@Component` annotation.
 *
 *  This is a shorthand for importing them each individually.
 *
 * ### Example
 *
 * ```typescript
 * @Component({
 *   selector: 'my-app',
 *   directives: [FORM_DIRECTIVES]
 * })
 * class MyApp {}
 * ```
 * @experimental
 */
exports.FORM_DIRECTIVES = [
    ng_control_name_1.NgControlName,
    ng_control_group_1.NgControlGroup,
    ng_form_control_1.NgFormControl,
    ng_model_1.NgModel,
    ng_form_model_1.NgFormModel,
    ng_form_1.NgForm,
    select_control_value_accessor_1.NgSelectOption,
    select_multiple_control_value_accessor_1.NgSelectMultipleOption,
    default_value_accessor_1.DefaultValueAccessor,
    number_value_accessor_1.NumberValueAccessor,
    checkbox_value_accessor_1.CheckboxControlValueAccessor,
    select_control_value_accessor_1.SelectControlValueAccessor,
    select_multiple_control_value_accessor_1.SelectMultipleControlValueAccessor,
    radio_control_value_accessor_1.RadioControlValueAccessor,
    ng_control_status_1.NgControlStatus,
    validators_1.RequiredValidator,
    validators_1.MinLengthValidator,
    validators_1.MaxLengthValidator,
    validators_1.PatternValidator,
];
//# sourceMappingURL=directives.js.map