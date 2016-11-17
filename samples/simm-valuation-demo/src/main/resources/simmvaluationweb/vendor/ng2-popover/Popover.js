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
var core_1 = require("@angular/core");
var PopoverContent_1 = require("./PopoverContent");
var Popover = (function () {
    // -------------------------------------------------------------------------
    // Constructor
    // -------------------------------------------------------------------------
    function Popover(viewContainerRef, resolver) {
        this.viewContainerRef = viewContainerRef;
        this.resolver = resolver;
        this.popoverAnimation = true;
        this.popoverPlacement = "bottom";
        this.popoverOnHover = false;
        this.popoverCloseOnClickOutside = false;
        this.popoverCloseOnMouseOutside = false;
        this.popoverDismissTimeout = 0;
    }
    // -------------------------------------------------------------------------
    // Public Methods
    // -------------------------------------------------------------------------
    Popover.prototype.showOrHideOnClick = function () {
        if (this.popoverOnHover)
            return;
        if (this.popoverDisabled)
            return;
        if (!this.visible) {
            this.show();
        }
        else {
            this.hide();
        }
    };
    Popover.prototype.showOnHover = function () {
        if (!this.popoverOnHover)
            return;
        if (this.popoverDisabled)
            return;
        if (this.visible)
            return;
        this.show();
    };
    Popover.prototype.hideOnHover = function () {
        if (this.popoverCloseOnMouseOutside)
            return; // don't do anything since not we control this
        if (!this.popoverOnHover)
            return;
        if (this.popoverDisabled)
            return;
        if (!this.visible)
            return;
        this.hide();
    };
    // -------------------------------------------------------------------------
    // Private Methods
    // -------------------------------------------------------------------------
    Popover.prototype.show = function () {
        var _this = this;
        this.visible = true;
        if (typeof this.content === "string") {
            this.resolver.resolveComponent(PopoverContent_1.PopoverContent).then(function (factory) {
                if (!_this.visible)
                    return;
                _this.popover = _this.viewContainerRef.createComponent(factory);
                var popover = _this.popover.instance;
                popover.hostElement = _this.viewContainerRef.element.nativeElement;
                popover.content = _this.content;
                if (_this.popoverPlacement !== undefined)
                    popover.placement = _this.popoverPlacement;
                if (_this.popoverAnimation !== undefined)
                    popover.animation = _this.popoverAnimation;
                if (_this.popoverTitle !== undefined)
                    popover.title = _this.popoverTitle;
                if (_this.popoverCloseOnClickOutside !== undefined)
                    popover.closeOnClickOutside = _this.popoverCloseOnClickOutside;
                if (_this.popoverCloseOnMouseOutside !== undefined)
                    popover.closeOnMouseOutside = _this.popoverCloseOnMouseOutside;
                popover.onCloseFromOutside.subscribe(function () { return _this.hide(); });
                // if dismissTimeout option is set, then this popover will be dismissed in dismissTimeout time
                if (_this.popoverDismissTimeout > 0)
                    setTimeout(function () { return _this.hide(); }, _this.popoverDismissTimeout);
            });
        }
        else {
            var popover = this.content;
            popover.hostElement = this.viewContainerRef.element.nativeElement;
            if (this.popoverPlacement !== undefined)
                popover.placement = this.popoverPlacement;
            if (this.popoverAnimation !== undefined)
                popover.animation = this.popoverAnimation;
            if (this.popoverTitle !== undefined)
                popover.title = this.popoverTitle;
            if (this.popoverCloseOnClickOutside !== undefined)
                popover.closeOnClickOutside = this.popoverCloseOnClickOutside;
            if (this.popoverCloseOnMouseOutside !== undefined)
                popover.closeOnMouseOutside = this.popoverCloseOnMouseOutside;
            popover.onCloseFromOutside.subscribe(function () { return _this.hide(); });
            // if dismissTimeout option is set, then this popover will be dismissed in dismissTimeout time
            if (this.popoverDismissTimeout > 0)
                setTimeout(function () { return _this.hide(); }, this.popoverDismissTimeout);
            popover.show();
        }
    };
    Popover.prototype.hide = function () {
        this.visible = false;
        if (this.popover)
            this.popover.destroy();
        if (this.content instanceof PopoverContent_1.PopoverContent)
            this.content.hide();
    };
    __decorate([
        core_1.Input("popover"), 
        __metadata('design:type', Object)
    ], Popover.prototype, "content", void 0);
    __decorate([
        core_1.Input(), 
        __metadata('design:type', Boolean)
    ], Popover.prototype, "popoverDisabled", void 0);
    __decorate([
        core_1.Input(), 
        __metadata('design:type', Boolean)
    ], Popover.prototype, "popoverAnimation", void 0);
    __decorate([
        core_1.Input(), 
        __metadata('design:type', Object)
    ], Popover.prototype, "popoverPlacement", void 0);
    __decorate([
        core_1.Input(), 
        __metadata('design:type', String)
    ], Popover.prototype, "popoverTitle", void 0);
    __decorate([
        core_1.Input(), 
        __metadata('design:type', Boolean)
    ], Popover.prototype, "popoverOnHover", void 0);
    __decorate([
        core_1.Input(), 
        __metadata('design:type', Boolean)
    ], Popover.prototype, "popoverCloseOnClickOutside", void 0);
    __decorate([
        core_1.Input(), 
        __metadata('design:type', Boolean)
    ], Popover.prototype, "popoverCloseOnMouseOutside", void 0);
    __decorate([
        core_1.Input(), 
        __metadata('design:type', Number)
    ], Popover.prototype, "popoverDismissTimeout", void 0);
    __decorate([
        core_1.HostListener("click"), 
        __metadata('design:type', Function), 
        __metadata('design:paramtypes', []), 
        __metadata('design:returntype', void 0)
    ], Popover.prototype, "showOrHideOnClick", null);
    __decorate([
        core_1.HostListener("focusin"),
        core_1.HostListener("mouseenter"), 
        __metadata('design:type', Function), 
        __metadata('design:paramtypes', []), 
        __metadata('design:returntype', void 0)
    ], Popover.prototype, "showOnHover", null);
    __decorate([
        core_1.HostListener("focusout"),
        core_1.HostListener("mouseleave"), 
        __metadata('design:type', Function), 
        __metadata('design:paramtypes', []), 
        __metadata('design:returntype', void 0)
    ], Popover.prototype, "hideOnHover", null);
    Popover = __decorate([
        core_1.Directive({
            selector: "[popover]"
        }), 
        __metadata('design:paramtypes', [core_1.ViewContainerRef, core_1.ComponentResolver])
    ], Popover);
    return Popover;
}());
exports.Popover = Popover;
//# sourceMappingURL=Popover.js.map