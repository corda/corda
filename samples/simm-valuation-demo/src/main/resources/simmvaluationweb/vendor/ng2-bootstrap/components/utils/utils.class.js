"use strict";
var lang_1 = require('@angular/core/src/facade/lang');
var Utils = (function () {
    function Utils() {
    }
    Utils.reflow = function (element) {
        new Function('bs', 'return bs')(element.offsetHeight);
    };
    // source: https://github.com/jquery/jquery/blob/master/src/css/var/getStyles.js
    Utils.getStyles = function (elem) {
        // Support: IE <=11 only, Firefox <=30 (#15098, #14150)
        // IE throws on elements created in popups
        // FF meanwhile throws on frame elements through "defaultView.getComputedStyle"
        var view = elem.ownerDocument.defaultView;
        if (!view || !view.opener) {
            view = lang_1.global;
        }
        return view.getComputedStyle(elem);
    };
    return Utils;
}());
exports.Utils = Utils;
