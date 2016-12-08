"use strict";
var __decorate = (this && this.__decorate) || function (decorators, target, key, desc) {
    var c = arguments.length, r = c < 3 ? target : desc === null ? desc = Object.getOwnPropertyDescriptor(target, key) : desc, d;
    if (typeof Reflect === "object" && typeof Reflect.decorate === "function") r = Reflect.decorate(decorators, target, key, desc);
    else for (var i = decorators.length - 1; i >= 0; i--) if (d = decorators[i]) r = (c < 3 ? d(r) : c > 3 ? d(target, key, r) : d(target, key)) || r;
    return c > 3 && r && Object.defineProperty(target, key, r), r;
};
var __metadata = (this && this.__metadata) || function (k, v) {
    if (typeof Reflect === "object" && typeof Reflect.metadata === "function") return Reflect.metadata(k, v);
};
var core_1 = require('@angular/core');
var common_1 = require('@angular/common');
var forms_1 = require('@angular/forms');
var ng2_bootstrap_config_1 = require('../ng2-bootstrap-config');
var datepicker_inner_component_1 = require('./datepicker-inner.component');
// write an interface for template options
var TEMPLATE_OPTIONS = (_a = {},
    _a[ng2_bootstrap_config_1.Ng2BootstrapTheme.BS4] = {
        DAY_TITLE: "\n        <th *ngFor=\"let labelz of labels\" class=\"text-xs-center\"><small aria-label=\"labelz.full\"><b>{{labelz.abbr}}</b></small></th>\n    ",
        WEEK_ROW: "\n        <td *ngIf=\"datePicker.showWeeks\" class=\"text-xs-center h6\"><em>{{ weekNumbers[index] }}</em></td>\n        <td *ngFor=\"let dtz of rowz\" class=\"text-xs-center\" role=\"gridcell\" [id]=\"dtz.uid\">\n          <button type=\"button\" style=\"min-width:100%;\" class=\"btn btn-sm {{dtz.customClass}}\"\n                  *ngIf=\"!(datePicker.onlyCurrentMonth && dtz.secondary)\"\n                  [ngClass]=\"{'btn-secondary': !dtz.selected && !datePicker.isActive(dtz), 'btn-info': dtz.selected, disabled: dtz.disabled}\"\n                  [disabled]=\"dtz.disabled\"\n                  (click)=\"datePicker.select(dtz.date)\" tabindex=\"-1\">\n            <span [ngClass]=\"{'text-muted': dtz.secondary || dtz.current}\">{{dtz.label}}</span>\n          </button>\n        </td>\n    ",
        ARROW_LEFT: '&lt;',
        ARROW_RIGHT: '&gt;'
    },
    _a[ng2_bootstrap_config_1.Ng2BootstrapTheme.BS3] = {
        DAY_TITLE: "\n        <th *ngFor=\"let labelz of labels\" class=\"text-center\"><small aria-label=\"labelz.full\"><b>{{labelz.abbr}}</b></small></th>\n    ",
        WEEK_ROW: "\n        <td *ngIf=\"datePicker.showWeeks\" class=\"text-center h6\"><em>{{ weekNumbers[index] }}</em></td>\n        <td *ngFor=\"let dtz of rowz\" class=\"text-center\" role=\"gridcell\" [id]=\"dtz.uid\">\n          <button type=\"button\" style=\"min-width:100%;\" class=\"btn btn-default btn-sm {{dtz.customClass}}\"\n                  *ngIf=\"!(datePicker.onlyCurrentMonth && dtz.secondary)\"\n                  [ngClass]=\"{'btn-info': dtz.selected, active: datePicker.isActive(dtz), disabled: dtz.disabled}\"\n                  [disabled]=\"dtz.disabled\"\n                  (click)=\"datePicker.select(dtz.date)\" tabindex=\"-1\">\n            <span [ngClass]=\"{'text-muted': dtz.secondary, 'text-info': dtz.current}\">{{dtz.label}}</span>\n          </button>\n        </td>\n    ",
        ARROW_LEFT: "\n    <i class=\"glyphicon glyphicon-chevron-left\"></i>\n    ",
        ARROW_RIGHT: "\n    <i class=\"glyphicon glyphicon-chevron-right\"></i>\n    "
    },
    _a
);
var CURRENT_THEME_TEMPLATE = TEMPLATE_OPTIONS[ng2_bootstrap_config_1.Ng2BootstrapConfig.theme || ng2_bootstrap_config_1.Ng2BootstrapTheme.BS3];
var DayPickerComponent = (function () {
    function DayPickerComponent(datePicker) {
        this.labels = [];
        this.rows = [];
        this.weekNumbers = [];
        this.datePicker = datePicker;
    }
    /*private getDaysInMonth(year:number, month:number) {
     return ((month === 1) && (year % 4 === 0) &&
     ((year % 100 !== 0) || (year % 400 === 0))) ? 29 : DAYS_IN_MONTH[month];
     }*/
    DayPickerComponent.prototype.ngOnInit = function () {
        var self = this;
        this.datePicker.stepDay = { months: 1 };
        this.datePicker.setRefreshViewHandler(function () {
            var year = this.activeDate.getFullYear();
            var month = this.activeDate.getMonth();
            var firstDayOfMonth = new Date(year, month, 1);
            var difference = this.startingDay - firstDayOfMonth.getDay();
            var numDisplayedFromPreviousMonth = (difference > 0)
                ? 7 - difference
                : -difference;
            var firstDate = new Date(firstDayOfMonth.getTime());
            if (numDisplayedFromPreviousMonth > 0) {
                firstDate.setDate(-numDisplayedFromPreviousMonth + 1);
            }
            // 42 is the number of days on a six-week calendar
            var _days = self.getDates(firstDate, 42);
            var days = [];
            for (var i = 0; i < 42; i++) {
                var _dateObject = this.createDateObject(_days[i], this.formatDay);
                _dateObject.secondary = _days[i].getMonth() !== month;
                _dateObject.uid = this.uniqueId + '-' + i;
                days[i] = _dateObject;
            }
            self.labels = [];
            for (var j = 0; j < 7; j++) {
                self.labels[j] = {};
                self.labels[j].abbr = this.dateFilter(days[j].date, this.formatDayHeader);
                self.labels[j].full = this.dateFilter(days[j].date, 'EEEE');
            }
            self.title = this.dateFilter(this.activeDate, this.formatDayTitle);
            self.rows = this.split(days, 7);
            if (this.showWeeks) {
                self.weekNumbers = [];
                var thursdayIndex = (4 + 7 - this.startingDay) % 7;
                var numWeeks = self.rows.length;
                for (var curWeek = 0; curWeek < numWeeks; curWeek++) {
                    self.weekNumbers.push(self.getISO8601WeekNumber(self.rows[curWeek][thursdayIndex].date));
                }
            }
        }, 'day');
        this.datePicker.setCompareHandler(function (date1, date2) {
            var d1 = new Date(date1.getFullYear(), date1.getMonth(), date1.getDate());
            var d2 = new Date(date2.getFullYear(), date2.getMonth(), date2.getDate());
            return d1.getTime() - d2.getTime();
        }, 'day');
        this.datePicker.refreshView();
    };
    DayPickerComponent.prototype.getDates = function (startDate, n) {
        var dates = new Array(n);
        var current = new Date(startDate.getTime());
        var i = 0;
        var date;
        while (i < n) {
            date = new Date(current.getTime());
            date = this.datePicker.fixTimeZone(date);
            dates[i++] = date;
            current = new Date(current.getFullYear(), current.getMonth(), current.getDate() + 1);
        }
        return dates;
    };
    DayPickerComponent.prototype.getISO8601WeekNumber = function (date) {
        var checkDate = new Date(date.getTime());
        // Thursday
        checkDate.setDate(checkDate.getDate() + 4 - (checkDate.getDay() || 7));
        var time = checkDate.getTime();
        // Compare with Jan 1
        checkDate.setMonth(0);
        checkDate.setDate(1);
        return Math.floor(Math.round((time - checkDate.getTime()) / 86400000) / 7) + 1;
    };
    DayPickerComponent = __decorate([
        core_1.Component({
            selector: 'daypicker',
            template: "\n<table *ngIf=\"datePicker.datepickerMode==='day'\" role=\"grid\" aria-labelledby=\"uniqueId+'-title'\" aria-activedescendant=\"activeDateId\">\n  <thead>\n    <tr>\n      <th>\n        <button type=\"button\" class=\"btn btn-default btn-secondary btn-sm pull-left\" (click)=\"datePicker.move(-1)\" tabindex=\"-1\">\n        " + CURRENT_THEME_TEMPLATE.ARROW_LEFT + "\n        </button>\n      </th>\n      <th [attr.colspan]=\"5 + datePicker.showWeeks\">\n        <button [id]=\"datePicker.uniqueId + '-title'\"\n                type=\"button\" class=\"btn btn-default btn-secondary btn-sm\"\n                (click)=\"datePicker.toggleMode()\"\n                [disabled]=\"datePicker.datepickerMode === datePicker.maxMode\"\n                [ngClass]=\"{disabled: datePicker.datepickerMode === datePicker.maxMode}\" tabindex=\"-1\" style=\"width:100%;\">\n          <strong>{{title}}</strong>\n        </button>\n      </th>\n      <th>\n        <button type=\"button\" class=\"btn btn-default btn-secondary btn-sm pull-right\" (click)=\"datePicker.move(1)\" tabindex=\"-1\">\n        " + CURRENT_THEME_TEMPLATE.ARROW_RIGHT + "\n        </button>\n      </th>\n    </tr>\n    <tr>\n      <th *ngIf=\"datePicker.showWeeks\"></th>\n      " + CURRENT_THEME_TEMPLATE.DAY_TITLE + "\n    </tr>\n  </thead>\n  <tbody>\n    <template ngFor [ngForOf]=\"rows\" let-rowz=\"$implicit\" let-index=\"index\">\n      <tr *ngIf=\"!(datePicker.onlyCurrentMonth && rowz[0].secondary && rowz[6].secondary)\">\n        " + CURRENT_THEME_TEMPLATE.WEEK_ROW + "\n      </tr>\n    </template>\n  </tbody>\n</table>\n  ",
            directives: [forms_1.FORM_DIRECTIVES, common_1.CORE_DIRECTIVES, common_1.NgClass]
        }), 
        __metadata('design:paramtypes', [datepicker_inner_component_1.DatePickerInnerComponent])
    ], DayPickerComponent);
    return DayPickerComponent;
}());
exports.DayPickerComponent = DayPickerComponent;
var _a;
