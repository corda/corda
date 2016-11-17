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
var core_1 = require('@angular/core');
var exceptions_1 = require('../facade/exceptions');
var lang_1 = require('../facade/lang');
var shared_styles_host_1 = require('./shared_styles_host');
var core_private_1 = require('../../core_private');
var event_manager_1 = require('./events/event_manager');
var dom_tokens_1 = require('./dom_tokens');
var dom_adapter_1 = require('./dom_adapter');
var util_1 = require('./util');
var NAMESPACE_URIS = {
    'xlink': 'http://www.w3.org/1999/xlink',
    'svg': 'http://www.w3.org/2000/svg',
    'xhtml': 'http://www.w3.org/1999/xhtml'
};
var TEMPLATE_COMMENT_TEXT = 'template bindings={}';
var TEMPLATE_BINDINGS_EXP = /^template bindings=(.*)$/g;
var DomRootRenderer = (function () {
    function DomRootRenderer(document, eventManager, sharedStylesHost, animationDriver) {
        this.document = document;
        this.eventManager = eventManager;
        this.sharedStylesHost = sharedStylesHost;
        this.animationDriver = animationDriver;
        this.registeredComponents = new Map();
    }
    DomRootRenderer.prototype.renderComponent = function (componentProto) {
        var renderer = this.registeredComponents.get(componentProto.id);
        if (lang_1.isBlank(renderer)) {
            renderer = new DomRenderer(this, componentProto, this.animationDriver);
            this.registeredComponents.set(componentProto.id, renderer);
        }
        return renderer;
    };
    return DomRootRenderer;
}());
exports.DomRootRenderer = DomRootRenderer;
var DomRootRenderer_ = (function (_super) {
    __extends(DomRootRenderer_, _super);
    function DomRootRenderer_(_document, _eventManager, sharedStylesHost, animationDriver) {
        _super.call(this, _document, _eventManager, sharedStylesHost, animationDriver);
    }
    /** @nocollapse */
    DomRootRenderer_.decorators = [
        { type: core_1.Injectable },
    ];
    /** @nocollapse */
    DomRootRenderer_.ctorParameters = [
        { type: undefined, decorators: [{ type: core_1.Inject, args: [dom_tokens_1.DOCUMENT,] },] },
        { type: event_manager_1.EventManager, },
        { type: shared_styles_host_1.DomSharedStylesHost, },
        { type: core_private_1.AnimationDriver, },
    ];
    return DomRootRenderer_;
}(DomRootRenderer));
exports.DomRootRenderer_ = DomRootRenderer_;
var DomRenderer = (function () {
    function DomRenderer(_rootRenderer, componentProto, _animationDriver) {
        this._rootRenderer = _rootRenderer;
        this.componentProto = componentProto;
        this._animationDriver = _animationDriver;
        this._styles = _flattenStyles(componentProto.id, componentProto.styles, []);
        if (componentProto.encapsulation !== core_1.ViewEncapsulation.Native) {
            this._rootRenderer.sharedStylesHost.addStyles(this._styles);
        }
        if (this.componentProto.encapsulation === core_1.ViewEncapsulation.Emulated) {
            this._contentAttr = _shimContentAttribute(componentProto.id);
            this._hostAttr = _shimHostAttribute(componentProto.id);
        }
        else {
            this._contentAttr = null;
            this._hostAttr = null;
        }
    }
    DomRenderer.prototype.selectRootElement = function (selectorOrNode, debugInfo) {
        var el;
        if (lang_1.isString(selectorOrNode)) {
            el = dom_adapter_1.getDOM().querySelector(this._rootRenderer.document, selectorOrNode);
            if (lang_1.isBlank(el)) {
                throw new exceptions_1.BaseException("The selector \"" + selectorOrNode + "\" did not match any elements");
            }
        }
        else {
            el = selectorOrNode;
        }
        dom_adapter_1.getDOM().clearNodes(el);
        return el;
    };
    DomRenderer.prototype.createElement = function (parent, name, debugInfo) {
        var nsAndName = splitNamespace(name);
        var el = lang_1.isPresent(nsAndName[0]) ?
            dom_adapter_1.getDOM().createElementNS(NAMESPACE_URIS[nsAndName[0]], nsAndName[1]) :
            dom_adapter_1.getDOM().createElement(nsAndName[1]);
        if (lang_1.isPresent(this._contentAttr)) {
            dom_adapter_1.getDOM().setAttribute(el, this._contentAttr, '');
        }
        if (lang_1.isPresent(parent)) {
            dom_adapter_1.getDOM().appendChild(parent, el);
        }
        return el;
    };
    DomRenderer.prototype.createViewRoot = function (hostElement) {
        var nodesParent;
        if (this.componentProto.encapsulation === core_1.ViewEncapsulation.Native) {
            nodesParent = dom_adapter_1.getDOM().createShadowRoot(hostElement);
            this._rootRenderer.sharedStylesHost.addHost(nodesParent);
            for (var i = 0; i < this._styles.length; i++) {
                dom_adapter_1.getDOM().appendChild(nodesParent, dom_adapter_1.getDOM().createStyleElement(this._styles[i]));
            }
        }
        else {
            if (lang_1.isPresent(this._hostAttr)) {
                dom_adapter_1.getDOM().setAttribute(hostElement, this._hostAttr, '');
            }
            nodesParent = hostElement;
        }
        return nodesParent;
    };
    DomRenderer.prototype.createTemplateAnchor = function (parentElement, debugInfo) {
        var comment = dom_adapter_1.getDOM().createComment(TEMPLATE_COMMENT_TEXT);
        if (lang_1.isPresent(parentElement)) {
            dom_adapter_1.getDOM().appendChild(parentElement, comment);
        }
        return comment;
    };
    DomRenderer.prototype.createText = function (parentElement, value, debugInfo) {
        var node = dom_adapter_1.getDOM().createTextNode(value);
        if (lang_1.isPresent(parentElement)) {
            dom_adapter_1.getDOM().appendChild(parentElement, node);
        }
        return node;
    };
    DomRenderer.prototype.projectNodes = function (parentElement, nodes) {
        if (lang_1.isBlank(parentElement))
            return;
        appendNodes(parentElement, nodes);
    };
    DomRenderer.prototype.attachViewAfter = function (node, viewRootNodes) { moveNodesAfterSibling(node, viewRootNodes); };
    DomRenderer.prototype.detachView = function (viewRootNodes) {
        for (var i = 0; i < viewRootNodes.length; i++) {
            dom_adapter_1.getDOM().remove(viewRootNodes[i]);
        }
    };
    DomRenderer.prototype.destroyView = function (hostElement, viewAllNodes) {
        if (this.componentProto.encapsulation === core_1.ViewEncapsulation.Native && lang_1.isPresent(hostElement)) {
            this._rootRenderer.sharedStylesHost.removeHost(dom_adapter_1.getDOM().getShadowRoot(hostElement));
        }
    };
    DomRenderer.prototype.listen = function (renderElement, name, callback) {
        return this._rootRenderer.eventManager.addEventListener(renderElement, name, decoratePreventDefault(callback));
    };
    DomRenderer.prototype.listenGlobal = function (target, name, callback) {
        return this._rootRenderer.eventManager.addGlobalEventListener(target, name, decoratePreventDefault(callback));
    };
    DomRenderer.prototype.setElementProperty = function (renderElement, propertyName, propertyValue) {
        dom_adapter_1.getDOM().setProperty(renderElement, propertyName, propertyValue);
    };
    DomRenderer.prototype.setElementAttribute = function (renderElement, attributeName, attributeValue) {
        var attrNs;
        var nsAndName = splitNamespace(attributeName);
        if (lang_1.isPresent(nsAndName[0])) {
            attributeName = nsAndName[0] + ':' + nsAndName[1];
            attrNs = NAMESPACE_URIS[nsAndName[0]];
        }
        if (lang_1.isPresent(attributeValue)) {
            if (lang_1.isPresent(attrNs)) {
                dom_adapter_1.getDOM().setAttributeNS(renderElement, attrNs, attributeName, attributeValue);
            }
            else {
                dom_adapter_1.getDOM().setAttribute(renderElement, attributeName, attributeValue);
            }
        }
        else {
            if (lang_1.isPresent(attrNs)) {
                dom_adapter_1.getDOM().removeAttributeNS(renderElement, attrNs, nsAndName[1]);
            }
            else {
                dom_adapter_1.getDOM().removeAttribute(renderElement, attributeName);
            }
        }
    };
    DomRenderer.prototype.setBindingDebugInfo = function (renderElement, propertyName, propertyValue) {
        var dashCasedPropertyName = util_1.camelCaseToDashCase(propertyName);
        if (dom_adapter_1.getDOM().isCommentNode(renderElement)) {
            var existingBindings = lang_1.RegExpWrapper.firstMatch(TEMPLATE_BINDINGS_EXP, lang_1.StringWrapper.replaceAll(dom_adapter_1.getDOM().getText(renderElement), /\n/g, ''));
            var parsedBindings = lang_1.Json.parse(existingBindings[1]);
            parsedBindings[dashCasedPropertyName] = propertyValue;
            dom_adapter_1.getDOM().setText(renderElement, lang_1.StringWrapper.replace(TEMPLATE_COMMENT_TEXT, '{}', lang_1.Json.stringify(parsedBindings)));
        }
        else {
            this.setElementAttribute(renderElement, propertyName, propertyValue);
        }
    };
    DomRenderer.prototype.setElementClass = function (renderElement, className, isAdd) {
        if (isAdd) {
            dom_adapter_1.getDOM().addClass(renderElement, className);
        }
        else {
            dom_adapter_1.getDOM().removeClass(renderElement, className);
        }
    };
    DomRenderer.prototype.setElementStyle = function (renderElement, styleName, styleValue) {
        if (lang_1.isPresent(styleValue)) {
            dom_adapter_1.getDOM().setStyle(renderElement, styleName, lang_1.stringify(styleValue));
        }
        else {
            dom_adapter_1.getDOM().removeStyle(renderElement, styleName);
        }
    };
    DomRenderer.prototype.invokeElementMethod = function (renderElement, methodName, args) {
        dom_adapter_1.getDOM().invoke(renderElement, methodName, args);
    };
    DomRenderer.prototype.setText = function (renderNode, text) { dom_adapter_1.getDOM().setText(renderNode, text); };
    DomRenderer.prototype.animate = function (element, startingStyles, keyframes, duration, delay, easing) {
        return this._animationDriver.animate(element, startingStyles, keyframes, duration, delay, easing);
    };
    return DomRenderer;
}());
exports.DomRenderer = DomRenderer;
function moveNodesAfterSibling(sibling /** TODO #9100 */, nodes /** TODO #9100 */) {
    var parent = dom_adapter_1.getDOM().parentElement(sibling);
    if (nodes.length > 0 && lang_1.isPresent(parent)) {
        var nextSibling = dom_adapter_1.getDOM().nextSibling(sibling);
        if (lang_1.isPresent(nextSibling)) {
            for (var i = 0; i < nodes.length; i++) {
                dom_adapter_1.getDOM().insertBefore(nextSibling, nodes[i]);
            }
        }
        else {
            for (var i = 0; i < nodes.length; i++) {
                dom_adapter_1.getDOM().appendChild(parent, nodes[i]);
            }
        }
    }
}
function appendNodes(parent /** TODO #9100 */, nodes /** TODO #9100 */) {
    for (var i = 0; i < nodes.length; i++) {
        dom_adapter_1.getDOM().appendChild(parent, nodes[i]);
    }
}
function decoratePreventDefault(eventHandler) {
    return function (event /** TODO #9100 */) {
        var allowDefaultBehavior = eventHandler(event);
        if (allowDefaultBehavior === false) {
            // TODO(tbosch): move preventDefault into event plugins...
            dom_adapter_1.getDOM().preventDefault(event);
        }
    };
}
var COMPONENT_REGEX = /%COMP%/g;
exports.COMPONENT_VARIABLE = '%COMP%';
exports.HOST_ATTR = "_nghost-" + exports.COMPONENT_VARIABLE;
exports.CONTENT_ATTR = "_ngcontent-" + exports.COMPONENT_VARIABLE;
function _shimContentAttribute(componentShortId) {
    return lang_1.StringWrapper.replaceAll(exports.CONTENT_ATTR, COMPONENT_REGEX, componentShortId);
}
function _shimHostAttribute(componentShortId) {
    return lang_1.StringWrapper.replaceAll(exports.HOST_ATTR, COMPONENT_REGEX, componentShortId);
}
function _flattenStyles(compId, styles, target) {
    for (var i = 0; i < styles.length; i++) {
        var style = styles[i];
        if (lang_1.isArray(style)) {
            _flattenStyles(compId, style, target);
        }
        else {
            style = lang_1.StringWrapper.replaceAll(style, COMPONENT_REGEX, compId);
            target.push(style);
        }
    }
    return target;
}
var NS_PREFIX_RE = /^:([^:]+):(.+)/g;
function splitNamespace(name) {
    if (name[0] != ':') {
        return [null, name];
    }
    var match = lang_1.RegExpWrapper.firstMatch(NS_PREFIX_RE, name);
    return [match[1], match[2]];
}
//# sourceMappingURL=dom_renderer.js.map