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
var PopoverContent = (function () {
    // -------------------------------------------------------------------------
    // Constructor
    // -------------------------------------------------------------------------
    function PopoverContent(element, cdr) {
        var _this = this;
        this.element = element;
        this.cdr = cdr;
        this.placement = "bottom";
        this.animation = true;
        this.closeOnClickOutside = false;
        this.closeOnMouseOutside = false;
        this.onCloseFromOutside = new core_1.EventEmitter();
        this.top = -1000;
        this.left = -1000;
        this.isIn = false;
        this.displayType = "none";
        // -------------------------------------------------------------------------
        // Anonymous 
        // -------------------------------------------------------------------------
        /**
         * Closes dropdown if user clicks outside of this directive.
         */
        this.onDocumentMouseDown = function (event) {
            var element = _this.element.nativeElement;
            if (!element || !_this.hostElement)
                return;
            if (element.contains(event.target) || _this.hostElement.contains(event.target))
                return;
            _this.hide();
            _this.onCloseFromOutside.emit(undefined);
        };
    }
    // -------------------------------------------------------------------------
    // Lifecycle callbacks
    // -------------------------------------------------------------------------
    PopoverContent.prototype.ngAfterViewInit = function () {
        if (this.closeOnClickOutside)
            document.addEventListener("mousedown", this.onDocumentMouseDown);
        if (this.closeOnMouseOutside)
            document.addEventListener("mouseover", this.onDocumentMouseDown);
        this.show();
        this.cdr.detectChanges();
    };
    PopoverContent.prototype.ngOnDestroy = function () {
        if (this.closeOnClickOutside)
            document.removeEventListener("mousedown", this.onDocumentMouseDown);
        if (this.closeOnMouseOutside)
            document.removeEventListener("mouseover", this.onDocumentMouseDown);
    };
    // -------------------------------------------------------------------------
    // Public Methods
    // -------------------------------------------------------------------------
    PopoverContent.prototype.show = function () {
        if (!this.hostElement)
            return;
        var p = this.positionElements(this.hostElement, this.popoverDiv.nativeElement, this.placement);
        this.displayType = "block";
        this.top = p.top;
        this.left = p.left;
        this.isIn = true;
    };
    PopoverContent.prototype.hide = function () {
        this.top = -1000;
        this.left = -1000;
        this.isIn = true;
    };
    // -------------------------------------------------------------------------
    // Private Methods
    // -------------------------------------------------------------------------
    PopoverContent.prototype.positionElements = function (hostEl, targetEl, positionStr, appendToBody) {
        if (appendToBody === void 0) { appendToBody = false; }
        var positionStrParts = positionStr.split("-");
        var pos0 = positionStrParts[0];
        var pos1 = positionStrParts[1] || "center";
        var hostElPos = appendToBody ? this.offset(hostEl) : this.position(hostEl);
        var targetElWidth = targetEl.offsetWidth;
        var targetElHeight = targetEl.offsetHeight;
        var shiftWidth = {
            center: function () {
                return hostElPos.left + hostElPos.width / 2 - targetElWidth / 2;
            },
            left: function () {
                return hostElPos.left;
            },
            right: function () {
                return hostElPos.left + hostElPos.width;
            }
        };
        var shiftHeight = {
            center: function () {
                return hostElPos.top + hostElPos.height / 2 - targetElHeight / 2;
            },
            top: function () {
                return hostElPos.top;
            },
            bottom: function () {
                return hostElPos.top + hostElPos.height;
            }
        };
        var targetElPos;
        switch (pos0) {
            case "right":
                targetElPos = {
                    top: shiftHeight[pos1](),
                    left: shiftWidth[pos0]()
                };
                break;
            case "left":
                targetElPos = {
                    top: shiftHeight[pos1](),
                    left: hostElPos.left - targetElWidth
                };
                break;
            case "bottom":
                targetElPos = {
                    top: shiftHeight[pos0](),
                    left: shiftWidth[pos1]()
                };
                break;
            default:
                targetElPos = {
                    top: hostElPos.top - targetElHeight,
                    left: shiftWidth[pos1]()
                };
                break;
        }
        return targetElPos;
    };
    PopoverContent.prototype.position = function (nativeEl) {
        var offsetParentBCR = { top: 0, left: 0 };
        var elBCR = this.offset(nativeEl);
        var offsetParentEl = this.parentOffsetEl(nativeEl);
        if (offsetParentEl !== window.document) {
            offsetParentBCR = this.offset(offsetParentEl);
            offsetParentBCR.top += offsetParentEl.clientTop - offsetParentEl.scrollTop;
            offsetParentBCR.left += offsetParentEl.clientLeft - offsetParentEl.scrollLeft;
        }
        var boundingClientRect = nativeEl.getBoundingClientRect();
        return {
            width: boundingClientRect.width || nativeEl.offsetWidth,
            height: boundingClientRect.height || nativeEl.offsetHeight,
            top: elBCR.top - offsetParentBCR.top,
            left: elBCR.left - offsetParentBCR.left
        };
    };
    PopoverContent.prototype.offset = function (nativeEl) {
        var boundingClientRect = nativeEl.getBoundingClientRect();
        return {
            width: boundingClientRect.width || nativeEl.offsetWidth,
            height: boundingClientRect.height || nativeEl.offsetHeight,
            top: boundingClientRect.top + (window.pageYOffset || window.document.documentElement.scrollTop),
            left: boundingClientRect.left + (window.pageXOffset || window.document.documentElement.scrollLeft)
        };
    };
    PopoverContent.prototype.getStyle = function (nativeEl, cssProp) {
        if (nativeEl.currentStyle)
            return nativeEl.currentStyle[cssProp];
        if (window.getComputedStyle)
            return window.getComputedStyle(nativeEl)[cssProp];
        // finally try and get inline style
        return nativeEl.style[cssProp];
    };
    PopoverContent.prototype.isStaticPositioned = function (nativeEl) {
        return (this.getStyle(nativeEl, "position") || "static") === "static";
    };
    PopoverContent.prototype.parentOffsetEl = function (nativeEl) {
        var offsetParent = nativeEl.offsetParent || window.document;
        while (offsetParent && offsetParent !== window.document && this.isStaticPositioned(offsetParent)) {
            offsetParent = offsetParent.offsetParent;
        }
        return offsetParent || window.document;
    };
    __decorate([
        core_1.Input(), 
        __metadata('design:type', HTMLElement)
    ], PopoverContent.prototype, "hostElement", void 0);
    __decorate([
        core_1.Input(), 
        __metadata('design:type', String)
    ], PopoverContent.prototype, "content", void 0);
    __decorate([
        core_1.Input(), 
        __metadata('design:type', Object)
    ], PopoverContent.prototype, "placement", void 0);
    __decorate([
        core_1.Input(), 
        __metadata('design:type', String)
    ], PopoverContent.prototype, "title", void 0);
    __decorate([
        core_1.Input(), 
        __metadata('design:type', Boolean)
    ], PopoverContent.prototype, "animation", void 0);
    __decorate([
        core_1.Input(), 
        __metadata('design:type', Boolean)
    ], PopoverContent.prototype, "closeOnClickOutside", void 0);
    __decorate([
        core_1.Input(), 
        __metadata('design:type', Boolean)
    ], PopoverContent.prototype, "closeOnMouseOutside", void 0);
    __decorate([
        core_1.ViewChild("popoverDiv"), 
        __metadata('design:type', core_1.ElementRef)
    ], PopoverContent.prototype, "popoverDiv", void 0);
    PopoverContent = __decorate([
        core_1.Component({
            selector: "popover-content",
            template: "\n<div #popoverDiv class=\"popover {{ placement }}\"\n     [style.top]=\"top + 'px'\"\n     [style.left]=\"left + 'px'\"\n     [class.in]=\"isIn\"\n     [class.fade]=\"animation\"\n     style=\"display: block\"\n     role=\"popover\">\n    <div [hidden]=\"!closeOnMouseOutside\" class=\"virtual-area\"></div>\n    <div class=\"arrow\"></div> \n    <h3 class=\"popover-title\" [hidden]=\"!title\">{{ title }}</h3>\n    <div class=\"popover-content\">\n        <ng-content></ng-content>\n        {{ content }}\n    </div> \n</div>\n",
            styles: ["\n.popover .virtual-area {\n    height: 11px;\n    width: 100%;\n    position: absolute;\n}\n.popover.top .virtual-area {\n    bottom: -11px; \n}\n.popover.bottom .virtual-area {\n    top: -11px; \n}\n.popover.left .virtual-area {\n    right: -11px; \n}\n.popover.right .virtual-area {\n    left: -11px; \n}\n"]
        }), 
        __metadata('design:paramtypes', [core_1.ElementRef, core_1.ChangeDetectorRef])
    ], PopoverContent);
    return PopoverContent;
}());
exports.PopoverContent = PopoverContent;
//# sourceMappingURL=PopoverContent.js.map