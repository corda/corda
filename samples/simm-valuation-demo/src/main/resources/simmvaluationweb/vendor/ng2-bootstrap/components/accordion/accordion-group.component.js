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
var collapse_1 = require('../collapse');
var accordion_component_1 = require('./accordion.component');
/* tslint:disable:component-selector-name */
var AccordionPanelComponent = (function () {
    function AccordionPanelComponent(accordion) {
        this.accordion = accordion;
    }
    Object.defineProperty(AccordionPanelComponent.prototype, "isOpen", {
        // Questionable, maybe .panel-open should be on child div.panel element?
        get: function () {
            return this._isOpen;
        },
        set: function (value) {
            this._isOpen = value;
            if (value) {
                this.accordion.closeOtherPanels(this);
            }
        },
        enumerable: true,
        configurable: true
    });
    AccordionPanelComponent.prototype.ngOnInit = function () {
        this.panelClass = this.panelClass || 'panel-default';
        this.accordion.addGroup(this);
    };
    AccordionPanelComponent.prototype.ngOnDestroy = function () {
        this.accordion.removeGroup(this);
    };
    AccordionPanelComponent.prototype.toggleOpen = function (event) {
        event.preventDefault();
        if (!this.isDisabled) {
            this.isOpen = !this.isOpen;
        }
    };
    __decorate([
        core_1.Input(), 
        __metadata('design:type', String)
    ], AccordionPanelComponent.prototype, "heading", void 0);
    __decorate([
        core_1.Input(), 
        __metadata('design:type', String)
    ], AccordionPanelComponent.prototype, "panelClass", void 0);
    __decorate([
        core_1.Input(), 
        __metadata('design:type', Boolean)
    ], AccordionPanelComponent.prototype, "isDisabled", void 0);
    __decorate([
        core_1.HostBinding('class.panel-open'),
        core_1.Input(), 
        __metadata('design:type', Boolean)
    ], AccordionPanelComponent.prototype, "isOpen", null);
    AccordionPanelComponent = __decorate([
        core_1.Component({
            selector: 'accordion-group, accordion-panel',
            directives: [collapse_1.CollapseDirective, common_1.NgClass],
            template: "\n    <div class=\"panel\" [ngClass]=\"panelClass\">\n      <div class=\"panel-heading\" (click)=\"toggleOpen($event)\">\n        <h4 class=\"panel-title\">\n          <a href tabindex=\"0\" class=\"accordion-toggle\">\n            <span *ngIf=\"heading\" [ngClass]=\"{'text-muted': isDisabled}\">{{heading}}</span>\n            <ng-content select=\"[accordion-heading]\"></ng-content>\n          </a>\n        </h4>\n      </div>\n      <div class=\"panel-collapse collapse\" [collapse]=\"!isOpen\">\n        <div class=\"panel-body\">\n          <ng-content></ng-content>\n        </div>\n      </div>\n    </div>\n  "
        }),
        __param(0, core_1.Inject(accordion_component_1.AccordionComponent)), 
        __metadata('design:paramtypes', [accordion_component_1.AccordionComponent])
    ], AccordionPanelComponent);
    return AccordionPanelComponent;
}());
exports.AccordionPanelComponent = AccordionPanelComponent;
