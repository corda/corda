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
var exceptions_1 = require('../facade/exceptions');
var lang_1 = require('../facade/lang');
/**
 * @stable
 */
var NoComponentFactoryError = (function (_super) {
    __extends(NoComponentFactoryError, _super);
    function NoComponentFactoryError(component) {
        _super.call(this, "No component factory found for " + lang_1.stringify(component));
        this.component = component;
    }
    return NoComponentFactoryError;
}(exceptions_1.BaseException));
exports.NoComponentFactoryError = NoComponentFactoryError;
var _NullComponentFactoryResolver = (function () {
    function _NullComponentFactoryResolver() {
    }
    _NullComponentFactoryResolver.prototype.resolveComponentFactory = function (component) {
        throw new NoComponentFactoryError(component);
    };
    return _NullComponentFactoryResolver;
}());
/**
 * @stable
 */
var ComponentFactoryResolver = (function () {
    function ComponentFactoryResolver() {
    }
    ComponentFactoryResolver.NULL = new _NullComponentFactoryResolver();
    return ComponentFactoryResolver;
}());
exports.ComponentFactoryResolver = ComponentFactoryResolver;
var CodegenComponentFactoryResolver = (function () {
    function CodegenComponentFactoryResolver(factories, _parent) {
        this._parent = _parent;
        this._factories = new Map();
        for (var i = 0; i < factories.length; i++) {
            var factory = factories[i];
            this._factories.set(factory.componentType, factory);
        }
    }
    CodegenComponentFactoryResolver.prototype.resolveComponentFactory = function (component) {
        var result = this._factories.get(component);
        if (!result) {
            result = this._parent.resolveComponentFactory(component);
        }
        return result;
    };
    return CodegenComponentFactoryResolver;
}());
exports.CodegenComponentFactoryResolver = CodegenComponentFactoryResolver;
//# sourceMappingURL=component_factory_resolver.js.map