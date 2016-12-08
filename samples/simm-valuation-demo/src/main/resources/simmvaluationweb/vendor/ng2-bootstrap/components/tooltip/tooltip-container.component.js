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
var position_1 = require('../position');
var tooltip_options_class_1 = require('./tooltip-options.class');
var TooltipContainerComponent = (function () {
    function TooltipContainerComponent(element, cdr, options) {
        this.top = '-1000px';
        this.left = '-1000px';
        this.display = 'block';
        this.element = element;
        this.cdr = cdr;
        Object.assign(this, options);
        this.classMap = { 'in': false, 'fade': false };
        this.classMap[options.placement] = true;
        this.classMap['tooltip-' + options.placement] = true;
    }
    TooltipContainerComponent.prototype.ngAfterViewInit = function () {
        var p = position_1.positionService
            .positionElements(this.hostEl.nativeElement, this.element.nativeElement.children[0], this.placement, this.appendToBody);
        this.top = p.top + 'px';
        this.left = p.left + 'px';
        this.classMap.in = true;
        if (this.animation) {
            this.classMap.fade = true;
        }
        if (this.popupClass) {
            this.classMap[this.popupClass] = true;
        }
        this.cdr.detectChanges();
    };
    TooltipContainerComponent = __decorate([
        core_1.Component({
            selector: 'tooltip-container',
            directives: [common_1.NgClass, common_1.NgStyle],
            // changeDetection: ChangeDetectionStrategy.OnPush,
            template: "<div class=\"tooltip\" role=\"tooltip\"\n     [ngStyle]=\"{top: top, left: left, display: display}\"\n     [ngClass]=\"classMap\">\n      <div class=\"tooltip-arrow\"></div>\n      <div class=\"tooltip-inner\"\n           *ngIf=\"htmlContent\" \n           innerHtml=\"{{htmlContent}}\">\n      </div>\n      <div class=\"tooltip-inner\"\n           *ngIf=\"content\">\n        {{content}}\n      </div>\n    </div>"
        }),
        __param(2, core_1.Inject(tooltip_options_class_1.TooltipOptions)), 
        __metadata('design:paramtypes', [core_1.ElementRef, core_1.ChangeDetectorRef, tooltip_options_class_1.TooltipOptions])
    ], TooltipContainerComponent);
    return TooltipContainerComponent;
}());
exports.TooltipContainerComponent = TooltipContainerComponent;
