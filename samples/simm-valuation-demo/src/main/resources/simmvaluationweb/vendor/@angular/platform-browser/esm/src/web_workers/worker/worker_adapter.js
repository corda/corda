/**
 * @license
 * Copyright Google Inc. All Rights Reserved.
 *
 * Use of this source code is governed by an MIT-style license that can be
 * found in the LICENSE file at https://angular.io/license
 */
import { DomAdapter, setRootDomAdapter } from '../../dom/dom_adapter';
/**
 * This adapter is required to log error messages.
 *
 * Note: other methods all throw as the DOM is not accessible directly in web worker context.
 */
export class WorkerDomAdapter extends DomAdapter {
    static makeCurrent() { setRootDomAdapter(new WorkerDomAdapter()); }
    logError(error /** TODO #9100 */) {
        if (console.error) {
            console.error(error);
        }
        else {
            console.log(error);
        }
    }
    log(error /** TODO #9100 */) { console.log(error); }
    logGroup(error /** TODO #9100 */) {
        if (console.group) {
            console.group(error);
            this.logError(error);
        }
        else {
            console.log(error);
        }
    }
    logGroupEnd() {
        if (console.groupEnd) {
            console.groupEnd();
        }
    }
    hasProperty(element /** TODO #9100 */, name) { throw 'not implemented'; }
    setProperty(el, name, value) { throw 'not implemented'; }
    getProperty(el, name) { throw 'not implemented'; }
    invoke(el, methodName, args) { throw 'not implemented'; }
    getXHR() { throw 'not implemented'; }
    get attrToPropMap() { throw 'not implemented'; }
    set attrToPropMap(value) { throw 'not implemented'; }
    parse(templateHtml) { throw 'not implemented'; }
    query(selector) { throw 'not implemented'; }
    querySelector(el /** TODO #9100 */, selector) {
        throw 'not implemented';
    }
    querySelectorAll(el /** TODO #9100 */, selector) { throw 'not implemented'; }
    on(el /** TODO #9100 */, evt /** TODO #9100 */, listener /** TODO #9100 */) {
        throw 'not implemented';
    }
    onAndCancel(el /** TODO #9100 */, evt /** TODO #9100 */, listener /** TODO #9100 */) {
        throw 'not implemented';
    }
    dispatchEvent(el /** TODO #9100 */, evt /** TODO #9100 */) { throw 'not implemented'; }
    createMouseEvent(eventType /** TODO #9100 */) { throw 'not implemented'; }
    createEvent(eventType) { throw 'not implemented'; }
    preventDefault(evt /** TODO #9100 */) { throw 'not implemented'; }
    isPrevented(evt /** TODO #9100 */) { throw 'not implemented'; }
    getInnerHTML(el /** TODO #9100 */) { throw 'not implemented'; }
    getTemplateContent(el /** TODO #9100 */) { throw 'not implemented'; }
    getOuterHTML(el /** TODO #9100 */) { throw 'not implemented'; }
    nodeName(node /** TODO #9100 */) { throw 'not implemented'; }
    nodeValue(node /** TODO #9100 */) { throw 'not implemented'; }
    type(node /** TODO #9100 */) { throw 'not implemented'; }
    content(node /** TODO #9100 */) { throw 'not implemented'; }
    firstChild(el /** TODO #9100 */) { throw 'not implemented'; }
    nextSibling(el /** TODO #9100 */) { throw 'not implemented'; }
    parentElement(el /** TODO #9100 */) { throw 'not implemented'; }
    childNodes(el /** TODO #9100 */) { throw 'not implemented'; }
    childNodesAsList(el /** TODO #9100 */) { throw 'not implemented'; }
    clearNodes(el /** TODO #9100 */) { throw 'not implemented'; }
    appendChild(el /** TODO #9100 */, node /** TODO #9100 */) { throw 'not implemented'; }
    removeChild(el /** TODO #9100 */, node /** TODO #9100 */) { throw 'not implemented'; }
    replaceChild(el /** TODO #9100 */, newNode /** TODO #9100 */, oldNode /** TODO #9100 */) {
        throw 'not implemented';
    }
    remove(el /** TODO #9100 */) { throw 'not implemented'; }
    insertBefore(el /** TODO #9100 */, node /** TODO #9100 */) { throw 'not implemented'; }
    insertAllBefore(el /** TODO #9100 */, nodes /** TODO #9100 */) {
        throw 'not implemented';
    }
    insertAfter(el /** TODO #9100 */, node /** TODO #9100 */) { throw 'not implemented'; }
    setInnerHTML(el /** TODO #9100 */, value /** TODO #9100 */) { throw 'not implemented'; }
    getText(el /** TODO #9100 */) { throw 'not implemented'; }
    setText(el /** TODO #9100 */, value) { throw 'not implemented'; }
    getValue(el /** TODO #9100 */) { throw 'not implemented'; }
    setValue(el /** TODO #9100 */, value) { throw 'not implemented'; }
    getChecked(el /** TODO #9100 */) { throw 'not implemented'; }
    setChecked(el /** TODO #9100 */, value) { throw 'not implemented'; }
    createComment(text) { throw 'not implemented'; }
    createTemplate(html /** TODO #9100 */) { throw 'not implemented'; }
    createElement(tagName /** TODO #9100 */, doc /** TODO #9100 */) {
        throw 'not implemented';
    }
    createElementNS(ns, tagName, doc /** TODO #9100 */) {
        throw 'not implemented';
    }
    createTextNode(text, doc /** TODO #9100 */) { throw 'not implemented'; }
    createScriptTag(attrName, attrValue, doc /** TODO #9100 */) {
        throw 'not implemented';
    }
    createStyleElement(css, doc /** TODO #9100 */) {
        throw 'not implemented';
    }
    createShadowRoot(el /** TODO #9100 */) { throw 'not implemented'; }
    getShadowRoot(el /** TODO #9100 */) { throw 'not implemented'; }
    getHost(el /** TODO #9100 */) { throw 'not implemented'; }
    getDistributedNodes(el /** TODO #9100 */) { throw 'not implemented'; }
    clone(node) { throw 'not implemented'; }
    getElementsByClassName(element /** TODO #9100 */, name) {
        throw 'not implemented';
    }
    getElementsByTagName(element /** TODO #9100 */, name) {
        throw 'not implemented';
    }
    classList(element /** TODO #9100 */) { throw 'not implemented'; }
    addClass(element /** TODO #9100 */, className) { throw 'not implemented'; }
    removeClass(element /** TODO #9100 */, className) { throw 'not implemented'; }
    hasClass(element /** TODO #9100 */, className) { throw 'not implemented'; }
    setStyle(element /** TODO #9100 */, styleName, styleValue) {
        throw 'not implemented';
    }
    removeStyle(element /** TODO #9100 */, styleName) { throw 'not implemented'; }
    getStyle(element /** TODO #9100 */, styleName) { throw 'not implemented'; }
    hasStyle(element /** TODO #9100 */, styleName, styleValue) {
        throw 'not implemented';
    }
    tagName(element /** TODO #9100 */) { throw 'not implemented'; }
    attributeMap(element /** TODO #9100 */) { throw 'not implemented'; }
    hasAttribute(element /** TODO #9100 */, attribute) {
        throw 'not implemented';
    }
    hasAttributeNS(element /** TODO #9100 */, ns, attribute) {
        throw 'not implemented';
    }
    getAttribute(element /** TODO #9100 */, attribute) {
        throw 'not implemented';
    }
    getAttributeNS(element /** TODO #9100 */, ns, attribute) {
        throw 'not implemented';
    }
    setAttribute(element /** TODO #9100 */, name, value) {
        throw 'not implemented';
    }
    setAttributeNS(element /** TODO #9100 */, ns, name, value) {
        throw 'not implemented';
    }
    removeAttribute(element /** TODO #9100 */, attribute) { throw 'not implemented'; }
    removeAttributeNS(element /** TODO #9100 */, ns, attribute) {
        throw 'not implemented';
    }
    templateAwareRoot(el /** TODO #9100 */) { throw 'not implemented'; }
    createHtmlDocument() { throw 'not implemented'; }
    defaultDoc() { throw 'not implemented'; }
    getBoundingClientRect(el /** TODO #9100 */) { throw 'not implemented'; }
    getTitle() { throw 'not implemented'; }
    setTitle(newTitle) { throw 'not implemented'; }
    elementMatches(n /** TODO #9100 */, selector) { throw 'not implemented'; }
    isTemplateElement(el) { throw 'not implemented'; }
    isTextNode(node /** TODO #9100 */) { throw 'not implemented'; }
    isCommentNode(node /** TODO #9100 */) { throw 'not implemented'; }
    isElementNode(node /** TODO #9100 */) { throw 'not implemented'; }
    hasShadowRoot(node /** TODO #9100 */) { throw 'not implemented'; }
    isShadowRoot(node /** TODO #9100 */) { throw 'not implemented'; }
    importIntoDoc(node) { throw 'not implemented'; }
    adoptNode(node) { throw 'not implemented'; }
    getHref(element /** TODO #9100 */) { throw 'not implemented'; }
    getEventKey(event /** TODO #9100 */) { throw 'not implemented'; }
    resolveAndSetHref(element /** TODO #9100 */, baseUrl, href) {
        throw 'not implemented';
    }
    supportsDOMEvents() { throw 'not implemented'; }
    supportsNativeShadowDOM() { throw 'not implemented'; }
    getGlobalEventTarget(target) { throw 'not implemented'; }
    getHistory() { throw 'not implemented'; }
    getLocation() { throw 'not implemented'; }
    getBaseHref() { throw 'not implemented'; }
    resetBaseElement() { throw 'not implemented'; }
    getUserAgent() { throw 'not implemented'; }
    setData(element /** TODO #9100 */, name, value) { throw 'not implemented'; }
    getComputedStyle(element /** TODO #9100 */) { throw 'not implemented'; }
    getData(element /** TODO #9100 */, name) { throw 'not implemented'; }
    setGlobalVar(name, value) { throw 'not implemented'; }
    requestAnimationFrame(callback /** TODO #9100 */) { throw 'not implemented'; }
    cancelAnimationFrame(id /** TODO #9100 */) { throw 'not implemented'; }
    performanceNow() { throw 'not implemented'; }
    getAnimationPrefix() { throw 'not implemented'; }
    getTransitionEnd() { throw 'not implemented'; }
    supportsAnimation() { throw 'not implemented'; }
    supportsWebAnimation() { throw 'not implemented'; }
    supportsCookies() { return false; }
    getCookie(name) { throw 'not implemented'; }
    setCookie(name, value) { throw 'not implemented'; }
}
//# sourceMappingURL=worker_adapter.js.map