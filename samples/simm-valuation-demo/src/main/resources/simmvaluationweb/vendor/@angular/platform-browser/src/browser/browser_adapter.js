/**
 * @license
 * Copyright Google Inc. All Rights Reserved.
 *
 * Use of this source code is governed by an MIT-style license that can be
 * found in the LICENSE file at https://angular.io/license
 */
"use strict";
var __extends = (this && this.__extends) || function (d, b) {
    for (var p in b) if (b.hasOwnProperty(p)) d[p] = b[p];
    function __() { this.constructor = d; }
    d.prototype = b === null ? Object.create(b) : (__.prototype = b.prototype, new __());
};
var dom_adapter_1 = require('../dom/dom_adapter');
var collection_1 = require('../facade/collection');
var lang_1 = require('../facade/lang');
var generic_browser_adapter_1 = require('./generic_browser_adapter');
var _attrToPropMap = {
    'class': 'className',
    'innerHtml': 'innerHTML',
    'readonly': 'readOnly',
    'tabindex': 'tabIndex'
};
var DOM_KEY_LOCATION_NUMPAD = 3;
// Map to convert some key or keyIdentifier values to what will be returned by getEventKey
var _keyMap = {
    // The following values are here for cross-browser compatibility and to match the W3C standard
    // cf http://www.w3.org/TR/DOM-Level-3-Events-key/
    '\b': 'Backspace',
    '\t': 'Tab',
    '\x7F': 'Delete',
    '\x1B': 'Escape',
    'Del': 'Delete',
    'Esc': 'Escape',
    'Left': 'ArrowLeft',
    'Right': 'ArrowRight',
    'Up': 'ArrowUp',
    'Down': 'ArrowDown',
    'Menu': 'ContextMenu',
    'Scroll': 'ScrollLock',
    'Win': 'OS'
};
// There is a bug in Chrome for numeric keypad keys:
// https://code.google.com/p/chromium/issues/detail?id=155654
// 1, 2, 3 ... are reported as A, B, C ...
var _chromeNumKeyPadMap = {
    'A': '1',
    'B': '2',
    'C': '3',
    'D': '4',
    'E': '5',
    'F': '6',
    'G': '7',
    'H': '8',
    'I': '9',
    'J': '*',
    'K': '+',
    'M': '-',
    'N': '.',
    'O': '/',
    '\x60': '0',
    '\x90': 'NumLock'
};
/**
 * A `DomAdapter` powered by full browser DOM APIs.
 */
/* tslint:disable:requireParameterType */
var BrowserDomAdapter = (function (_super) {
    __extends(BrowserDomAdapter, _super);
    function BrowserDomAdapter() {
        _super.apply(this, arguments);
    }
    BrowserDomAdapter.prototype.parse = function (templateHtml) { throw new Error('parse not implemented'); };
    BrowserDomAdapter.makeCurrent = function () { dom_adapter_1.setRootDomAdapter(new BrowserDomAdapter()); };
    BrowserDomAdapter.prototype.hasProperty = function (element /** TODO #9100 */, name) { return name in element; };
    BrowserDomAdapter.prototype.setProperty = function (el, name, value) { el[name] = value; };
    BrowserDomAdapter.prototype.getProperty = function (el, name) { return el[name]; };
    BrowserDomAdapter.prototype.invoke = function (el, methodName, args) {
        el[methodName].apply(el, args);
    };
    // TODO(tbosch): move this into a separate environment class once we have it
    BrowserDomAdapter.prototype.logError = function (error /** TODO #9100 */) {
        if (window.console.error) {
            window.console.error(error);
        }
        else {
            window.console.log(error);
        }
    };
    BrowserDomAdapter.prototype.log = function (error /** TODO #9100 */) { window.console.log(error); };
    BrowserDomAdapter.prototype.logGroup = function (error /** TODO #9100 */) {
        if (window.console.group) {
            window.console.group(error);
            this.logError(error);
        }
        else {
            window.console.log(error);
        }
    };
    BrowserDomAdapter.prototype.logGroupEnd = function () {
        if (window.console.groupEnd) {
            window.console.groupEnd();
        }
    };
    Object.defineProperty(BrowserDomAdapter.prototype, "attrToPropMap", {
        get: function () { return _attrToPropMap; },
        enumerable: true,
        configurable: true
    });
    BrowserDomAdapter.prototype.query = function (selector) { return document.querySelector(selector); };
    BrowserDomAdapter.prototype.querySelector = function (el /** TODO #9100 */, selector) {
        return el.querySelector(selector);
    };
    BrowserDomAdapter.prototype.querySelectorAll = function (el /** TODO #9100 */, selector) {
        return el.querySelectorAll(selector);
    };
    BrowserDomAdapter.prototype.on = function (el /** TODO #9100 */, evt /** TODO #9100 */, listener /** TODO #9100 */) {
        el.addEventListener(evt, listener, false);
    };
    BrowserDomAdapter.prototype.onAndCancel = function (el /** TODO #9100 */, evt /** TODO #9100 */, listener /** TODO #9100 */) {
        el.addEventListener(evt, listener, false);
        // Needed to follow Dart's subscription semantic, until fix of
        // https://code.google.com/p/dart/issues/detail?id=17406
        return function () { el.removeEventListener(evt, listener, false); };
    };
    BrowserDomAdapter.prototype.dispatchEvent = function (el /** TODO #9100 */, evt /** TODO #9100 */) { el.dispatchEvent(evt); };
    BrowserDomAdapter.prototype.createMouseEvent = function (eventType) {
        var evt = document.createEvent('MouseEvent');
        evt.initEvent(eventType, true, true);
        return evt;
    };
    BrowserDomAdapter.prototype.createEvent = function (eventType /** TODO #9100 */) {
        var evt = document.createEvent('Event');
        evt.initEvent(eventType, true, true);
        return evt;
    };
    BrowserDomAdapter.prototype.preventDefault = function (evt) {
        evt.preventDefault();
        evt.returnValue = false;
    };
    BrowserDomAdapter.prototype.isPrevented = function (evt) {
        return evt.defaultPrevented || lang_1.isPresent(evt.returnValue) && !evt.returnValue;
    };
    BrowserDomAdapter.prototype.getInnerHTML = function (el /** TODO #9100 */) { return el.innerHTML; };
    BrowserDomAdapter.prototype.getTemplateContent = function (el /** TODO #9100 */) {
        return 'content' in el && el instanceof HTMLTemplateElement ? el.content : null;
    };
    BrowserDomAdapter.prototype.getOuterHTML = function (el /** TODO #9100 */) { return el.outerHTML; };
    BrowserDomAdapter.prototype.nodeName = function (node) { return node.nodeName; };
    BrowserDomAdapter.prototype.nodeValue = function (node) { return node.nodeValue; };
    BrowserDomAdapter.prototype.type = function (node) { return node.type; };
    BrowserDomAdapter.prototype.content = function (node) {
        if (this.hasProperty(node, 'content')) {
            return node.content;
        }
        else {
            return node;
        }
    };
    BrowserDomAdapter.prototype.firstChild = function (el /** TODO #9100 */) { return el.firstChild; };
    BrowserDomAdapter.prototype.nextSibling = function (el /** TODO #9100 */) { return el.nextSibling; };
    BrowserDomAdapter.prototype.parentElement = function (el /** TODO #9100 */) { return el.parentNode; };
    BrowserDomAdapter.prototype.childNodes = function (el /** TODO #9100 */) { return el.childNodes; };
    BrowserDomAdapter.prototype.childNodesAsList = function (el /** TODO #9100 */) {
        var childNodes = el.childNodes;
        var res = collection_1.ListWrapper.createFixedSize(childNodes.length);
        for (var i = 0; i < childNodes.length; i++) {
            res[i] = childNodes[i];
        }
        return res;
    };
    BrowserDomAdapter.prototype.clearNodes = function (el /** TODO #9100 */) {
        while (el.firstChild) {
            el.removeChild(el.firstChild);
        }
    };
    BrowserDomAdapter.prototype.appendChild = function (el /** TODO #9100 */, node /** TODO #9100 */) { el.appendChild(node); };
    BrowserDomAdapter.prototype.removeChild = function (el /** TODO #9100 */, node /** TODO #9100 */) { el.removeChild(node); };
    BrowserDomAdapter.prototype.replaceChild = function (el, newChild /** TODO #9100 */, oldChild /** TODO #9100 */) {
        el.replaceChild(newChild, oldChild);
    };
    BrowserDomAdapter.prototype.remove = function (node /** TODO #9100 */) {
        if (node.parentNode) {
            node.parentNode.removeChild(node);
        }
        return node;
    };
    BrowserDomAdapter.prototype.insertBefore = function (el /** TODO #9100 */, node /** TODO #9100 */) {
        el.parentNode.insertBefore(node, el);
    };
    BrowserDomAdapter.prototype.insertAllBefore = function (el /** TODO #9100 */, nodes /** TODO #9100 */) {
        nodes.forEach(function (n /** TODO #9100 */) { return el.parentNode.insertBefore(n, el); });
    };
    BrowserDomAdapter.prototype.insertAfter = function (el /** TODO #9100 */, node /** TODO #9100 */) {
        el.parentNode.insertBefore(node, el.nextSibling);
    };
    BrowserDomAdapter.prototype.setInnerHTML = function (el /** TODO #9100 */, value /** TODO #9100 */) { el.innerHTML = value; };
    BrowserDomAdapter.prototype.getText = function (el /** TODO #9100 */) { return el.textContent; };
    // TODO(vicb): removed Element type because it does not support StyleElement
    BrowserDomAdapter.prototype.setText = function (el /** TODO #9100 */, value) { el.textContent = value; };
    BrowserDomAdapter.prototype.getValue = function (el /** TODO #9100 */) { return el.value; };
    BrowserDomAdapter.prototype.setValue = function (el /** TODO #9100 */, value) { el.value = value; };
    BrowserDomAdapter.prototype.getChecked = function (el /** TODO #9100 */) { return el.checked; };
    BrowserDomAdapter.prototype.setChecked = function (el /** TODO #9100 */, value) { el.checked = value; };
    BrowserDomAdapter.prototype.createComment = function (text) { return document.createComment(text); };
    BrowserDomAdapter.prototype.createTemplate = function (html /** TODO #9100 */) {
        var t = document.createElement('template');
        t.innerHTML = html;
        return t;
    };
    BrowserDomAdapter.prototype.createElement = function (tagName /* TODO #9100 */, doc) {
        if (doc === void 0) { doc = document; }
        return doc.createElement(tagName);
    };
    BrowserDomAdapter.prototype.createElementNS = function (ns /* TODO #9100 */, tagName /* TODO #9100 */, doc) {
        if (doc === void 0) { doc = document; }
        return doc.createElementNS(ns, tagName);
    };
    BrowserDomAdapter.prototype.createTextNode = function (text, doc) {
        if (doc === void 0) { doc = document; }
        return doc.createTextNode(text);
    };
    BrowserDomAdapter.prototype.createScriptTag = function (attrName, attrValue, doc) {
        if (doc === void 0) { doc = document; }
        var el = doc.createElement('SCRIPT');
        el.setAttribute(attrName, attrValue);
        return el;
    };
    BrowserDomAdapter.prototype.createStyleElement = function (css, doc) {
        if (doc === void 0) { doc = document; }
        var style = doc.createElement('style');
        this.appendChild(style, this.createTextNode(css));
        return style;
    };
    BrowserDomAdapter.prototype.createShadowRoot = function (el) { return el.createShadowRoot(); };
    BrowserDomAdapter.prototype.getShadowRoot = function (el) { return el.shadowRoot; };
    BrowserDomAdapter.prototype.getHost = function (el) { return el.host; };
    BrowserDomAdapter.prototype.clone = function (node) { return node.cloneNode(true); };
    BrowserDomAdapter.prototype.getElementsByClassName = function (element /** TODO #9100 */, name) {
        return element.getElementsByClassName(name);
    };
    BrowserDomAdapter.prototype.getElementsByTagName = function (element /** TODO #9100 */, name) {
        return element.getElementsByTagName(name);
    };
    BrowserDomAdapter.prototype.classList = function (element /** TODO #9100 */) {
        return Array.prototype.slice.call(element.classList, 0);
    };
    BrowserDomAdapter.prototype.addClass = function (element /** TODO #9100 */, className) { element.classList.add(className); };
    BrowserDomAdapter.prototype.removeClass = function (element /** TODO #9100 */, className) {
        element.classList.remove(className);
    };
    BrowserDomAdapter.prototype.hasClass = function (element /** TODO #9100 */, className) {
        return element.classList.contains(className);
    };
    BrowserDomAdapter.prototype.setStyle = function (element /** TODO #9100 */, styleName, styleValue) {
        element.style[styleName] = styleValue;
    };
    BrowserDomAdapter.prototype.removeStyle = function (element /** TODO #9100 */, stylename) {
        element.style[stylename] = null;
    };
    BrowserDomAdapter.prototype.getStyle = function (element /** TODO #9100 */, stylename) {
        return element.style[stylename];
    };
    BrowserDomAdapter.prototype.hasStyle = function (element /** TODO #9100 */, styleName, styleValue) {
        if (styleValue === void 0) { styleValue = null; }
        var value = this.getStyle(element, styleName) || '';
        return styleValue ? value == styleValue : value.length > 0;
    };
    BrowserDomAdapter.prototype.tagName = function (element /** TODO #9100 */) { return element.tagName; };
    BrowserDomAdapter.prototype.attributeMap = function (element /** TODO #9100 */) {
        var res = new Map();
        var elAttrs = element.attributes;
        for (var i = 0; i < elAttrs.length; i++) {
            var attrib = elAttrs[i];
            res.set(attrib.name, attrib.value);
        }
        return res;
    };
    BrowserDomAdapter.prototype.hasAttribute = function (element /** TODO #9100 */, attribute) {
        return element.hasAttribute(attribute);
    };
    BrowserDomAdapter.prototype.hasAttributeNS = function (element /** TODO #9100 */, ns, attribute) {
        return element.hasAttributeNS(ns, attribute);
    };
    BrowserDomAdapter.prototype.getAttribute = function (element /** TODO #9100 */, attribute) {
        return element.getAttribute(attribute);
    };
    BrowserDomAdapter.prototype.getAttributeNS = function (element /** TODO #9100 */, ns, name) {
        return element.getAttributeNS(ns, name);
    };
    BrowserDomAdapter.prototype.setAttribute = function (element /** TODO #9100 */, name, value) {
        element.setAttribute(name, value);
    };
    BrowserDomAdapter.prototype.setAttributeNS = function (element /** TODO #9100 */, ns, name, value) {
        element.setAttributeNS(ns, name, value);
    };
    BrowserDomAdapter.prototype.removeAttribute = function (element /** TODO #9100 */, attribute) {
        element.removeAttribute(attribute);
    };
    BrowserDomAdapter.prototype.removeAttributeNS = function (element /** TODO #9100 */, ns, name) {
        element.removeAttributeNS(ns, name);
    };
    BrowserDomAdapter.prototype.templateAwareRoot = function (el /** TODO #9100 */) {
        return this.isTemplateElement(el) ? this.content(el) : el;
    };
    BrowserDomAdapter.prototype.createHtmlDocument = function () {
        return document.implementation.createHTMLDocument('fakeTitle');
    };
    BrowserDomAdapter.prototype.defaultDoc = function () { return document; };
    BrowserDomAdapter.prototype.getBoundingClientRect = function (el /** TODO #9100 */) {
        try {
            return el.getBoundingClientRect();
        }
        catch (e) {
            return { top: 0, bottom: 0, left: 0, right: 0, width: 0, height: 0 };
        }
    };
    BrowserDomAdapter.prototype.getTitle = function () { return document.title; };
    BrowserDomAdapter.prototype.setTitle = function (newTitle) { document.title = newTitle || ''; };
    BrowserDomAdapter.prototype.elementMatches = function (n /** TODO #9100 */, selector) {
        var matches = false;
        if (n instanceof HTMLElement) {
            if (n.matches) {
                matches = n.matches(selector);
            }
            else if (n.msMatchesSelector) {
                matches = n.msMatchesSelector(selector);
            }
            else if (n.webkitMatchesSelector) {
                matches = n.webkitMatchesSelector(selector);
            }
        }
        return matches;
    };
    BrowserDomAdapter.prototype.isTemplateElement = function (el) {
        return el instanceof HTMLElement && el.nodeName == 'TEMPLATE';
    };
    BrowserDomAdapter.prototype.isTextNode = function (node) { return node.nodeType === Node.TEXT_NODE; };
    BrowserDomAdapter.prototype.isCommentNode = function (node) { return node.nodeType === Node.COMMENT_NODE; };
    BrowserDomAdapter.prototype.isElementNode = function (node) { return node.nodeType === Node.ELEMENT_NODE; };
    BrowserDomAdapter.prototype.hasShadowRoot = function (node /** TODO #9100 */) {
        return node instanceof HTMLElement && lang_1.isPresent(node.shadowRoot);
    };
    BrowserDomAdapter.prototype.isShadowRoot = function (node /** TODO #9100 */) { return node instanceof DocumentFragment; };
    BrowserDomAdapter.prototype.importIntoDoc = function (node) {
        var toImport = node;
        if (this.isTemplateElement(node)) {
            toImport = this.content(node);
        }
        return document.importNode(toImport, true);
    };
    BrowserDomAdapter.prototype.adoptNode = function (node) { return document.adoptNode(node); };
    BrowserDomAdapter.prototype.getHref = function (el) { return el.href; };
    BrowserDomAdapter.prototype.getEventKey = function (event /** TODO #9100 */) {
        var key = event.key;
        if (lang_1.isBlank(key)) {
            key = event.keyIdentifier;
            // keyIdentifier is defined in the old draft of DOM Level 3 Events implemented by Chrome and
            // Safari
            // cf
            // http://www.w3.org/TR/2007/WD-DOM-Level-3-Events-20071221/events.html#Events-KeyboardEvents-Interfaces
            if (lang_1.isBlank(key)) {
                return 'Unidentified';
            }
            if (key.startsWith('U+')) {
                key = String.fromCharCode(parseInt(key.substring(2), 16));
                if (event.location === DOM_KEY_LOCATION_NUMPAD && _chromeNumKeyPadMap.hasOwnProperty(key)) {
                    // There is a bug in Chrome for numeric keypad keys:
                    // https://code.google.com/p/chromium/issues/detail?id=155654
                    // 1, 2, 3 ... are reported as A, B, C ...
                    key = _chromeNumKeyPadMap[key];
                }
            }
        }
        if (_keyMap.hasOwnProperty(key)) {
            key = _keyMap[key];
        }
        return key;
    };
    BrowserDomAdapter.prototype.getGlobalEventTarget = function (target) {
        if (target == 'window') {
            return window;
        }
        else if (target == 'document') {
            return document;
        }
        else if (target == 'body') {
            return document.body;
        }
    };
    BrowserDomAdapter.prototype.getHistory = function () { return window.history; };
    BrowserDomAdapter.prototype.getLocation = function () { return window.location; };
    BrowserDomAdapter.prototype.getBaseHref = function () {
        var href = getBaseElementHref();
        if (lang_1.isBlank(href)) {
            return null;
        }
        return relativePath(href);
    };
    BrowserDomAdapter.prototype.resetBaseElement = function () { baseElement = null; };
    BrowserDomAdapter.prototype.getUserAgent = function () { return window.navigator.userAgent; };
    BrowserDomAdapter.prototype.setData = function (element /** TODO #9100 */, name, value) {
        this.setAttribute(element, 'data-' + name, value);
    };
    BrowserDomAdapter.prototype.getData = function (element /** TODO #9100 */, name) {
        return this.getAttribute(element, 'data-' + name);
    };
    BrowserDomAdapter.prototype.getComputedStyle = function (element /** TODO #9100 */) { return getComputedStyle(element); };
    // TODO(tbosch): move this into a separate environment class once we have it
    BrowserDomAdapter.prototype.setGlobalVar = function (path, value) { lang_1.setValueOnPath(lang_1.global, path, value); };
    BrowserDomAdapter.prototype.requestAnimationFrame = function (callback /** TODO #9100 */) {
        return window.requestAnimationFrame(callback);
    };
    BrowserDomAdapter.prototype.cancelAnimationFrame = function (id) { window.cancelAnimationFrame(id); };
    BrowserDomAdapter.prototype.supportsWebAnimation = function () {
        return lang_1.isFunction(document.body['animate']);
    };
    BrowserDomAdapter.prototype.performanceNow = function () {
        // performance.now() is not available in all browsers, see
        // http://caniuse.com/#search=performance.now
        if (lang_1.isPresent(window.performance) && lang_1.isPresent(window.performance.now)) {
            return window.performance.now();
        }
        else {
            return lang_1.DateWrapper.toMillis(lang_1.DateWrapper.now());
        }
    };
    BrowserDomAdapter.prototype.supportsCookies = function () { return true; };
    BrowserDomAdapter.prototype.getCookie = function (name) { return parseCookieValue(document.cookie, name); };
    BrowserDomAdapter.prototype.setCookie = function (name, value) {
        // document.cookie is magical, assigning into it assigns/overrides one cookie value, but does
        // not clear other cookies.
        document.cookie = encodeURIComponent(name) + '=' + encodeURIComponent(value);
    };
    return BrowserDomAdapter;
}(generic_browser_adapter_1.GenericBrowserDomAdapter));
exports.BrowserDomAdapter = BrowserDomAdapter;
var baseElement = null;
function getBaseElementHref() {
    if (lang_1.isBlank(baseElement)) {
        baseElement = document.querySelector('base');
        if (lang_1.isBlank(baseElement)) {
            return null;
        }
    }
    return baseElement.getAttribute('href');
}
// based on urlUtils.js in AngularJS 1
var urlParsingNode = null;
function relativePath(url /** TODO #9100 */) {
    if (lang_1.isBlank(urlParsingNode)) {
        urlParsingNode = document.createElement('a');
    }
    urlParsingNode.setAttribute('href', url);
    return (urlParsingNode.pathname.charAt(0) === '/') ? urlParsingNode.pathname :
        '/' + urlParsingNode.pathname;
}
function parseCookieValue(cookie, name) {
    name = encodeURIComponent(name);
    var cookies = cookie.split(';');
    for (var _i = 0, cookies_1 = cookies; _i < cookies_1.length; _i++) {
        var cookie_1 = cookies_1[_i];
        var _a = cookie_1.split('=', 2), key = _a[0], value = _a[1];
        if (key.trim() === name) {
            return decodeURIComponent(value);
        }
    }
    return null;
}
exports.parseCookieValue = parseCookieValue;
//# sourceMappingURL=browser_adapter.js.map