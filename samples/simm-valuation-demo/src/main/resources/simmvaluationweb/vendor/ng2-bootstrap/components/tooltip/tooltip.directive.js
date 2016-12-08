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
var tooltip_options_class_1 = require('./tooltip-options.class');
var tooltip_container_component_1 = require('./tooltip-container.component');
/* tslint:disable */
var TooltipDirective = (function () {
    function TooltipDirective(viewContainerRef, loader) {
        this.placement = 'top';
        this.enable = true;
        this.animation = true;
        this.visible = false;
        this.viewContainerRef = viewContainerRef;
        this.loader = loader;
    }
    // todo: filter triggers
    // params: event, target
    TooltipDirective.prototype.show = function () {
        if (this.visible || !this.enable) {
            return;
        }
        this.visible = true;
        var options = new tooltip_options_class_1.TooltipOptions({
            content: this.content,
            htmlContent: this.htmlContent,
            placement: this.placement,
            animation: this.animation,
            hostEl: this.viewContainerRef.element,
            popupClass: this.popupClass
        });
        var binding = core_1.ReflectiveInjector.resolve([
            new core_1.Provider(tooltip_options_class_1.TooltipOptions, { useValue: options })
        ]);
        this.tooltip = this.loader
            .loadNextToLocation(tooltip_container_component_1.TooltipContainerComponent, this.viewContainerRef, binding)
            .then(function (componentRef) {
            return componentRef;
        });
    };
    // params event, target
    TooltipDirective.prototype.hide = function () {
        if (!this.visible) {
            return;
        }
        this.visible = false;
        this.tooltip.then(function (componentRef) {
            componentRef.destroy();
            return componentRef;
        });
    };
    __decorate([
        core_1.Input('tooltip'), 
        __metadata('design:type', String)
    ], TooltipDirective.prototype, "content", void 0);
    __decorate([
        core_1.Input('tooltipHtml'), 
        __metadata('design:type', String)
    ], TooltipDirective.prototype, "htmlContent", void 0);
    __decorate([
        core_1.Input('tooltipPlacement'), 
        __metadata('design:type', String)
    ], TooltipDirective.prototype, "placement", void 0);
    __decorate([
        core_1.Input('tooltipIsOpen'), 
        __metadata('design:type', Boolean)
    ], TooltipDirective.prototype, "isOpen", void 0);
    __decorate([
        core_1.Input('tooltipEnable'), 
        __metadata('design:type', Boolean)
    ], TooltipDirective.prototype, "enable", void 0);
    __decorate([
        core_1.Input('tooltipAnimation'), 
        __metadata('design:type', Boolean)
    ], TooltipDirective.prototype, "animation", void 0);
    __decorate([
        core_1.Input('tooltipAppendToBody'), 
        __metadata('design:type', Boolean)
    ], TooltipDirective.prototype, "appendToBody", void 0);
    __decorate([
        core_1.Input('tooltipClass'), 
        __metadata('design:type', String)
    ], TooltipDirective.prototype, "popupClass", void 0);
    __decorate([
        core_1.HostListener('focusin', ['$event', '$target']),
        core_1.HostListener('mouseenter', ['$event', '$target']), 
        __metadata('design:type', Function), 
        __metadata('design:paramtypes', []), 
        __metadata('design:returntype', void 0)
    ], TooltipDirective.prototype, "show", null);
    __decorate([
        core_1.HostListener('focusout', ['$event', '$target']),
        core_1.HostListener('mouseleave', ['$event', '$target']), 
        __metadata('design:type', Function), 
        __metadata('design:paramtypes', []), 
        __metadata('design:returntype', void 0)
    ], TooltipDirective.prototype, "hide", null);
    TooltipDirective = __decorate([
        core_1.Directive({ selector: '[tooltip], [tooltipHtml]' }), 
        __metadata('design:paramtypes', [core_1.ViewContainerRef, core_1.DynamicComponentLoader])
    ], TooltipDirective);
    return TooltipDirective;
}());
exports.TooltipDirective = TooltipDirective;
