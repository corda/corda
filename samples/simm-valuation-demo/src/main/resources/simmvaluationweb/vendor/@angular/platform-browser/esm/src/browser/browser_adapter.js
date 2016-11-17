/**
 * @license
 * Copyright Google Inc. All Rights Reserved.
 *
 * Use of this source code is governed by an MIT-style license that can be
 * found in the LICENSE file at https://angular.io/license
 */
import { setRootDomAdapter } from '../dom/dom_adapter';
import { ListWrapper } from '../facade/collection';
import { DateWrapper, global, isBlank, isFunction, isPresent, setValueOnPath } from '../facade/lang';
import { GenericBrowserDomAdapter } from './generic_browser_adapter';
var _attrToPropMap = {
    'class': 'className',
    'innerHtml': 'innerHTML',
    'readonly': 'readOnly',
    'tabindex': 'tabIndex'
};
const DOM_KEY_LOCATION_NUMPAD = 3;
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
export class BrowserDomAdapter extends GenericBrowserDomAdapter {
    parse(templateHtml) { throw new Error('parse not implemented'); }
    static makeCurrent() { setRootDomAdapter(new BrowserDomAdapter()); }
    hasProperty(element /** TODO #9100 */, name) { return name in element; }
    setProperty(el, name, value) { el[name] = value; }
    getProperty(el, name) { return el[name]; }
    invoke(el, methodName, args) {
        el[methodName].apply(el, args);
    }
    // TODO(tbosch): move this into a separate environment class once we have it
    logError(error /** TODO #9100 */) {
        if (window.console.error) {
            window.console.error(error);
        }
        else {
            window.console.log(error);
        }
    }
    log(error /** TODO #9100 */) { window.console.log(error); }
    logGroup(error /** TODO #9100 */) {
        if (window.console.group) {
            window.console.group(error);
            this.logError(error);
        }
        else {
            window.console.log(error);
        }
    }
    logGroupEnd() {
        if (window.console.groupEnd) {
            window.console.groupEnd();
        }
    }
    get attrToPropMap() { return _attrToPropMap; }
    query(selector) { return document.querySelector(selector); }
    querySelector(el /** TODO #9100 */, selector) {
        return el.querySelector(selector);
    }
    querySelectorAll(el /** TODO #9100 */, selector) {
        return el.querySelectorAll(selector);
    }
    on(el /** TODO #9100 */, evt /** TODO #9100 */, listener /** TODO #9100 */) {
        el.addEventListener(evt, listener, false);
    }
    onAndCancel(el /** TODO #9100 */, evt /** TODO #9100 */, listener /** TODO #9100 */) {
        el.addEventListener(evt, listener, false);
        // Needed to follow Dart's subscription semantic, until fix of
        // https://code.google.com/p/dart/issues/detail?id=17406
        return () => { el.removeEventListener(evt, listener, false); };
    }
    dispatchEvent(el /** TODO #9100 */, evt /** TODO #9100 */) { el.dispatchEvent(evt); }
    createMouseEvent(eventType) {
        var evt = document.createEvent('MouseEvent');
        evt.initEvent(eventType, true, true);
        return evt;
    }
    createEvent(eventType /** TODO #9100 */) {
        var evt = document.createEvent('Event');
        evt.initEvent(eventType, true, true);
        return evt;
    }
    preventDefault(evt) {
        evt.preventDefault();
        evt.returnValue = false;
    }
    isPrevented(evt) {
        return evt.defaultPrevented || isPresent(evt.returnValue) && !evt.returnValue;
    }
    getInnerHTML(el /** TODO #9100 */) { return el.innerHTML; }
    getTemplateContent(el /** TODO #9100 */) {
        return 'content' in el && el instanceof HTMLTemplateElement ? el.content : null;
    }
    getOuterHTML(el /** TODO #9100 */) { return el.outerHTML; }
    nodeName(node) { return node.nodeName; }
    nodeValue(node) { return node.nodeValue; }
    type(node) { return node.type; }
    content(node) {
        if (this.hasProperty(node, 'content')) {
            return node.content;
        }
        else {
            return node;
        }
    }
    firstChild(el /** TODO #9100 */) { return el.firstChild; }
    nextSibling(el /** TODO #9100 */) { return el.nextSibling; }
    parentElement(el /** TODO #9100 */) { return el.parentNode; }
    childNodes(el /** TODO #9100 */) { return el.childNodes; }
    childNodesAsList(el /** TODO #9100 */) {
        var childNodes = el.childNodes;
        var res = ListWrapper.createFixedSize(childNodes.length);
        for (var i = 0; i < childNodes.length; i++) {
            res[i] = childNodes[i];
        }
        return res;
    }
    clearNodes(el /** TODO #9100 */) {
        while (el.firstChild) {
            el.removeChild(el.firstChild);
        }
    }
    appendChild(el /** TODO #9100 */, node /** TODO #9100 */) { el.appendChild(node); }
    removeChild(el /** TODO #9100 */, node /** TODO #9100 */) { el.removeChild(node); }
    replaceChild(el, newChild /** TODO #9100 */, oldChild /** TODO #9100 */) {
        el.replaceChild(newChild, oldChild);
    }
    remove(node /** TODO #9100 */) {
        if (node.parentNode) {
            node.parentNode.removeChild(node);
        }
        return node;
    }
    insertBefore(el /** TODO #9100 */, node /** TODO #9100 */) {
        el.parentNode.insertBefore(node, el);
    }
    insertAllBefore(el /** TODO #9100 */, nodes /** TODO #9100 */) {
        nodes.forEach((n /** TODO #9100 */) => el.parentNode.insertBefore(n, el));
    }
    insertAfter(el /** TODO #9100 */, node /** TODO #9100 */) {
        el.parentNode.insertBefore(node, el.nextSibling);
    }
    setInnerHTML(el /** TODO #9100 */, value /** TODO #9100 */) { el.innerHTML = value; }
    getText(el /** TODO #9100 */) { return el.textContent; }
    // TODO(vicb): removed Element type because it does not support StyleElement
    setText(el /** TODO #9100 */, value) { el.textContent = value; }
    getValue(el /** TODO #9100 */) { return el.value; }
    setValue(el /** TODO #9100 */, value) { el.value = value; }
    getChecked(el /** TODO #9100 */) { return el.checked; }
    setChecked(el /** TODO #9100 */, value) { el.checked = value; }
    createComment(text) { return document.createComment(text); }
    createTemplate(html /** TODO #9100 */) {
        var t = document.createElement('template');
        t.innerHTML = html;
        return t;
    }
    createElement(tagName /* TODO #9100 */, doc = document) {
        return doc.createElement(tagName);
    }
    createElementNS(ns /* TODO #9100 */, tagName /* TODO #9100 */, doc = document) {
        return doc.createElementNS(ns, tagName);
    }
    createTextNode(text, doc = document) { return doc.createTextNode(text); }
    createScriptTag(attrName, attrValue, doc = document) {
        var el = doc.createElement('SCRIPT');
        el.setAttribute(attrName, attrValue);
        return el;
    }
    createStyleElement(css, doc = document) {
        var style = doc.createElement('style');
        this.appendChild(style, this.createTextNode(css));
        return style;
    }
    createShadowRoot(el) { return el.createShadowRoot(); }
    getShadowRoot(el) { return el.shadowRoot; }
    getHost(el) { return el.host; }
    clone(node) { return node.cloneNode(true); }
    getElementsByClassName(element /** TODO #9100 */, name) {
        return element.getElementsByClassName(name);
    }
    getElementsByTagName(element /** TODO #9100 */, name) {
        return element.getElementsByTagName(name);
    }
    classList(element /** TODO #9100 */) {
        return Array.prototype.slice.call(element.classList, 0);
    }
    addClass(element /** TODO #9100 */, className) { element.classList.add(className); }
    removeClass(element /** TODO #9100 */, className) {
        element.classList.remove(className);
    }
    hasClass(element /** TODO #9100 */, className) {
        return element.classList.contains(className);
    }
    setStyle(element /** TODO #9100 */, styleName, styleValue) {
        element.style[styleName] = styleValue;
    }
    removeStyle(element /** TODO #9100 */, stylename) {
        element.style[stylename] = null;
    }
    getStyle(element /** TODO #9100 */, stylename) {
        return element.style[stylename];
    }
    hasStyle(element /** TODO #9100 */, styleName, styleValue = null) {
        var value = this.getStyle(element, styleName) || '';
        return styleValue ? value == styleValue : value.length > 0;
    }
    tagName(element /** TODO #9100 */) { return element.tagName; }
    attributeMap(element /** TODO #9100 */) {
        var res = new Map();
        var elAttrs = element.attributes;
        for (var i = 0; i < elAttrs.length; i++) {
            var attrib = elAttrs[i];
            res.set(attrib.name, attrib.value);
        }
        return res;
    }
    hasAttribute(element /** TODO #9100 */, attribute) {
        return element.hasAttribute(attribute);
    }
    hasAttributeNS(element /** TODO #9100 */, ns, attribute) {
        return element.hasAttributeNS(ns, attribute);
    }
    getAttribute(element /** TODO #9100 */, attribute) {
        return element.getAttribute(attribute);
    }
    getAttributeNS(element /** TODO #9100 */, ns, name) {
        return element.getAttributeNS(ns, name);
    }
    setAttribute(element /** TODO #9100 */, name, value) {
        element.setAttribute(name, value);
    }
    setAttributeNS(element /** TODO #9100 */, ns, name, value) {
        element.setAttributeNS(ns, name, value);
    }
    removeAttribute(element /** TODO #9100 */, attribute) {
        element.removeAttribute(attribute);
    }
    removeAttributeNS(element /** TODO #9100 */, ns, name) {
        element.removeAttributeNS(ns, name);
    }
    templateAwareRoot(el /** TODO #9100 */) {
        return this.isTemplateElement(el) ? this.content(el) : el;
    }
    createHtmlDocument() {
        return document.implementation.createHTMLDocument('fakeTitle');
    }
    defaultDoc() { return document; }
    getBoundingClientRect(el /** TODO #9100 */) {
        try {
            return el.getBoundingClientRect();
        }
        catch (e) {
            return { top: 0, bottom: 0, left: 0, right: 0, width: 0, height: 0 };
        }
    }
    getTitle() { return document.title; }
    setTitle(newTitle) { document.title = newTitle || ''; }
    elementMatches(n /** TODO #9100 */, selector) {
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
    }
    isTemplateElement(el) {
        return el instanceof HTMLElement && el.nodeName == 'TEMPLATE';
    }
    isTextNode(node) { return node.nodeType === Node.TEXT_NODE; }
    isCommentNode(node) { return node.nodeType === Node.COMMENT_NODE; }
    isElementNode(node) { return node.nodeType === Node.ELEMENT_NODE; }
    hasShadowRoot(node /** TODO #9100 */) {
        return node instanceof HTMLElement && isPresent(node.shadowRoot);
    }
    isShadowRoot(node /** TODO #9100 */) { return node instanceof DocumentFragment; }
    importIntoDoc(node) {
        var toImport = node;
        if (this.isTemplateElement(node)) {
            toImport = this.content(node);
        }
        return document.importNode(toImport, true);
    }
    adoptNode(node) { return document.adoptNode(node); }
    getHref(el) { return el.href; }
    getEventKey(event /** TODO #9100 */) {
        var key = event.key;
        if (isBlank(key)) {
            key = event.keyIdentifier;
            // keyIdentifier is defined in the old draft of DOM Level 3 Events implemented by Chrome and
            // Safari
            // cf
            // http://www.w3.org/TR/2007/WD-DOM-Level-3-Events-20071221/events.html#Events-KeyboardEvents-Interfaces
            if (isBlank(key)) {
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
    }
    getGlobalEventTarget(target) {
        if (target == 'window') {
            return window;
        }
        else if (target == 'document') {
            return document;
        }
        else if (target == 'body') {
            return document.body;
        }
    }
    getHistory() { return window.history; }
    getLocation() { return window.location; }
    getBaseHref() {
        var href = getBaseElementHref();
        if (isBlank(href)) {
            return null;
        }
        return relativePath(href);
    }
    resetBaseElement() { baseElement = null; }
    getUserAgent() { return window.navigator.userAgent; }
    setData(element /** TODO #9100 */, name, value) {
        this.setAttribute(element, 'data-' + name, value);
    }
    getData(element /** TODO #9100 */, name) {
        return this.getAttribute(element, 'data-' + name);
    }
    getComputedStyle(element /** TODO #9100 */) { return getComputedStyle(element); }
    // TODO(tbosch): move this into a separate environment class once we have it
    setGlobalVar(path, value) { setValueOnPath(global, path, value); }
    requestAnimationFrame(callback /** TODO #9100 */) {
        return window.requestAnimationFrame(callback);
    }
    cancelAnimationFrame(id) { window.cancelAnimationFrame(id); }
    supportsWebAnimation() {
        return isFunction(document.body['animate']);
    }
    performanceNow() {
        // performance.now() is not available in all browsers, see
        // http://caniuse.com/#search=performance.now
        if (isPresent(window.performance) && isPresent(window.performance.now)) {
            return window.performance.now();
        }
        else {
            return DateWrapper.toMillis(DateWrapper.now());
        }
    }
    supportsCookies() { return true; }
    getCookie(name) { return parseCookieValue(document.cookie, name); }
    setCookie(name, value) {
        // document.cookie is magical, assigning into it assigns/overrides one cookie value, but does
        // not clear other cookies.
        document.cookie = encodeURIComponent(name) + '=' + encodeURIComponent(value);
    }
}
var baseElement = null;
function getBaseElementHref() {
    if (isBlank(baseElement)) {
        baseElement = document.querySelector('base');
        if (isBlank(baseElement)) {
            return null;
        }
    }
    return baseElement.getAttribute('href');
}
// based on urlUtils.js in AngularJS 1
var urlParsingNode = null;
function relativePath(url /** TODO #9100 */) {
    if (isBlank(urlParsingNode)) {
        urlParsingNode = document.createElement('a');
    }
    urlParsingNode.setAttribute('href', url);
    return (urlParsingNode.pathname.charAt(0) === '/') ? urlParsingNode.pathname :
        '/' + urlParsingNode.pathname;
}
export function parseCookieValue(cookie, name) {
    name = encodeURIComponent(name);
    let cookies = cookie.split(';');
    for (let cookie of cookies) {
        let [key, value] = cookie.split('=', 2);
        if (key.trim() === name) {
            return decodeURIComponent(value);
        }
    }
    return null;
}
//# sourceMappingURL=browser_adapter.js.map