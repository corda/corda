"use strict";
function __export(m) {
    for (var p in m) if (!exports.hasOwnProperty(p)) exports[p] = m[p];
}
var accordion_1 = require('./components/accordion');
var alert_1 = require('./components/alert');
var buttons_1 = require('./components/buttons');
var carousel_1 = require('./components/carousel');
var collapse_1 = require('./components/collapse');
var datepicker_1 = require('./components/datepicker');
var dropdown_1 = require('./components/dropdown');
var modal_1 = require('./components/modal');
var pagination_1 = require('./components/pagination');
var progressbar_1 = require('./components/progressbar');
var rating_1 = require('./components/rating');
var tabs_1 = require('./components/tabs');
var timepicker_1 = require('./components/timepicker');
var tooltip_1 = require('./components/tooltip');
var typeahead_1 = require('./components/typeahead');
var components_helper_service_1 = require('./components/utils/components-helper.service');
__export(require('./components/accordion'));
__export(require('./components/alert'));
__export(require('./components/buttons'));
__export(require('./components/carousel'));
__export(require('./components/collapse'));
__export(require('./components/datepicker'));
__export(require('./components/modal'));
__export(require('./components/dropdown'));
__export(require('./components/pagination'));
__export(require('./components/progressbar'));
__export(require('./components/rating'));
__export(require('./components/tabs'));
__export(require('./components/timepicker'));
__export(require('./components/tooltip'));
__export(require('./components/typeahead'));
__export(require('./components/position'));
__export(require('./components/common'));
__export(require('./components/ng2-bootstrap-config'));
exports.BS_VIEW_PROVIDERS = [{ provide: components_helper_service_1.ComponentsHelper, useClass: components_helper_service_1.ComponentsHelper }];
Object.defineProperty(exports, "__esModule", { value: true });
exports.default = {
    directives: [
        alert_1.AlertComponent,
        accordion_1.ACCORDION_DIRECTIVES,
        buttons_1.BUTTON_DIRECTIVES,
        carousel_1.CAROUSEL_DIRECTIVES,
        collapse_1.CollapseDirective,
        datepicker_1.DATEPICKER_DIRECTIVES,
        dropdown_1.DROPDOWN_DIRECTIVES,
        modal_1.MODAL_DIRECTIVES,
        pagination_1.PAGINATION_DIRECTIVES,
        progressbar_1.PROGRESSBAR_DIRECTIVES,
        rating_1.RatingComponent,
        tabs_1.TAB_DIRECTIVES,
        timepicker_1.TimepickerComponent,
        tooltip_1.TOOLTIP_DIRECTIVES,
        typeahead_1.TYPEAHEAD_DIRECTIVES
    ],
    providers: [
        components_helper_service_1.ComponentsHelper
    ]
};
