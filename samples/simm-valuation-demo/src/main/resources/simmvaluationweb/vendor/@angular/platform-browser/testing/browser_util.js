/**
 * @license
 * Copyright Google Inc. All Rights Reserved.
 *
 * Use of this source code is governed by an MIT-style license that can be
 * found in the LICENSE file at https://angular.io/license
 */
"use strict";
var dom_adapter_1 = require('../src/dom/dom_adapter');
var collection_1 = require('../src/facade/collection');
var lang_1 = require('../src/facade/lang');
var BrowserDetection = (function () {
    function BrowserDetection(ua) {
        this._overrideUa = ua;
    }
    Object.defineProperty(BrowserDetection.prototype, "_ua", {
        get: function () {
            if (lang_1.isPresent(this._overrideUa)) {
                return this._overrideUa;
            }
            else {
                return lang_1.isPresent(dom_adapter_1.getDOM()) ? dom_adapter_1.getDOM().getUserAgent() : '';
            }
        },
        enumerable: true,
        configurable: true
    });
    BrowserDetection.setup = function () { exports.browserDetection = new BrowserDetection(null); };
    Object.defineProperty(BrowserDetection.prototype, "isFirefox", {
        get: function () { return this._ua.indexOf('Firefox') > -1; },
        enumerable: true,
        configurable: true
    });
    Object.defineProperty(BrowserDetection.prototype, "isAndroid", {
        get: function () {
            return this._ua.indexOf('Mozilla/5.0') > -1 && this._ua.indexOf('Android') > -1 &&
                this._ua.indexOf('AppleWebKit') > -1 && this._ua.indexOf('Chrome') == -1;
        },
        enumerable: true,
        configurable: true
    });
    Object.defineProperty(BrowserDetection.prototype, "isEdge", {
        get: function () { return this._ua.indexOf('Edge') > -1; },
        enumerable: true,
        configurable: true
    });
    Object.defineProperty(BrowserDetection.prototype, "isIE", {
        get: function () { return this._ua.indexOf('Trident') > -1; },
        enumerable: true,
        configurable: true
    });
    Object.defineProperty(BrowserDetection.prototype, "isWebkit", {
        get: function () {
            return this._ua.indexOf('AppleWebKit') > -1 && this._ua.indexOf('Edge') == -1;
        },
        enumerable: true,
        configurable: true
    });
    Object.defineProperty(BrowserDetection.prototype, "isIOS7", {
        get: function () {
            return this._ua.indexOf('iPhone OS 7') > -1 || this._ua.indexOf('iPad OS 7') > -1;
        },
        enumerable: true,
        configurable: true
    });
    Object.defineProperty(BrowserDetection.prototype, "isSlow", {
        get: function () { return this.isAndroid || this.isIE || this.isIOS7; },
        enumerable: true,
        configurable: true
    });
    Object.defineProperty(BrowserDetection.prototype, "supportsIntlApi", {
        // The Intl API is only properly supported in recent Chrome and Opera.
        // Note: Edge is disguised as Chrome 42, so checking the "Edge" part is needed,
        // see https://msdn.microsoft.com/en-us/library/hh869301(v=vs.85).aspx
        get: function () { return !!lang_1.global.Intl; },
        enumerable: true,
        configurable: true
    });
    Object.defineProperty(BrowserDetection.prototype, "isChromeDesktop", {
        get: function () {
            return this._ua.indexOf('Chrome') > -1 && this._ua.indexOf('Mobile Safari') == -1 &&
                this._ua.indexOf('Edge') == -1;
        },
        enumerable: true,
        configurable: true
    });
    return BrowserDetection;
}());
exports.BrowserDetection = BrowserDetection;
BrowserDetection.setup();
function dispatchEvent(element /** TODO #9100 */, eventType /** TODO #9100 */) {
    dom_adapter_1.getDOM().dispatchEvent(element, dom_adapter_1.getDOM().createEvent(eventType));
}
exports.dispatchEvent = dispatchEvent;
function el(html) {
    return dom_adapter_1.getDOM().firstChild(dom_adapter_1.getDOM().content(dom_adapter_1.getDOM().createTemplate(html)));
}
exports.el = el;
function normalizeCSS(css) {
    css = lang_1.StringWrapper.replaceAll(css, /\s+/g, ' ');
    css = lang_1.StringWrapper.replaceAll(css, /:\s/g, ':');
    css = lang_1.StringWrapper.replaceAll(css, /'/g, '"');
    css = lang_1.StringWrapper.replaceAll(css, / }/g, '}');
    css = lang_1.StringWrapper.replaceAllMapped(css, /url\((\"|\s)(.+)(\"|\s)\)(\s*)/g, function (match /** TODO #9100 */) { return ("url(\"" + match[2] + "\")"); });
    css = lang_1.StringWrapper.replaceAllMapped(css, /\[(.+)=([^"\]]+)\]/g, function (match /** TODO #9100 */) { return ("[" + match[1] + "=\"" + match[2] + "\"]"); });
    return css;
}
exports.normalizeCSS = normalizeCSS;
var _singleTagWhitelist = ['br', 'hr', 'input'];
function stringifyElement(el /** TODO #9100 */) {
    var result = '';
    if (dom_adapter_1.getDOM().isElementNode(el)) {
        var tagName = dom_adapter_1.getDOM().tagName(el).toLowerCase();
        // Opening tag
        result += "<" + tagName;
        // Attributes in an ordered way
        var attributeMap = dom_adapter_1.getDOM().attributeMap(el);
        var keys = [];
        attributeMap.forEach(function (v, k) { return keys.push(k); });
        collection_1.ListWrapper.sort(keys);
        for (var i = 0; i < keys.length; i++) {
            var key = keys[i];
            var attValue = attributeMap.get(key);
            if (!lang_1.isString(attValue)) {
                result += " " + key;
            }
            else {
                result += " " + key + "=\"" + attValue + "\"";
            }
        }
        result += '>';
        // Children
        var childrenRoot = dom_adapter_1.getDOM().templateAwareRoot(el);
        var children = lang_1.isPresent(childrenRoot) ? dom_adapter_1.getDOM().childNodes(childrenRoot) : [];
        for (var j = 0; j < children.length; j++) {
            result += stringifyElement(children[j]);
        }
        // Closing tag
        if (!collection_1.ListWrapper.contains(_singleTagWhitelist, tagName)) {
            result += "</" + tagName + ">";
        }
    }
    else if (dom_adapter_1.getDOM().isCommentNode(el)) {
        result += "<!--" + dom_adapter_1.getDOM().nodeValue(el) + "-->";
    }
    else {
        result += dom_adapter_1.getDOM().getText(el);
    }
    return result;
}
exports.stringifyElement = stringifyElement;
exports.browserDetection = new BrowserDetection(null);
//# sourceMappingURL=browser_util.js.map