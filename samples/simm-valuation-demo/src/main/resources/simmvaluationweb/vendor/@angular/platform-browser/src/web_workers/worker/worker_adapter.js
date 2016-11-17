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
var dom_adapter_1 = require('../../dom/dom_adapter');
/**
 * This adapter is required to log error messages.
 *
 * Note: other methods all throw as the DOM is not accessible directly in web worker context.
 */
var WorkerDomAdapter = (function (_super) {
    __extends(WorkerDomAdapter, _super);
    function WorkerDomAdapter() {
        _super.apply(this, arguments);
    }
    WorkerDomAdapter.makeCurrent = function () { dom_adapter_1.setRootDomAdapter(new WorkerDomAdapter()); };
    WorkerDomAdapter.prototype.logError = function (error /** TODO #9100 */) {
        if (console.error) {
            console.error(error);
        }
        else {
            console.log(error);
        }
    };
    WorkerDomAdapter.prototype.log = function (error /** TODO #9100 */) { console.log(error); };
    WorkerDomAdapter.prototype.logGroup = function (error /** TODO #9100 */) {
        if (console.group) {
            console.group(error);
            this.logError(error);
        }
        else {
            console.log(error);
        }
    };
    WorkerDomAdapter.prototype.logGroupEnd = function () {
        if (console.groupEnd) {
            console.groupEnd();
        }
    };
    WorkerDomAdapter.prototype.hasProperty = function (element /** TODO #9100 */, name) { throw 'not implemented'; };
    WorkerDomAdapter.prototype.setProperty = function (el, name, value) { throw 'not implemented'; };
    WorkerDomAdapter.prototype.getProperty = function (el, name) { throw 'not implemented'; };
    WorkerDomAdapter.prototype.invoke = function (el, methodName, args) { throw 'not implemented'; };
    WorkerDomAdapter.prototype.getXHR = function () { throw 'not implemented'; };
    Object.defineProperty(WorkerDomAdapter.prototype, "attrToPropMap", {
        get: function () { throw 'not implemented'; },
        set: function (value) { throw 'not implemented'; },
        enumerable: true,
        configurable: true
    });
    WorkerDomAdapter.prototype.parse = function (templateHtml) { throw 'not implemented'; };
    WorkerDomAdapter.prototype.query = function (selector) { throw 'not implemented'; };
    WorkerDomAdapter.prototype.querySelector = function (el /** TODO #9100 */, selector) {
        throw 'not implemented';
    };
    WorkerDomAdapter.prototype.querySelectorAll = function (el /** TODO #9100 */, selector) { throw 'not implemented'; };
    WorkerDomAdapter.prototype.on = function (el /** TODO #9100 */, evt /** TODO #9100 */, listener /** TODO #9100 */) {
        throw 'not implemented';
    };
    WorkerDomAdapter.prototype.onAndCancel = function (el /** TODO #9100 */, evt /** TODO #9100 */, listener /** TODO #9100 */) {
        throw 'not implemented';
    };
    WorkerDomAdapter.prototype.dispatchEvent = function (el /** TODO #9100 */, evt /** TODO #9100 */) { throw 'not implemented'; };
    WorkerDomAdapter.prototype.createMouseEvent = function (eventType /** TODO #9100 */) { throw 'not implemented'; };
    WorkerDomAdapter.prototype.createEvent = function (eventType) { throw 'not implemented'; };
    WorkerDomAdapter.prototype.preventDefault = function (evt /** TODO #9100 */) { throw 'not implemented'; };
    WorkerDomAdapter.prototype.isPrevented = function (evt /** TODO #9100 */) { throw 'not implemented'; };
    WorkerDomAdapter.prototype.getInnerHTML = function (el /** TODO #9100 */) { throw 'not implemented'; };
    WorkerDomAdapter.prototype.getTemplateContent = function (el /** TODO #9100 */) { throw 'not implemented'; };
    WorkerDomAdapter.prototype.getOuterHTML = function (el /** TODO #9100 */) { throw 'not implemented'; };
    WorkerDomAdapter.prototype.nodeName = function (node /** TODO #9100 */) { throw 'not implemented'; };
    WorkerDomAdapter.prototype.nodeValue = function (node /** TODO #9100 */) { throw 'not implemented'; };
    WorkerDomAdapter.prototype.type = function (node /** TODO #9100 */) { throw 'not implemented'; };
    WorkerDomAdapter.prototype.content = function (node /** TODO #9100 */) { throw 'not implemented'; };
    WorkerDomAdapter.prototype.firstChild = function (el /** TODO #9100 */) { throw 'not implemented'; };
    WorkerDomAdapter.prototype.nextSibling = function (el /** TODO #9100 */) { throw 'not implemented'; };
    WorkerDomAdapter.prototype.parentElement = function (el /** TODO #9100 */) { throw 'not implemented'; };
    WorkerDomAdapter.prototype.childNodes = function (el /** TODO #9100 */) { throw 'not implemented'; };
    WorkerDomAdapter.prototype.childNodesAsList = function (el /** TODO #9100 */) { throw 'not implemented'; };
    WorkerDomAdapter.prototype.clearNodes = function (el /** TODO #9100 */) { throw 'not implemented'; };
    WorkerDomAdapter.prototype.appendChild = function (el /** TODO #9100 */, node /** TODO #9100 */) { throw 'not implemented'; };
    WorkerDomAdapter.prototype.removeChild = function (el /** TODO #9100 */, node /** TODO #9100 */) { throw 'not implemented'; };
    WorkerDomAdapter.prototype.replaceChild = function (el /** TODO #9100 */, newNode /** TODO #9100 */, oldNode /** TODO #9100 */) {
        throw 'not implemented';
    };
    WorkerDomAdapter.prototype.remove = function (el /** TODO #9100 */) { throw 'not implemented'; };
    WorkerDomAdapter.prototype.insertBefore = function (el /** TODO #9100 */, node /** TODO #9100 */) { throw 'not implemented'; };
    WorkerDomAdapter.prototype.insertAllBefore = function (el /** TODO #9100 */, nodes /** TODO #9100 */) {
        throw 'not implemented';
    };
    WorkerDomAdapter.prototype.insertAfter = function (el /** TODO #9100 */, node /** TODO #9100 */) { throw 'not implemented'; };
    WorkerDomAdapter.prototype.setInnerHTML = function (el /** TODO #9100 */, value /** TODO #9100 */) { throw 'not implemented'; };
    WorkerDomAdapter.prototype.getText = function (el /** TODO #9100 */) { throw 'not implemented'; };
    WorkerDomAdapter.prototype.setText = function (el /** TODO #9100 */, value) { throw 'not implemented'; };
    WorkerDomAdapter.prototype.getValue = function (el /** TODO #9100 */) { throw 'not implemented'; };
    WorkerDomAdapter.prototype.setValue = function (el /** TODO #9100 */, value) { throw 'not implemented'; };
    WorkerDomAdapter.prototype.getChecked = function (el /** TODO #9100 */) { throw 'not implemented'; };
    WorkerDomAdapter.prototype.setChecked = function (el /** TODO #9100 */, value) { throw 'not implemented'; };
    WorkerDomAdapter.prototype.createComment = function (text) { throw 'not implemented'; };
    WorkerDomAdapter.prototype.createTemplate = function (html /** TODO #9100 */) { throw 'not implemented'; };
    WorkerDomAdapter.prototype.createElement = function (tagName /** TODO #9100 */, doc /** TODO #9100 */) {
        throw 'not implemented';
    };
    WorkerDomAdapter.prototype.createElementNS = function (ns, tagName, doc /** TODO #9100 */) {
        throw 'not implemented';
    };
    WorkerDomAdapter.prototype.createTextNode = function (text, doc /** TODO #9100 */) { throw 'not implemented'; };
    WorkerDomAdapter.prototype.createScriptTag = function (attrName, attrValue, doc /** TODO #9100 */) {
        throw 'not implemented';
    };
    WorkerDomAdapter.prototype.createStyleElement = function (css, doc /** TODO #9100 */) {
        throw 'not implemented';
    };
    WorkerDomAdapter.prototype.createShadowRoot = function (el /** TODO #9100 */) { throw 'not implemented'; };
    WorkerDomAdapter.prototype.getShadowRoot = function (el /** TODO #9100 */) { throw 'not implemented'; };
    WorkerDomAdapter.prototype.getHost = function (el /** TODO #9100 */) { throw 'not implemented'; };
    WorkerDomAdapter.prototype.getDistributedNodes = function (el /** TODO #9100 */) { throw 'not implemented'; };
    WorkerDomAdapter.prototype.clone = function (node) { throw 'not implemented'; };
    WorkerDomAdapter.prototype.getElementsByClassName = function (element /** TODO #9100 */, name) {
        throw 'not implemented';
    };
    WorkerDomAdapter.prototype.getElementsByTagName = function (element /** TODO #9100 */, name) {
        throw 'not implemented';
    };
    WorkerDomAdapter.prototype.classList = function (element /** TODO #9100 */) { throw 'not implemented'; };
    WorkerDomAdapter.prototype.addClass = function (element /** TODO #9100 */, className) { throw 'not implemented'; };
    WorkerDomAdapter.prototype.removeClass = function (element /** TODO #9100 */, className) { throw 'not implemented'; };
    WorkerDomAdapter.prototype.hasClass = function (element /** TODO #9100 */, className) { throw 'not implemented'; };
    WorkerDomAdapter.prototype.setStyle = function (element /** TODO #9100 */, styleName, styleValue) {
        throw 'not implemented';
    };
    WorkerDomAdapter.prototype.removeStyle = function (element /** TODO #9100 */, styleName) { throw 'not implemented'; };
    WorkerDomAdapter.prototype.getStyle = function (element /** TODO #9100 */, styleName) { throw 'not implemented'; };
    WorkerDomAdapter.prototype.hasStyle = function (element /** TODO #9100 */, styleName, styleValue) {
        throw 'not implemented';
    };
    WorkerDomAdapter.prototype.tagName = function (element /** TODO #9100 */) { throw 'not implemented'; };
    WorkerDomAdapter.prototype.attributeMap = function (element /** TODO #9100 */) { throw 'not implemented'; };
    WorkerDomAdapter.prototype.hasAttribute = function (element /** TODO #9100 */, attribute) {
        throw 'not implemented';
    };
    WorkerDomAdapter.prototype.hasAttributeNS = function (element /** TODO #9100 */, ns, attribute) {
        throw 'not implemented';
    };
    WorkerDomAdapter.prototype.getAttribute = function (element /** TODO #9100 */, attribute) {
        throw 'not implemented';
    };
    WorkerDomAdapter.prototype.getAttributeNS = function (element /** TODO #9100 */, ns, attribute) {
        throw 'not implemented';
    };
    WorkerDomAdapter.prototype.setAttribute = function (element /** TODO #9100 */, name, value) {
        throw 'not implemented';
    };
    WorkerDomAdapter.prototype.setAttributeNS = function (element /** TODO #9100 */, ns, name, value) {
        throw 'not implemented';
    };
    WorkerDomAdapter.prototype.removeAttribute = function (element /** TODO #9100 */, attribute) { throw 'not implemented'; };
    WorkerDomAdapter.prototype.removeAttributeNS = function (element /** TODO #9100 */, ns, attribute) {
        throw 'not implemented';
    };
    WorkerDomAdapter.prototype.templateAwareRoot = function (el /** TODO #9100 */) { throw 'not implemented'; };
    WorkerDomAdapter.prototype.createHtmlDocument = function () { throw 'not implemented'; };
    WorkerDomAdapter.prototype.defaultDoc = function () { throw 'not implemented'; };
    WorkerDomAdapter.prototype.getBoundingClientRect = function (el /** TODO #9100 */) { throw 'not implemented'; };
    WorkerDomAdapter.prototype.getTitle = function () { throw 'not implemented'; };
    WorkerDomAdapter.prototype.setTitle = function (newTitle) { throw 'not implemented'; };
    WorkerDomAdapter.prototype.elementMatches = function (n /** TODO #9100 */, selector) { throw 'not implemented'; };
    WorkerDomAdapter.prototype.isTemplateElement = function (el) { throw 'not implemented'; };
    WorkerDomAdapter.prototype.isTextNode = function (node /** TODO #9100 */) { throw 'not implemented'; };
    WorkerDomAdapter.prototype.isCommentNode = function (node /** TODO #9100 */) { throw 'not implemented'; };
    WorkerDomAdapter.prototype.isElementNode = function (node /** TODO #9100 */) { throw 'not implemented'; };
    WorkerDomAdapter.prototype.hasShadowRoot = function (node /** TODO #9100 */) { throw 'not implemented'; };
    WorkerDomAdapter.prototype.isShadowRoot = function (node /** TODO #9100 */) { throw 'not implemented'; };
    WorkerDomAdapter.prototype.importIntoDoc = function (node) { throw 'not implemented'; };
    WorkerDomAdapter.prototype.adoptNode = function (node) { throw 'not implemented'; };
    WorkerDomAdapter.prototype.getHref = function (element /** TODO #9100 */) { throw 'not implemented'; };
    WorkerDomAdapter.prototype.getEventKey = function (event /** TODO #9100 */) { throw 'not implemented'; };
    WorkerDomAdapter.prototype.resolveAndSetHref = function (element /** TODO #9100 */, baseUrl, href) {
        throw 'not implemented';
    };
    WorkerDomAdapter.prototype.supportsDOMEvents = function () { throw 'not implemented'; };
    WorkerDomAdapter.prototype.supportsNativeShadowDOM = function () { throw 'not implemented'; };
    WorkerDomAdapter.prototype.getGlobalEventTarget = function (target) { throw 'not implemented'; };
    WorkerDomAdapter.prototype.getHistory = function () { throw 'not implemented'; };
    WorkerDomAdapter.prototype.getLocation = function () { throw 'not implemented'; };
    WorkerDomAdapter.prototype.getBaseHref = function () { throw 'not implemented'; };
    WorkerDomAdapter.prototype.resetBaseElement = function () { throw 'not implemented'; };
    WorkerDomAdapter.prototype.getUserAgent = function () { throw 'not implemented'; };
    WorkerDomAdapter.prototype.setData = function (element /** TODO #9100 */, name, value) { throw 'not implemented'; };
    WorkerDomAdapter.prototype.getComputedStyle = function (element /** TODO #9100 */) { throw 'not implemented'; };
    WorkerDomAdapter.prototype.getData = function (element /** TODO #9100 */, name) { throw 'not implemented'; };
    WorkerDomAdapter.prototype.setGlobalVar = function (name, value) { throw 'not implemented'; };
    WorkerDomAdapter.prototype.requestAnimationFrame = function (callback /** TODO #9100 */) { throw 'not implemented'; };
    WorkerDomAdapter.prototype.cancelAnimationFrame = function (id /** TODO #9100 */) { throw 'not implemented'; };
    WorkerDomAdapter.prototype.performanceNow = function () { throw 'not implemented'; };
    WorkerDomAdapter.prototype.getAnimationPrefix = function () { throw 'not implemented'; };
    WorkerDomAdapter.prototype.getTransitionEnd = function () { throw 'not implemented'; };
    WorkerDomAdapter.prototype.supportsAnimation = function () { throw 'not implemented'; };
    WorkerDomAdapter.prototype.supportsWebAnimation = function () { throw 'not implemented'; };
    WorkerDomAdapter.prototype.supportsCookies = function () { return false; };
    WorkerDomAdapter.prototype.getCookie = function (name) { throw 'not implemented'; };
    WorkerDomAdapter.prototype.setCookie = function (name, value) { throw 'not implemented'; };
    return WorkerDomAdapter;
}(dom_adapter_1.DomAdapter));
exports.WorkerDomAdapter = WorkerDomAdapter;
//# sourceMappingURL=worker_adapter.js.map