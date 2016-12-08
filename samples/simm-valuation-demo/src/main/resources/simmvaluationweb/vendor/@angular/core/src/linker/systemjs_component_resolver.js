/**
 * @license
 * Copyright Google Inc. All Rights Reserved.
 *
 * Use of this source code is governed by an MIT-style license that can be
 * found in the LICENSE file at https://angular.io/license
 */
"use strict";
var lang_1 = require('../facade/lang');
var _SEPARATOR = '#';
/**
 * Component resolver that can load components lazily
 * @experimental
 */
var SystemJsComponentResolver = (function () {
    function SystemJsComponentResolver(_resolver) {
        this._resolver = _resolver;
    }
    SystemJsComponentResolver.prototype.resolveComponent = function (componentType) {
        var _this = this;
        if (lang_1.isString(componentType)) {
            var _a = componentType.split(_SEPARATOR), module = _a[0], component_1 = _a[1];
            if (component_1 === void (0)) {
                // Use the default export when no component is specified
                component_1 = 'default';
            }
            return lang_1.global
                .System.import(module)
                .then(function (module) { return _this._resolver.resolveComponent(module[component_1]); });
        }
        return this._resolver.resolveComponent(componentType);
    };
    SystemJsComponentResolver.prototype.clearCache = function () { };
    return SystemJsComponentResolver;
}());
exports.SystemJsComponentResolver = SystemJsComponentResolver;
var FACTORY_MODULE_SUFFIX = '.ngfactory';
var FACTORY_CLASS_SUFFIX = 'NgFactory';
/**
 * Component resolver that can load component factories lazily
 * @experimental
 */
var SystemJsCmpFactoryResolver = (function () {
    function SystemJsCmpFactoryResolver() {
    }
    SystemJsCmpFactoryResolver.prototype.resolveComponent = function (componentType) {
        if (lang_1.isString(componentType)) {
            var _a = componentType.split(_SEPARATOR), module = _a[0], factory_1 = _a[1];
            return lang_1.global
                .System.import(module + FACTORY_MODULE_SUFFIX)
                .then(function (module) { return module[factory_1 + FACTORY_CLASS_SUFFIX]; });
        }
        return Promise.resolve(null);
    };
    SystemJsCmpFactoryResolver.prototype.clearCache = function () { };
    return SystemJsCmpFactoryResolver;
}());
exports.SystemJsCmpFactoryResolver = SystemJsCmpFactoryResolver;
//# sourceMappingURL=systemjs_component_resolver.js.map