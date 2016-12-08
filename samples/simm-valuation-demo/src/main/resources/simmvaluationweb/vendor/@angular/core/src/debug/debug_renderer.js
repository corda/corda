/**
 * @license
 * Copyright Google Inc. All Rights Reserved.
 *
 * Use of this source code is governed by an MIT-style license that can be
 * found in the LICENSE file at https://angular.io/license
 */
"use strict";
var lang_1 = require('../facade/lang');
var debug_node_1 = require('./debug_node');
var DebugDomRootRenderer = (function () {
    function DebugDomRootRenderer(_delegate) {
        this._delegate = _delegate;
    }
    DebugDomRootRenderer.prototype.renderComponent = function (componentProto) {
        return new DebugDomRenderer(this._delegate.renderComponent(componentProto));
    };
    return DebugDomRootRenderer;
}());
exports.DebugDomRootRenderer = DebugDomRootRenderer;
var DebugDomRenderer = (function () {
    function DebugDomRenderer(_delegate) {
        this._delegate = _delegate;
    }
    DebugDomRenderer.prototype.selectRootElement = function (selectorOrNode, debugInfo) {
        var nativeEl = this._delegate.selectRootElement(selectorOrNode, debugInfo);
        var debugEl = new debug_node_1.DebugElement(nativeEl, null, debugInfo);
        debug_node_1.indexDebugNode(debugEl);
        return nativeEl;
    };
    DebugDomRenderer.prototype.createElement = function (parentElement, name, debugInfo) {
        var nativeEl = this._delegate.createElement(parentElement, name, debugInfo);
        var debugEl = new debug_node_1.DebugElement(nativeEl, debug_node_1.getDebugNode(parentElement), debugInfo);
        debugEl.name = name;
        debug_node_1.indexDebugNode(debugEl);
        return nativeEl;
    };
    DebugDomRenderer.prototype.createViewRoot = function (hostElement) { return this._delegate.createViewRoot(hostElement); };
    DebugDomRenderer.prototype.createTemplateAnchor = function (parentElement, debugInfo) {
        var comment = this._delegate.createTemplateAnchor(parentElement, debugInfo);
        var debugEl = new debug_node_1.DebugNode(comment, debug_node_1.getDebugNode(parentElement), debugInfo);
        debug_node_1.indexDebugNode(debugEl);
        return comment;
    };
    DebugDomRenderer.prototype.createText = function (parentElement, value, debugInfo) {
        var text = this._delegate.createText(parentElement, value, debugInfo);
        var debugEl = new debug_node_1.DebugNode(text, debug_node_1.getDebugNode(parentElement), debugInfo);
        debug_node_1.indexDebugNode(debugEl);
        return text;
    };
    DebugDomRenderer.prototype.projectNodes = function (parentElement, nodes) {
        var debugParent = debug_node_1.getDebugNode(parentElement);
        if (lang_1.isPresent(debugParent) && debugParent instanceof debug_node_1.DebugElement) {
            var debugElement_1 = debugParent;
            nodes.forEach(function (node) { debugElement_1.addChild(debug_node_1.getDebugNode(node)); });
        }
        this._delegate.projectNodes(parentElement, nodes);
    };
    DebugDomRenderer.prototype.attachViewAfter = function (node, viewRootNodes) {
        var debugNode = debug_node_1.getDebugNode(node);
        if (lang_1.isPresent(debugNode)) {
            var debugParent = debugNode.parent;
            if (viewRootNodes.length > 0 && lang_1.isPresent(debugParent)) {
                var debugViewRootNodes = [];
                viewRootNodes.forEach(function (rootNode) { return debugViewRootNodes.push(debug_node_1.getDebugNode(rootNode)); });
                debugParent.insertChildrenAfter(debugNode, debugViewRootNodes);
            }
        }
        this._delegate.attachViewAfter(node, viewRootNodes);
    };
    DebugDomRenderer.prototype.detachView = function (viewRootNodes) {
        viewRootNodes.forEach(function (node) {
            var debugNode = debug_node_1.getDebugNode(node);
            if (lang_1.isPresent(debugNode) && lang_1.isPresent(debugNode.parent)) {
                debugNode.parent.removeChild(debugNode);
            }
        });
        this._delegate.detachView(viewRootNodes);
    };
    DebugDomRenderer.prototype.destroyView = function (hostElement, viewAllNodes) {
        viewAllNodes.forEach(function (node) { debug_node_1.removeDebugNodeFromIndex(debug_node_1.getDebugNode(node)); });
        this._delegate.destroyView(hostElement, viewAllNodes);
    };
    DebugDomRenderer.prototype.listen = function (renderElement, name, callback) {
        var debugEl = debug_node_1.getDebugNode(renderElement);
        if (lang_1.isPresent(debugEl)) {
            debugEl.listeners.push(new debug_node_1.EventListener(name, callback));
        }
        return this._delegate.listen(renderElement, name, callback);
    };
    DebugDomRenderer.prototype.listenGlobal = function (target, name, callback) {
        return this._delegate.listenGlobal(target, name, callback);
    };
    DebugDomRenderer.prototype.setElementProperty = function (renderElement, propertyName, propertyValue) {
        var debugEl = debug_node_1.getDebugNode(renderElement);
        if (lang_1.isPresent(debugEl) && debugEl instanceof debug_node_1.DebugElement) {
            debugEl.properties[propertyName] = propertyValue;
        }
        this._delegate.setElementProperty(renderElement, propertyName, propertyValue);
    };
    DebugDomRenderer.prototype.setElementAttribute = function (renderElement, attributeName, attributeValue) {
        var debugEl = debug_node_1.getDebugNode(renderElement);
        if (lang_1.isPresent(debugEl) && debugEl instanceof debug_node_1.DebugElement) {
            debugEl.attributes[attributeName] = attributeValue;
        }
        this._delegate.setElementAttribute(renderElement, attributeName, attributeValue);
    };
    DebugDomRenderer.prototype.setBindingDebugInfo = function (renderElement, propertyName, propertyValue) {
        this._delegate.setBindingDebugInfo(renderElement, propertyName, propertyValue);
    };
    DebugDomRenderer.prototype.setElementClass = function (renderElement, className, isAdd) {
        var debugEl = debug_node_1.getDebugNode(renderElement);
        if (lang_1.isPresent(debugEl) && debugEl instanceof debug_node_1.DebugElement) {
            debugEl.classes[className] = isAdd;
        }
        this._delegate.setElementClass(renderElement, className, isAdd);
    };
    DebugDomRenderer.prototype.setElementStyle = function (renderElement, styleName, styleValue) {
        var debugEl = debug_node_1.getDebugNode(renderElement);
        if (lang_1.isPresent(debugEl) && debugEl instanceof debug_node_1.DebugElement) {
            debugEl.styles[styleName] = styleValue;
        }
        this._delegate.setElementStyle(renderElement, styleName, styleValue);
    };
    DebugDomRenderer.prototype.invokeElementMethod = function (renderElement, methodName, args) {
        this._delegate.invokeElementMethod(renderElement, methodName, args);
    };
    DebugDomRenderer.prototype.setText = function (renderNode, text) { this._delegate.setText(renderNode, text); };
    DebugDomRenderer.prototype.animate = function (element, startingStyles, keyframes, duration, delay, easing) {
        return this._delegate.animate(element, startingStyles, keyframes, duration, delay, easing);
    };
    return DebugDomRenderer;
}());
exports.DebugDomRenderer = DebugDomRenderer;
//# sourceMappingURL=debug_renderer.js.map