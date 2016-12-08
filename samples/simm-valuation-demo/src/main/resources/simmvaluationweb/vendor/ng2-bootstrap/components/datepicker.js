"use strict";
/*
 todo: general:
 1. Popup
 2. Keyboard support
 3. custom-class attribute support
 4. date-disabled attribute support
 5. template-url attribute support
 */
var datepicker_popup_component_1 = require('./datepicker/datepicker-popup.component');
var datepicker_component_1 = require('./datepicker/datepicker.component');
var datepicker_popup_component_2 = require('./datepicker/datepicker-popup.component');
exports.DatePickerPopupDirective = datepicker_popup_component_2.DatePickerPopupDirective;
var datepicker_component_2 = require('./datepicker/datepicker.component');
exports.DatePickerComponent = datepicker_component_2.DatePickerComponent;
exports.DATEPICKER_DIRECTIVES = [datepicker_component_1.DatePickerComponent, datepicker_popup_component_1.DatePickerPopupDirective];
