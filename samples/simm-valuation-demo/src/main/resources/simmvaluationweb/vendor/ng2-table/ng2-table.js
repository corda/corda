"use strict";
function __export(m) {
    for (var p in m) if (!exports.hasOwnProperty(p)) exports[p] = m[p];
}
var ng_table_component_1 = require('./components/table/ng-table.component');
var ng_table_filtering_directive_1 = require('./components/table/ng-table-filtering.directive');
var ng_table_paging_directive_1 = require('./components/table/ng-table-paging.directive');
var ng_table_sorting_directive_1 = require('./components/table/ng-table-sorting.directive');
__export(require('./components/table/ng-table.component'));
__export(require('./components/table/ng-table-filtering.directive'));
__export(require('./components/table/ng-table-paging.directive'));
__export(require('./components/table/ng-table-sorting.directive'));
__export(require('./components/ng-table-directives'));
Object.defineProperty(exports, "__esModule", { value: true });
exports.default = {
    directives: [
        ng_table_component_1.NgTableComponent,
        ng_table_filtering_directive_1.NgTableFilteringDirective,
        ng_table_sorting_directive_1.NgTableSortingDirective,
        ng_table_paging_directive_1.NgTablePagingDirective
    ]
};
