/**
 * @license
 * Copyright Google Inc. All Rights Reserved.
 *
 * Use of this source code is governed by an MIT-style license that can be
 * found in the LICENSE file at https://angular.io/license
 */
import { Injectable, RenderComponentType, ViewEncapsulation } from '@angular/core';
import { ObservableWrapper } from '../../facade/async';
import { ListWrapper } from '../../facade/collection';
import { isBlank, isPresent } from '../../facade/lang';
import { ClientMessageBrokerFactory, FnArg, UiArguments } from '../shared/client_message_broker';
import { MessageBus } from '../shared/message_bus';
import { EVENT_CHANNEL, RENDERER_CHANNEL } from '../shared/messaging_api';
import { RenderStore } from '../shared/render_store';
import { RenderStoreObject, Serializer } from '../shared/serializer';
import { deserializeGenericEvent } from './event_deserializer';
export class WebWorkerRootRenderer {
    constructor(messageBrokerFactory, bus, _serializer, _renderStore) {
        this._serializer = _serializer;
        this._renderStore = _renderStore;
        this.globalEvents = new NamedEventEmitter();
        this._componentRenderers = new Map();
        this._messageBroker = messageBrokerFactory.createMessageBroker(RENDERER_CHANNEL);
        bus.initChannel(EVENT_CHANNEL);
        var source = bus.from(EVENT_CHANNEL);
        ObservableWrapper.subscribe(source, (message) => this._dispatchEvent(message));
    }
    _dispatchEvent(message) {
        var eventName = message['eventName'];
        var target = message['eventTarget'];
        var event = deserializeGenericEvent(message['event']);
        if (isPresent(target)) {
            this.globalEvents.dispatchEvent(eventNameWithTarget(target, eventName), event);
        }
        else {
            var element = this._serializer.deserialize(message['element'], RenderStoreObject);
            element.events.dispatchEvent(eventName, event);
        }
    }
    renderComponent(componentType) {
        var result = this._componentRenderers.get(componentType.id);
        if (isBlank(result)) {
            result = new WebWorkerRenderer(this, componentType);
            this._componentRenderers.set(componentType.id, result);
            var id = this._renderStore.allocateId();
            this._renderStore.store(result, id);
            this.runOnService('renderComponent', [
                new FnArg(componentType, RenderComponentType),
                new FnArg(result, RenderStoreObject),
            ]);
        }
        return result;
    }
    runOnService(fnName, fnArgs) {
        var args = new UiArguments(fnName, fnArgs);
        this._messageBroker.runOnService(args, null);
    }
    allocateNode() {
        var result = new WebWorkerRenderNode();
        var id = this._renderStore.allocateId();
        this._renderStore.store(result, id);
        return result;
    }
    allocateId() { return this._renderStore.allocateId(); }
    destroyNodes(nodes) {
        for (var i = 0; i < nodes.length; i++) {
            this._renderStore.remove(nodes[i]);
        }
    }
}
/** @nocollapse */
WebWorkerRootRenderer.decorators = [
    { type: Injectable },
];
/** @nocollapse */
WebWorkerRootRenderer.ctorParameters = [
    { type: ClientMessageBrokerFactory, },
    { type: MessageBus, },
    { type: Serializer, },
    { type: RenderStore, },
];
export class WebWorkerRenderer {
    constructor(_rootRenderer, _componentType) {
        this._rootRenderer = _rootRenderer;
        this._componentType = _componentType;
    }
    _runOnService(fnName, fnArgs) {
        var fnArgsWithRenderer = [new FnArg(this, RenderStoreObject)].concat(fnArgs);
        this._rootRenderer.runOnService(fnName, fnArgsWithRenderer);
    }
    selectRootElement(selectorOrNode, debugInfo) {
        var node = this._rootRenderer.allocateNode();
        this._runOnService('selectRootElement', [new FnArg(selectorOrNode, null), new FnArg(node, RenderStoreObject)]);
        return node;
    }
    createElement(parentElement, name, debugInfo) {
        var node = this._rootRenderer.allocateNode();
        this._runOnService('createElement', [
            new FnArg(parentElement, RenderStoreObject), new FnArg(name, null),
            new FnArg(node, RenderStoreObject)
        ]);
        return node;
    }
    createViewRoot(hostElement) {
        var viewRoot = this._componentType.encapsulation === ViewEncapsulation.Native ?
            this._rootRenderer.allocateNode() :
            hostElement;
        this._runOnService('createViewRoot', [new FnArg(hostElement, RenderStoreObject), new FnArg(viewRoot, RenderStoreObject)]);
        return viewRoot;
    }
    createTemplateAnchor(parentElement, debugInfo) {
        var node = this._rootRenderer.allocateNode();
        this._runOnService('createTemplateAnchor', [new FnArg(parentElement, RenderStoreObject), new FnArg(node, RenderStoreObject)]);
        return node;
    }
    createText(parentElement, value, debugInfo) {
        var node = this._rootRenderer.allocateNode();
        this._runOnService('createText', [
            new FnArg(parentElement, RenderStoreObject), new FnArg(value, null),
            new FnArg(node, RenderStoreObject)
        ]);
        return node;
    }
    projectNodes(parentElement, nodes) {
        this._runOnService('projectNodes', [new FnArg(parentElement, RenderStoreObject), new FnArg(nodes, RenderStoreObject)]);
    }
    attachViewAfter(node, viewRootNodes) {
        this._runOnService('attachViewAfter', [new FnArg(node, RenderStoreObject), new FnArg(viewRootNodes, RenderStoreObject)]);
    }
    detachView(viewRootNodes) {
        this._runOnService('detachView', [new FnArg(viewRootNodes, RenderStoreObject)]);
    }
    destroyView(hostElement, viewAllNodes) {
        this._runOnService('destroyView', [new FnArg(hostElement, RenderStoreObject), new FnArg(viewAllNodes, RenderStoreObject)]);
        this._rootRenderer.destroyNodes(viewAllNodes);
    }
    setElementProperty(renderElement, propertyName, propertyValue) {
        this._runOnService('setElementProperty', [
            new FnArg(renderElement, RenderStoreObject), new FnArg(propertyName, null),
            new FnArg(propertyValue, null)
        ]);
    }
    setElementAttribute(renderElement, attributeName, attributeValue) {
        this._runOnService('setElementAttribute', [
            new FnArg(renderElement, RenderStoreObject), new FnArg(attributeName, null),
            new FnArg(attributeValue, null)
        ]);
    }
    setBindingDebugInfo(renderElement, propertyName, propertyValue) {
        this._runOnService('setBindingDebugInfo', [
            new FnArg(renderElement, RenderStoreObject), new FnArg(propertyName, null),
            new FnArg(propertyValue, null)
        ]);
    }
    setElementClass(renderElement, className, isAdd) {
        this._runOnService('setElementClass', [
            new FnArg(renderElement, RenderStoreObject), new FnArg(className, null),
            new FnArg(isAdd, null)
        ]);
    }
    setElementStyle(renderElement, styleName, styleValue) {
        this._runOnService('setElementStyle', [
            new FnArg(renderElement, RenderStoreObject), new FnArg(styleName, null),
            new FnArg(styleValue, null)
        ]);
    }
    invokeElementMethod(renderElement, methodName, args) {
        this._runOnService('invokeElementMethod', [
            new FnArg(renderElement, RenderStoreObject), new FnArg(methodName, null),
            new FnArg(args, null)
        ]);
    }
    setText(renderNode, text) {
        this._runOnService('setText', [new FnArg(renderNode, RenderStoreObject), new FnArg(text, null)]);
    }
    listen(renderElement, name, callback) {
        renderElement.events.listen(name, callback);
        var unlistenCallbackId = this._rootRenderer.allocateId();
        this._runOnService('listen', [
            new FnArg(renderElement, RenderStoreObject), new FnArg(name, null),
            new FnArg(unlistenCallbackId, null)
        ]);
        return () => {
            renderElement.events.unlisten(name, callback);
            this._runOnService('listenDone', [new FnArg(unlistenCallbackId, null)]);
        };
    }
    listenGlobal(target, name, callback) {
        this._rootRenderer.globalEvents.listen(eventNameWithTarget(target, name), callback);
        var unlistenCallbackId = this._rootRenderer.allocateId();
        this._runOnService('listenGlobal', [new FnArg(target, null), new FnArg(name, null), new FnArg(unlistenCallbackId, null)]);
        return () => {
            this._rootRenderer.globalEvents.unlisten(eventNameWithTarget(target, name), callback);
            this._runOnService('listenDone', [new FnArg(unlistenCallbackId, null)]);
        };
    }
    animate(element, startingStyles, keyframes, duration, delay, easing) {
        // TODO
        return null;
    }
}
export class NamedEventEmitter {
    _getListeners(eventName) {
        if (isBlank(this._listeners)) {
            this._listeners = new Map();
        }
        var listeners = this._listeners.get(eventName);
        if (isBlank(listeners)) {
            listeners = [];
            this._listeners.set(eventName, listeners);
        }
        return listeners;
    }
    listen(eventName, callback) { this._getListeners(eventName).push(callback); }
    unlisten(eventName, callback) {
        ListWrapper.remove(this._getListeners(eventName), callback);
    }
    dispatchEvent(eventName, event) {
        var listeners = this._getListeners(eventName);
        for (var i = 0; i < listeners.length; i++) {
            listeners[i](event);
        }
    }
}
function eventNameWithTarget(target, eventName) {
    return `${target}:${eventName}`;
}
export class WebWorkerRenderNode {
    constructor() {
        this.events = new NamedEventEmitter();
    }
}
//# sourceMappingURL=renderer.js.map