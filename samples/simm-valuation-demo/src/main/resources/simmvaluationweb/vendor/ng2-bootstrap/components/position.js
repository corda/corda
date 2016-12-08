"use strict";
var PositionService = (function () {
    function PositionService() {
    }
    /**
     * Provides read-only equivalent of jQuery's position function:
     * http://api.jquery.com/position/
     */
    PositionService.prototype.position = function (nativeEl) {
        var elBCR = this.offset(nativeEl);
        var offsetParentBCR = { top: 0, left: 0 };
        var offsetParentEl = this.parentOffsetEl(nativeEl);
        if (offsetParentEl !== this.document) {
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
    /**
     * Provides read-only equivalent of jQuery's offset function:
     * http://api.jquery.com/offset/
     */
    PositionService.prototype.offset = function (nativeEl) {
        var boundingClientRect = nativeEl.getBoundingClientRect();
        return {
            width: boundingClientRect.width || nativeEl.offsetWidth,
            height: boundingClientRect.height || nativeEl.offsetHeight,
            top: boundingClientRect.top + (this.window.pageYOffset || this.document.documentElement.scrollTop),
            left: boundingClientRect.left + (this.window.pageXOffset || this.document.documentElement.scrollLeft)
        };
    };
    /**
     * Provides coordinates for the targetEl in relation to hostEl
     */
    PositionService.prototype.positionElements = function (hostEl, targetEl, positionStr, appendToBody) {
        var positionStrParts = positionStr.split('-');
        var pos0 = positionStrParts[0];
        var pos1 = positionStrParts[1] || 'center';
        var hostElPos = appendToBody ?
            this.offset(hostEl) :
            this.position(hostEl);
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
            case 'right':
                targetElPos = {
                    top: shiftHeight[pos1](),
                    left: shiftWidth[pos0]()
                };
                break;
            case 'left':
                targetElPos = {
                    top: shiftHeight[pos1](),
                    left: hostElPos.left - targetElWidth
                };
                break;
            case 'bottom':
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
    Object.defineProperty(PositionService.prototype, "window", {
        get: function () {
            return window;
        },
        enumerable: true,
        configurable: true
    });
    Object.defineProperty(PositionService.prototype, "document", {
        get: function () {
            return window.document;
        },
        enumerable: true,
        configurable: true
    });
    PositionService.prototype.getStyle = function (nativeEl, cssProp) {
        // IE
        if (nativeEl.currentStyle) {
            return nativeEl.currentStyle[cssProp];
        }
        if (this.window.getComputedStyle) {
            return this.window.getComputedStyle(nativeEl)[cssProp];
        }
        // finally try and get inline style
        return nativeEl.style[cssProp];
    };
    /**
     * Checks if a given element is statically positioned
     * @param nativeEl - raw DOM element
     */
    PositionService.prototype.isStaticPositioned = function (nativeEl) {
        return (this.getStyle(nativeEl, 'position') || 'static') === 'static';
    };
    /**
     * returns the closest, non-statically positioned parentOffset of a given
     * element
     * @param nativeEl
     */
    PositionService.prototype.parentOffsetEl = function (nativeEl) {
        var offsetParent = nativeEl.offsetParent || this.document;
        while (offsetParent && offsetParent !== this.document &&
            this.isStaticPositioned(offsetParent)) {
            offsetParent = offsetParent.offsetParent;
        }
        return offsetParent || this.document;
    };
    ;
    return PositionService;
}());
exports.PositionService = PositionService;
exports.positionService = new PositionService();
