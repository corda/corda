System.registerDynamic("ng2-table/components/table/ng-table.component", ["@angular/core", "@angular/common", "./ng-table-sorting.directive"], true, function($__require, exports, module) {
  "use strict";
  ;
  var define,
      global = this,
      GLOBAL = this;
  var __decorate = (this && this.__decorate) || function(decorators, target, key, desc) {
    var c = arguments.length,
        r = c < 3 ? target : desc === null ? desc = Object.getOwnPropertyDescriptor(target, key) : desc,
        d;
    if (typeof Reflect === "object" && typeof Reflect.decorate === "function")
      r = Reflect.decorate(decorators, target, key, desc);
    else
      for (var i = decorators.length - 1; i >= 0; i--)
        if (d = decorators[i])
          r = (c < 3 ? d(r) : c > 3 ? d(target, key, r) : d(target, key)) || r;
    return c > 3 && r && Object.defineProperty(target, key, r), r;
  };
  var __metadata = (this && this.__metadata) || function(k, v) {
    if (typeof Reflect === "object" && typeof Reflect.metadata === "function")
      return Reflect.metadata(k, v);
  };
  var core_1 = $__require('@angular/core');
  var common_1 = $__require('@angular/common');
  var ng_table_sorting_directive_1 = $__require('./ng-table-sorting.directive');
  var NgTableComponent = (function() {
    function NgTableComponent() {
      this.rows = [];
      this.config = {};
      this.tableChanged = new core_1.EventEmitter();
      this._columns = [];
    }
    Object.defineProperty(NgTableComponent.prototype, "columns", {
      get: function() {
        return this._columns;
      },
      set: function(values) {
        var _this = this;
        values.forEach(function(value) {
          var column = _this._columns.find(function(col) {
            return col.name === value.name;
          });
          if (column) {
            Object.assign(column, value);
          }
          if (!column) {
            _this._columns.push(value);
          }
        });
      },
      enumerable: true,
      configurable: true
    });
    Object.defineProperty(NgTableComponent.prototype, "configColumns", {
      get: function() {
        var sortColumns = [];
        this.columns.forEach(function(column) {
          if (column.sort) {
            sortColumns.push(column);
          }
        });
        return {columns: sortColumns};
      },
      enumerable: true,
      configurable: true
    });
    NgTableComponent.prototype.onChangeTable = function(column) {
      this._columns.forEach(function(col) {
        if (col.name !== column.name) {
          col.sort = '';
        }
      });
      this.tableChanged.emit({sorting: this.configColumns});
    };
    NgTableComponent.prototype.getData = function(row, propertyName) {
      return propertyName.split('.').reduce(function(prev, curr) {
        return prev[curr];
      }, row);
    };
    __decorate([core_1.Input(), __metadata('design:type', Array)], NgTableComponent.prototype, "rows", void 0);
    __decorate([core_1.Input(), __metadata('design:type', Object)], NgTableComponent.prototype, "config", void 0);
    __decorate([core_1.Output(), __metadata('design:type', core_1.EventEmitter)], NgTableComponent.prototype, "tableChanged", void 0);
    __decorate([core_1.Input(), __metadata('design:type', Array), __metadata('design:paramtypes', [Array])], NgTableComponent.prototype, "columns", null);
    NgTableComponent = __decorate([core_1.Component({
      selector: 'ng-table',
      template: "\n    <table class=\"table table-striped table-bordered dataTable\"\n           role=\"grid\" style=\"width: 100%;\">\n      <thead>\n      <tr role=\"row\">\n        <th *ngFor=\"let column of columns\" [ngTableSorting]=\"config\" [column]=\"column\" (sortChanged)=\"onChangeTable($event)\">\n          {{column.title}}\n          <i *ngIf=\"config && column.sort\" class=\"pull-right fa\"\n            [ngClass]=\"{'fa-chevron-down': column.sort === 'desc', 'fa-chevron-up': column.sort === 'asc'}\"></i>\n        </th>\n      </tr>\n      </thead>\n      <tbody>\n      <tr *ngFor=\"let row of rows\">\n        <td *ngFor=\"let column of columns\">{{getData(row, column.name)}}</td>\n      </tr>\n      </tbody>\n    </table>\n",
      directives: [ng_table_sorting_directive_1.NgTableSortingDirective, common_1.NgClass, common_1.CORE_DIRECTIVES]
    }), __metadata('design:paramtypes', [])], NgTableComponent);
    return NgTableComponent;
  }());
  exports.NgTableComponent = NgTableComponent;
  return module.exports;
});

System.registerDynamic("ng2-table/components/table/ng-table-filtering.directive", ["@angular/core"], true, function($__require, exports, module) {
  "use strict";
  ;
  var define,
      global = this,
      GLOBAL = this;
  var __decorate = (this && this.__decorate) || function(decorators, target, key, desc) {
    var c = arguments.length,
        r = c < 3 ? target : desc === null ? desc = Object.getOwnPropertyDescriptor(target, key) : desc,
        d;
    if (typeof Reflect === "object" && typeof Reflect.decorate === "function")
      r = Reflect.decorate(decorators, target, key, desc);
    else
      for (var i = decorators.length - 1; i >= 0; i--)
        if (d = decorators[i])
          r = (c < 3 ? d(r) : c > 3 ? d(target, key, r) : d(target, key)) || r;
    return c > 3 && r && Object.defineProperty(target, key, r), r;
  };
  var __metadata = (this && this.__metadata) || function(k, v) {
    if (typeof Reflect === "object" && typeof Reflect.metadata === "function")
      return Reflect.metadata(k, v);
  };
  var core_1 = $__require('@angular/core');
  function setProperty(renderer, elementRef, propName, propValue) {
    renderer.setElementProperty(elementRef, propName, propValue);
  }
  var NgTableFilteringDirective = (function() {
    function NgTableFilteringDirective(element, renderer) {
      this.ngTableFiltering = {
        filterString: '',
        columnName: 'name'
      };
      this.tableChanged = new core_1.EventEmitter();
      this.element = element;
      this.renderer = renderer;
      setProperty(this.renderer, this.element, 'value', this.ngTableFiltering.filterString);
    }
    Object.defineProperty(NgTableFilteringDirective.prototype, "config", {
      get: function() {
        return this.ngTableFiltering;
      },
      set: function(value) {
        this.ngTableFiltering = value;
      },
      enumerable: true,
      configurable: true
    });
    NgTableFilteringDirective.prototype.onChangeFilter = function(event) {
      this.ngTableFiltering.filterString = event;
      this.tableChanged.emit({filtering: this.ngTableFiltering});
    };
    __decorate([core_1.Input(), __metadata('design:type', Object)], NgTableFilteringDirective.prototype, "ngTableFiltering", void 0);
    __decorate([core_1.Output(), __metadata('design:type', core_1.EventEmitter)], NgTableFilteringDirective.prototype, "tableChanged", void 0);
    __decorate([core_1.Input(), __metadata('design:type', Object)], NgTableFilteringDirective.prototype, "config", null);
    __decorate([core_1.HostListener('input', ['$event.target.value']), __metadata('design:type', Function), __metadata('design:paramtypes', [Object]), __metadata('design:returntype', void 0)], NgTableFilteringDirective.prototype, "onChangeFilter", null);
    NgTableFilteringDirective = __decorate([core_1.Directive({selector: '[ngTableFiltering]'}), __metadata('design:paramtypes', [core_1.ElementRef, core_1.Renderer])], NgTableFilteringDirective);
    return NgTableFilteringDirective;
  }());
  exports.NgTableFilteringDirective = NgTableFilteringDirective;
  return module.exports;
});

System.registerDynamic("ng2-table/components/table/ng-table-paging.directive", ["@angular/core"], true, function($__require, exports, module) {
  "use strict";
  ;
  var define,
      global = this,
      GLOBAL = this;
  var __decorate = (this && this.__decorate) || function(decorators, target, key, desc) {
    var c = arguments.length,
        r = c < 3 ? target : desc === null ? desc = Object.getOwnPropertyDescriptor(target, key) : desc,
        d;
    if (typeof Reflect === "object" && typeof Reflect.decorate === "function")
      r = Reflect.decorate(decorators, target, key, desc);
    else
      for (var i = decorators.length - 1; i >= 0; i--)
        if (d = decorators[i])
          r = (c < 3 ? d(r) : c > 3 ? d(target, key, r) : d(target, key)) || r;
    return c > 3 && r && Object.defineProperty(target, key, r), r;
  };
  var __metadata = (this && this.__metadata) || function(k, v) {
    if (typeof Reflect === "object" && typeof Reflect.metadata === "function")
      return Reflect.metadata(k, v);
  };
  var core_1 = $__require('@angular/core');
  var NgTablePagingDirective = (function() {
    function NgTablePagingDirective() {
      this.ngTablePaging = true;
      this.tableChanged = new core_1.EventEmitter();
    }
    Object.defineProperty(NgTablePagingDirective.prototype, "config", {
      get: function() {
        return this.ngTablePaging;
      },
      set: function(value) {
        this.ngTablePaging = value;
      },
      enumerable: true,
      configurable: true
    });
    NgTablePagingDirective.prototype.onChangePage = function(event) {
      if (this.ngTablePaging) {
        this.tableChanged.emit({paging: event});
      }
    };
    __decorate([core_1.Input(), __metadata('design:type', Boolean)], NgTablePagingDirective.prototype, "ngTablePaging", void 0);
    __decorate([core_1.Output(), __metadata('design:type', core_1.EventEmitter)], NgTablePagingDirective.prototype, "tableChanged", void 0);
    __decorate([core_1.Input(), __metadata('design:type', Object)], NgTablePagingDirective.prototype, "config", null);
    __decorate([core_1.HostListener('pagechanged', ['$event']), __metadata('design:type', Function), __metadata('design:paramtypes', [Object]), __metadata('design:returntype', void 0)], NgTablePagingDirective.prototype, "onChangePage", null);
    NgTablePagingDirective = __decorate([core_1.Directive({selector: '[ngTablePaging]'}), __metadata('design:paramtypes', [])], NgTablePagingDirective);
    return NgTablePagingDirective;
  }());
  exports.NgTablePagingDirective = NgTablePagingDirective;
  return module.exports;
});

System.registerDynamic("ng2-table/components/table/ng-table-sorting.directive", ["@angular/core"], true, function($__require, exports, module) {
  "use strict";
  ;
  var define,
      global = this,
      GLOBAL = this;
  var __decorate = (this && this.__decorate) || function(decorators, target, key, desc) {
    var c = arguments.length,
        r = c < 3 ? target : desc === null ? desc = Object.getOwnPropertyDescriptor(target, key) : desc,
        d;
    if (typeof Reflect === "object" && typeof Reflect.decorate === "function")
      r = Reflect.decorate(decorators, target, key, desc);
    else
      for (var i = decorators.length - 1; i >= 0; i--)
        if (d = decorators[i])
          r = (c < 3 ? d(r) : c > 3 ? d(target, key, r) : d(target, key)) || r;
    return c > 3 && r && Object.defineProperty(target, key, r), r;
  };
  var __metadata = (this && this.__metadata) || function(k, v) {
    if (typeof Reflect === "object" && typeof Reflect.metadata === "function")
      return Reflect.metadata(k, v);
  };
  var core_1 = $__require('@angular/core');
  var NgTableSortingDirective = (function() {
    function NgTableSortingDirective() {
      this.sortChanged = new core_1.EventEmitter();
    }
    Object.defineProperty(NgTableSortingDirective.prototype, "config", {
      get: function() {
        return this.ngTableSorting;
      },
      set: function(value) {
        this.ngTableSorting = value;
      },
      enumerable: true,
      configurable: true
    });
    NgTableSortingDirective.prototype.onToggleSort = function(event) {
      if (event) {
        event.preventDefault();
      }
      if (this.ngTableSorting && this.column && this.column.sort !== false) {
        switch (this.column.sort) {
          case 'asc':
            this.column.sort = 'desc';
            break;
          case 'desc':
            this.column.sort = '';
            break;
          default:
            this.column.sort = 'asc';
            break;
        }
        this.sortChanged.emit(this.column);
      }
    };
    __decorate([core_1.Input(), __metadata('design:type', Object)], NgTableSortingDirective.prototype, "ngTableSorting", void 0);
    __decorate([core_1.Input(), __metadata('design:type', Object)], NgTableSortingDirective.prototype, "column", void 0);
    __decorate([core_1.Output(), __metadata('design:type', core_1.EventEmitter)], NgTableSortingDirective.prototype, "sortChanged", void 0);
    __decorate([core_1.Input(), __metadata('design:type', Object)], NgTableSortingDirective.prototype, "config", null);
    __decorate([core_1.HostListener('click', ['$event', '$target']), __metadata('design:type', Function), __metadata('design:paramtypes', [Object]), __metadata('design:returntype', void 0)], NgTableSortingDirective.prototype, "onToggleSort", null);
    NgTableSortingDirective = __decorate([core_1.Directive({selector: '[ngTableSorting]'}), __metadata('design:paramtypes', [])], NgTableSortingDirective);
    return NgTableSortingDirective;
  }());
  exports.NgTableSortingDirective = NgTableSortingDirective;
  return module.exports;
});

System.registerDynamic("ng2-table/components/ng-table-directives", ["./table/ng-table.component", "./table/ng-table-filtering.directive", "./table/ng-table-paging.directive", "./table/ng-table-sorting.directive"], true, function($__require, exports, module) {
  "use strict";
  ;
  var define,
      global = this,
      GLOBAL = this;
  var ng_table_component_1 = $__require('./table/ng-table.component');
  var ng_table_filtering_directive_1 = $__require('./table/ng-table-filtering.directive');
  var ng_table_paging_directive_1 = $__require('./table/ng-table-paging.directive');
  var ng_table_sorting_directive_1 = $__require('./table/ng-table-sorting.directive');
  exports.NG_TABLE_DIRECTIVES = [ng_table_component_1.NgTableComponent, ng_table_filtering_directive_1.NgTableFilteringDirective, ng_table_paging_directive_1.NgTablePagingDirective, ng_table_sorting_directive_1.NgTableSortingDirective];
  return module.exports;
});

System.registerDynamic("ng2-table/ng2-table", ["./components/table/ng-table.component", "./components/table/ng-table-filtering.directive", "./components/table/ng-table-paging.directive", "./components/table/ng-table-sorting.directive", "./components/ng-table-directives"], true, function($__require, exports, module) {
  "use strict";
  ;
  var define,
      global = this,
      GLOBAL = this;
  function __export(m) {
    for (var p in m)
      if (!exports.hasOwnProperty(p))
        exports[p] = m[p];
  }
  var ng_table_component_1 = $__require('./components/table/ng-table.component');
  var ng_table_filtering_directive_1 = $__require('./components/table/ng-table-filtering.directive');
  var ng_table_paging_directive_1 = $__require('./components/table/ng-table-paging.directive');
  var ng_table_sorting_directive_1 = $__require('./components/table/ng-table-sorting.directive');
  __export($__require('./components/table/ng-table.component'));
  __export($__require('./components/table/ng-table-filtering.directive'));
  __export($__require('./components/table/ng-table-paging.directive'));
  __export($__require('./components/table/ng-table-sorting.directive'));
  __export($__require('./components/ng-table-directives'));
  Object.defineProperty(exports, "__esModule", {value: true});
  exports.default = {directives: [ng_table_component_1.NgTableComponent, ng_table_filtering_directive_1.NgTableFilteringDirective, ng_table_sorting_directive_1.NgTableSortingDirective, ng_table_paging_directive_1.NgTablePagingDirective]};
  return module.exports;
});

//# sourceMappingURL=ng2-table.js.map