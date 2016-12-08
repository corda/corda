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
var __param = (this && this.__param) || function (paramIndex, decorator) {
    return function (target, key) { decorator(target, key, paramIndex); }
};
var core_1 = require('@angular/core');
var common_1 = require('@angular/common');
var forms_1 = require('@angular/forms');
var paginationConfig = {
    maxSize: void 0,
    itemsPerPage: 10,
    boundaryLinks: false,
    directionLinks: true,
    firstText: 'First',
    previousText: 'Previous',
    nextText: 'Next',
    lastText: 'Last',
    rotate: true
};
var PAGINATION_TEMPLATE = "\n  <ul class=\"pagination\" [ngClass]=\"classMap\">\n    <li class=\"pagination-first page-item\"\n        *ngIf=\"boundaryLinks\"\n        [class.disabled]=\"noPrevious()||disabled\">\n      <a class=\"page-link\" href (click)=\"selectPage(1, $event)\" [innerHTML]=\"getText('first')\"></a>\n    </li>\n\n    <li class=\"pagination-prev page-item\"\n        *ngIf=\"directionLinks\"\n        [class.disabled]=\"noPrevious()||disabled\">\n      <a class=\"page-link\" href (click)=\"selectPage(page - 1, $event)\" [innerHTML]=\"getText('previous')\"></a>\n      </li>\n\n    <li *ngFor=\"let pg of pages\"\n        [class.active]=\"pg.active\"\n        [class.disabled]=\"disabled&&!pg.active\"\n        class=\"pagination-page page-item\">\n      <a class=\"page-link\" href (click)=\"selectPage(pg.number, $event)\" [innerHTML]=\"pg.text\"></a>\n    </li>\n\n    <li class=\"pagination-next page-item\"\n        *ngIf=\"directionLinks\"\n        [class.disabled]=\"noNext()\">\n      <a class=\"page-link\" href (click)=\"selectPage(page + 1, $event)\" [innerHTML]=\"getText('next')\"></a></li>\n\n    <li class=\"pagination-last page-item\"\n        *ngIf=\"boundaryLinks\"\n        [class.disabled]=\"noNext()\">\n      <a class=\"page-link\" href (click)=\"selectPage(totalPages, $event)\" [innerHTML]=\"getText('last')\"></a></li>\n  </ul>\n  ";
/* tslint:disable */
var PaginationComponent = (function () {
    function PaginationComponent(cd, renderer, elementRef) {
        this.numPages = new core_1.EventEmitter(false);
        this.pageChanged = new core_1.EventEmitter(false);
        this.onChange = Function.prototype;
        this.onTouched = Function.prototype;
        this.inited = false;
        this.cd = cd;
        this.renderer = renderer;
        this.elementRef = elementRef;
        cd.valueAccessor = this;
        this.config = this.config || paginationConfig;
    }
    Object.defineProperty(PaginationComponent.prototype, "itemsPerPage", {
        get: function () {
            return this._itemsPerPage;
        },
        set: function (v) {
            this._itemsPerPage = v;
            this.totalPages = this.calculateTotalPages();
        },
        enumerable: true,
        configurable: true
    });
    Object.defineProperty(PaginationComponent.prototype, "totalItems", {
        get: function () {
            return this._totalItems;
        },
        set: function (v) {
            this._totalItems = v;
            this.totalPages = this.calculateTotalPages();
        },
        enumerable: true,
        configurable: true
    });
    Object.defineProperty(PaginationComponent.prototype, "totalPages", {
        get: function () {
            return this._totalPages;
        },
        set: function (v) {
            this._totalPages = v;
            this.numPages.emit(v);
            if (this.inited) {
                this.selectPage(this.page);
            }
        },
        enumerable: true,
        configurable: true
    });
    Object.defineProperty(PaginationComponent.prototype, "page", {
        get: function () {
            return this._page;
        },
        set: function (value) {
            var _previous = this._page;
            this._page = (value > this.totalPages) ? this.totalPages : (value || 1);
            if (_previous === this._page || typeof _previous === 'undefined') {
                return;
            }
            this.pageChanged.emit({
                page: this._page,
                itemsPerPage: this.itemsPerPage
            });
        },
        enumerable: true,
        configurable: true
    });
    PaginationComponent.prototype.ngOnInit = function () {
        this.classMap = this.elementRef.nativeElement.getAttribute('class') || '';
        // watch for maxSize
        this.maxSize = typeof this.maxSize !== 'undefined'
            ? this.maxSize
            : paginationConfig.maxSize;
        this.rotate = typeof this.rotate !== 'undefined'
            ? this.rotate
            : paginationConfig.rotate;
        this.boundaryLinks = typeof this.boundaryLinks !== 'undefined'
            ? this.boundaryLinks
            : paginationConfig.boundaryLinks;
        this.directionLinks = typeof this.directionLinks !== 'undefined'
            ? this.directionLinks
            : paginationConfig.directionLinks;
        // base class
        this.itemsPerPage = typeof this.itemsPerPage !== 'undefined'
            ? this.itemsPerPage
            : paginationConfig.itemsPerPage;
        this.totalPages = this.calculateTotalPages();
        // this class
        this.pages = this.getPages(this.page, this.totalPages);
        this.page = this.cd.value;
        this.inited = true;
    };
    PaginationComponent.prototype.writeValue = function (value) {
        this.page = value;
        this.pages = this.getPages(this.page, this.totalPages);
    };
    PaginationComponent.prototype.getText = function (key) {
        return this[key + 'Text'] || paginationConfig[key + 'Text'];
    };
    PaginationComponent.prototype.noPrevious = function () {
        return this.page === 1;
    };
    PaginationComponent.prototype.noNext = function () {
        return this.page === this.totalPages;
    };
    PaginationComponent.prototype.registerOnChange = function (fn) { this.onChange = fn; };
    PaginationComponent.prototype.registerOnTouched = function (fn) { this.onTouched = fn; };
    PaginationComponent.prototype.selectPage = function (page, event) {
        if (event) {
            event.preventDefault();
        }
        if (!this.disabled) {
            if (event && event.target) {
                var target = event.target;
                target.blur();
            }
            this.writeValue(page);
            this.cd.viewToModelUpdate(this.page);
        }
    };
    // Create page object used in template
    PaginationComponent.prototype.makePage = function (num, text, isActive) {
        return {
            number: num,
            text: text,
            active: isActive
        };
    };
    PaginationComponent.prototype.getPages = function (currentPage, totalPages) {
        var pages = [];
        // Default page limits
        var startPage = 1;
        var endPage = totalPages;
        var isMaxSized = typeof this.maxSize !== 'undefined' && this.maxSize < totalPages;
        // recompute if maxSize
        if (isMaxSized) {
            if (this.rotate) {
                // Current page is displayed in the middle of the visible ones
                startPage = Math.max(currentPage - Math.floor(this.maxSize / 2), 1);
                endPage = startPage + this.maxSize - 1;
                // Adjust if limit is exceeded
                if (endPage > totalPages) {
                    endPage = totalPages;
                    startPage = endPage - this.maxSize + 1;
                }
            }
            else {
                // Visible pages are paginated with maxSize
                startPage = ((Math.ceil(currentPage / this.maxSize) - 1) * this.maxSize) + 1;
                // Adjust last page if limit is exceeded
                endPage = Math.min(startPage + this.maxSize - 1, totalPages);
            }
        }
        // Add page number links
        for (var num = startPage; num <= endPage; num++) {
            var page = this.makePage(num, num.toString(), num === currentPage);
            pages.push(page);
        }
        // Add links to move between page sets
        if (isMaxSized && !this.rotate) {
            if (startPage > 1) {
                var previousPageSet = this.makePage(startPage - 1, '...', false);
                pages.unshift(previousPageSet);
            }
            if (endPage < totalPages) {
                var nextPageSet = this.makePage(endPage + 1, '...', false);
                pages.push(nextPageSet);
            }
        }
        return pages;
    };
    // base class
    PaginationComponent.prototype.calculateTotalPages = function () {
        var totalPages = this.itemsPerPage < 1
            ? 1
            : Math.ceil(this.totalItems / this.itemsPerPage);
        return Math.max(totalPages || 0, 1);
    };
    __decorate([
        core_1.Input(), 
        __metadata('design:type', Number)
    ], PaginationComponent.prototype, "maxSize", void 0);
    __decorate([
        core_1.Input(), 
        __metadata('design:type', Boolean)
    ], PaginationComponent.prototype, "boundaryLinks", void 0);
    __decorate([
        core_1.Input(), 
        __metadata('design:type', Boolean)
    ], PaginationComponent.prototype, "directionLinks", void 0);
    __decorate([
        core_1.Input(), 
        __metadata('design:type', String)
    ], PaginationComponent.prototype, "firstText", void 0);
    __decorate([
        core_1.Input(), 
        __metadata('design:type', String)
    ], PaginationComponent.prototype, "previousText", void 0);
    __decorate([
        core_1.Input(), 
        __metadata('design:type', String)
    ], PaginationComponent.prototype, "nextText", void 0);
    __decorate([
        core_1.Input(), 
        __metadata('design:type', String)
    ], PaginationComponent.prototype, "lastText", void 0);
    __decorate([
        core_1.Input(), 
        __metadata('design:type', Boolean)
    ], PaginationComponent.prototype, "rotate", void 0);
    __decorate([
        core_1.Input(), 
        __metadata('design:type', Boolean)
    ], PaginationComponent.prototype, "disabled", void 0);
    __decorate([
        core_1.Output(), 
        __metadata('design:type', core_1.EventEmitter)
    ], PaginationComponent.prototype, "numPages", void 0);
    __decorate([
        core_1.Output(), 
        __metadata('design:type', core_1.EventEmitter)
    ], PaginationComponent.prototype, "pageChanged", void 0);
    __decorate([
        core_1.Input(), 
        __metadata('design:type', Number)
    ], PaginationComponent.prototype, "itemsPerPage", null);
    __decorate([
        core_1.Input(), 
        __metadata('design:type', Number)
    ], PaginationComponent.prototype, "totalItems", null);
    PaginationComponent = __decorate([
        core_1.Component({
            selector: 'pagination[ngModel]',
            template: PAGINATION_TEMPLATE,
            directives: [common_1.NgFor, common_1.NgIf]
        }),
        __param(0, core_1.Self()), 
        __metadata('design:paramtypes', [forms_1.NgModel, core_1.Renderer, core_1.ElementRef])
    ], PaginationComponent);
    return PaginationComponent;
}());
exports.PaginationComponent = PaginationComponent;
