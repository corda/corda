/**
 * @license
 * Copyright Google Inc. All Rights Reserved.
 *
 * Use of this source code is governed by an MIT-style license that can be
 * found in the LICENSE file at https://angular.io/license
 */
"use strict";
var exceptions_1 = require('../facade/exceptions');
/**
 * @experimental
 */
var RenderComponentType = (function () {
    function RenderComponentType(id, templateUrl, slotCount, encapsulation, styles) {
        this.id = id;
        this.templateUrl = templateUrl;
        this.slotCount = slotCount;
        this.encapsulation = encapsulation;
        this.styles = styles;
    }
    return RenderComponentType;
}());
exports.RenderComponentType = RenderComponentType;
var RenderDebugInfo = (function () {
    function RenderDebugInfo() {
    }
    Object.defineProperty(RenderDebugInfo.prototype, "injector", {
        get: function () { return exceptions_1.unimplemented(); },
        enumerable: true,
        configurable: true
    });
    Object.defineProperty(RenderDebugInfo.prototype, "component", {
        get: function () { return exceptions_1.unimplemented(); },
        enumerable: true,
        configurable: true
    });
    Object.defineProperty(RenderDebugInfo.prototype, "providerTokens", {
        get: function () { return exceptions_1.unimplemented(); },
        enumerable: true,
        configurable: true
    });
    Object.defineProperty(RenderDebugInfo.prototype, "references", {
        get: function () { return exceptions_1.unimplemented(); },
        enumerable: true,
        configurable: true
    });
    Object.defineProperty(RenderDebugInfo.prototype, "context", {
        get: function () { return exceptions_1.unimplemented(); },
        enumerable: true,
        configurable: true
    });
    Object.defineProperty(RenderDebugInfo.prototype, "source", {
        get: function () { return exceptions_1.unimplemented(); },
        enumerable: true,
        configurable: true
    });
    return RenderDebugInfo;
}());
exports.RenderDebugInfo = RenderDebugInfo;
/**
 * @experimental
 */
var Renderer = (function () {
    function Renderer() {
    }
    return Renderer;
}());
exports.Renderer = Renderer;
/**
 * Injectable service that provides a low-level interface for modifying the UI.
 *
 * Use this service to bypass Angular's templating and make custom UI changes that can't be
 * expressed declaratively. For example if you need to set a property or an attribute whose name is
 * not statically known, use {@link #setElementProperty} or {@link #setElementAttribute}
 * respectively.
 *
 * If you are implementing a custom renderer, you must implement this interface.
 *
 * The default Renderer implementation is `DomRenderer`. Also available is `WebWorkerRenderer`.
 * @experimental
 */
var RootRenderer = (function () {
    function RootRenderer() {
    }
    return RootRenderer;
}());
exports.RootRenderer = RootRenderer;
//# sourceMappingURL=api.js.map