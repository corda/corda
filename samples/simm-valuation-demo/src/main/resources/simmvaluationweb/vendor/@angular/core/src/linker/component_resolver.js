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
var decorators_1 = require('../di/decorators');
var async_1 = require('../facade/async');
var exceptions_1 = require('../facade/exceptions');
var lang_1 = require('../facade/lang');
var reflection_1 = require('../reflection/reflection');
var component_factory_1 = require('./component_factory');
/**
 * Low-level service for loading {@link ComponentFactory}s, which
 * can later be used to create and render a Component instance.
 * @experimental
 */
var ComponentResolver = (function () {
    function ComponentResolver() {
    }
    return ComponentResolver;
}());
exports.ComponentResolver = ComponentResolver;
function _isComponentFactory(type) {
    return type instanceof component_factory_1.ComponentFactory;
}
var ReflectorComponentResolver = (function (_super) {
    __extends(ReflectorComponentResolver, _super);
    function ReflectorComponentResolver() {
        _super.apply(this, arguments);
    }
    ReflectorComponentResolver.prototype.resolveComponent = function (component) {
        if (lang_1.isString(component)) {
            return async_1.PromiseWrapper.reject(new exceptions_1.BaseException("Cannot resolve component using '" + component + "'."), null);
        }
        var metadatas = reflection_1.reflector.annotations(component);
        var componentFactory = metadatas.find(_isComponentFactory);
        if (lang_1.isBlank(componentFactory)) {
            throw new exceptions_1.BaseException("No precompiled component " + lang_1.stringify(component) + " found");
        }
        return async_1.PromiseWrapper.resolve(componentFactory);
    };
    ReflectorComponentResolver.prototype.clearCache = function () { };
    /** @nocollapse */
    ReflectorComponentResolver.decorators = [
        { type: decorators_1.Injectable },
    ];
    return ReflectorComponentResolver;
}(ComponentResolver));
exports.ReflectorComponentResolver = ReflectorComponentResolver;
//# sourceMappingURL=component_resolver.js.map