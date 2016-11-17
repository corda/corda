/**
 * @license
 * Copyright Google Inc. All Rights Reserved.
 *
 * Use of this source code is governed by an MIT-style license that can be
 * found in the LICENSE file at https://angular.io/license
 */
import { Injectable, RenderComponentType, RootRenderer } from '@angular/core';
import { FunctionWrapper } from '../../facade/lang';
import { MessageBus } from '../shared/message_bus';
import { EVENT_CHANNEL, RENDERER_CHANNEL } from '../shared/messaging_api';
import { RenderStore } from '../shared/render_store';
import { PRIMITIVE, RenderStoreObject, Serializer } from '../shared/serializer';
import { ServiceMessageBrokerFactory } from '../shared/service_message_broker';
import { EventDispatcher } from '../ui/event_dispatcher';
export class MessageBasedRenderer {
    constructor(_brokerFactory, _bus, _serializer, _renderStore, _rootRenderer) {
        this._brokerFactory = _brokerFactory;
        this._bus = _bus;
        this._serializer = _serializer;
        this._renderStore = _renderStore;
        this._rootRenderer = _rootRenderer;
    }
    start() {
        var broker = this._brokerFactory.createMessageBroker(RENDERER_CHANNEL);
        this._bus.initChannel(EVENT_CHANNEL);
        this._eventDispatcher = new EventDispatcher(this._bus.to(EVENT_CHANNEL), this._serializer);
        broker.registerMethod('renderComponent', [RenderComponentType, PRIMITIVE], FunctionWrapper.bind(this._renderComponent, this));
        broker.registerMethod('selectRootElement', [RenderStoreObject, PRIMITIVE, PRIMITIVE], FunctionWrapper.bind(this._selectRootElement, this));
        broker.registerMethod('createElement', [RenderStoreObject, RenderStoreObject, PRIMITIVE, PRIMITIVE], FunctionWrapper.bind(this._createElement, this));
        broker.registerMethod('createViewRoot', [RenderStoreObject, RenderStoreObject, PRIMITIVE], FunctionWrapper.bind(this._createViewRoot, this));
        broker.registerMethod('createTemplateAnchor', [RenderStoreObject, RenderStoreObject, PRIMITIVE], FunctionWrapper.bind(this._createTemplateAnchor, this));
        broker.registerMethod('createText', [RenderStoreObject, RenderStoreObject, PRIMITIVE, PRIMITIVE], FunctionWrapper.bind(this._createText, this));
        broker.registerMethod('projectNodes', [RenderStoreObject, RenderStoreObject, RenderStoreObject], FunctionWrapper.bind(this._projectNodes, this));
        broker.registerMethod('attachViewAfter', [RenderStoreObject, RenderStoreObject, RenderStoreObject], FunctionWrapper.bind(this._attachViewAfter, this));
        broker.registerMethod('detachView', [RenderStoreObject, RenderStoreObject], FunctionWrapper.bind(this._detachView, this));
        broker.registerMethod('destroyView', [RenderStoreObject, RenderStoreObject, RenderStoreObject], FunctionWrapper.bind(this._destroyView, this));
        broker.registerMethod('setElementProperty', [RenderStoreObject, RenderStoreObject, PRIMITIVE, PRIMITIVE], FunctionWrapper.bind(this._setElementProperty, this));
        broker.registerMethod('setElementAttribute', [RenderStoreObject, RenderStoreObject, PRIMITIVE, PRIMITIVE], FunctionWrapper.bind(this._setElementAttribute, this));
        broker.registerMethod('setBindingDebugInfo', [RenderStoreObject, RenderStoreObject, PRIMITIVE, PRIMITIVE], FunctionWrapper.bind(this._setBindingDebugInfo, this));
        broker.registerMethod('setElementClass', [RenderStoreObject, RenderStoreObject, PRIMITIVE, PRIMITIVE], FunctionWrapper.bind(this._setElementClass, this));
        broker.registerMethod('setElementStyle', [RenderStoreObject, RenderStoreObject, PRIMITIVE, PRIMITIVE], FunctionWrapper.bind(this._setElementStyle, this));
        broker.registerMethod('invokeElementMethod', [RenderStoreObject, RenderStoreObject, PRIMITIVE, PRIMITIVE], FunctionWrapper.bind(this._invokeElementMethod, this));
        broker.registerMethod('setText', [RenderStoreObject, RenderStoreObject, PRIMITIVE], FunctionWrapper.bind(this._setText, this));
        broker.registerMethod('listen', [RenderStoreObject, RenderStoreObject, PRIMITIVE, PRIMITIVE], FunctionWrapper.bind(this._listen, this));
        broker.registerMethod('listenGlobal', [RenderStoreObject, PRIMITIVE, PRIMITIVE, PRIMITIVE], FunctionWrapper.bind(this._listenGlobal, this));
        broker.registerMethod('listenDone', [RenderStoreObject, RenderStoreObject], FunctionWrapper.bind(this._listenDone, this));
    }
    _renderComponent(renderComponentType, rendererId) {
        var renderer = this._rootRenderer.renderComponent(renderComponentType);
        this._renderStore.store(renderer, rendererId);
    }
    _selectRootElement(renderer, selector, elId) {
        this._renderStore.store(renderer.selectRootElement(selector, null), elId);
    }
    _createElement(renderer, parentElement, name, elId) {
        this._renderStore.store(renderer.createElement(parentElement, name, null), elId);
    }
    _createViewRoot(renderer, hostElement, elId) {
        var viewRoot = renderer.createViewRoot(hostElement);
        if (this._renderStore.serialize(hostElement) !== elId) {
            this._renderStore.store(viewRoot, elId);
        }
    }
    _createTemplateAnchor(renderer, parentElement, elId) {
        this._renderStore.store(renderer.createTemplateAnchor(parentElement, null), elId);
    }
    _createText(renderer, parentElement, value, elId) {
        this._renderStore.store(renderer.createText(parentElement, value, null), elId);
    }
    _projectNodes(renderer, parentElement, nodes) {
        renderer.projectNodes(parentElement, nodes);
    }
    _attachViewAfter(renderer, node, viewRootNodes) {
        renderer.attachViewAfter(node, viewRootNodes);
    }
    _detachView(renderer, viewRootNodes) {
        renderer.detachView(viewRootNodes);
    }
    _destroyView(renderer, hostElement, viewAllNodes) {
        renderer.destroyView(hostElement, viewAllNodes);
        for (var i = 0; i < viewAllNodes.length; i++) {
            this._renderStore.remove(viewAllNodes[i]);
        }
    }
    _setElementProperty(renderer, renderElement, propertyName, propertyValue) {
        renderer.setElementProperty(renderElement, propertyName, propertyValue);
    }
    _setElementAttribute(renderer, renderElement, attributeName, attributeValue) {
        renderer.setElementAttribute(renderElement, attributeName, attributeValue);
    }
    _setBindingDebugInfo(renderer, renderElement, propertyName, propertyValue) {
        renderer.setBindingDebugInfo(renderElement, propertyName, propertyValue);
    }
    _setElementClass(renderer, renderElement, className, isAdd) {
        renderer.setElementClass(renderElement, className, isAdd);
    }
    _setElementStyle(renderer, renderElement, styleName, styleValue) {
        renderer.setElementStyle(renderElement, styleName, styleValue);
    }
    _invokeElementMethod(renderer, renderElement, methodName, args) {
        renderer.invokeElementMethod(renderElement, methodName, args);
    }
    _setText(renderer, renderNode, text) {
        renderer.setText(renderNode, text);
    }
    _listen(renderer, renderElement, eventName, unlistenId) {
        var unregisterCallback = renderer.listen(renderElement, eventName, (event /** TODO #9100 */) => this._eventDispatcher.dispatchRenderEvent(renderElement, null, eventName, event));
        this._renderStore.store(unregisterCallback, unlistenId);
    }
    _listenGlobal(renderer, eventTarget, eventName, unlistenId) {
        var unregisterCallback = renderer.listenGlobal(eventTarget, eventName, (event /** TODO #9100 */) => this._eventDispatcher.dispatchRenderEvent(null, eventTarget, eventName, event));
        this._renderStore.store(unregisterCallback, unlistenId);
    }
    _listenDone(renderer, unlistenCallback) { unlistenCallback(); }
}
/** @nocollapse */
MessageBasedRenderer.decorators = [
    { type: Injectable },
];
/** @nocollapse */
MessageBasedRenderer.ctorParameters = [
    { type: ServiceMessageBrokerFactory, },
    { type: MessageBus, },
    { type: Serializer, },
    { type: RenderStore, },
    { type: RootRenderer, },
];
//# sourceMappingURL=renderer.js.map