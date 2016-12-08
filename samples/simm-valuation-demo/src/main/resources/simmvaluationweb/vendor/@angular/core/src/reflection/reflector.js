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
var collection_1 = require('../facade/collection');
var exceptions_1 = require('../facade/exceptions');
var lang_1 = require('../facade/lang');
var reflector_reader_1 = require('./reflector_reader');
/**
 * Reflective information about a symbol, including annotations, interfaces, and other metadata.
 */
var ReflectionInfo = (function () {
    function ReflectionInfo(annotations, parameters, factory, interfaces, propMetadata) {
        this.annotations = annotations;
        this.parameters = parameters;
        this.factory = factory;
        this.interfaces = interfaces;
        this.propMetadata = propMetadata;
    }
    return ReflectionInfo;
}());
exports.ReflectionInfo = ReflectionInfo;
/**
 * Provides access to reflection data about symbols. Used internally by Angular
 * to power dependency injection and compilation.
 */
var Reflector = (function (_super) {
    __extends(Reflector, _super);
    function Reflector(reflectionCapabilities) {
        _super.call(this);
        /** @internal */
        this._injectableInfo = new collection_1.Map();
        /** @internal */
        this._getters = new collection_1.Map();
        /** @internal */
        this._setters = new collection_1.Map();
        /** @internal */
        this._methods = new collection_1.Map();
        this._usedKeys = null;
        this.reflectionCapabilities = reflectionCapabilities;
    }
    Reflector.prototype.updateCapabilities = function (caps) { this.reflectionCapabilities = caps; };
    Reflector.prototype.isReflectionEnabled = function () { return this.reflectionCapabilities.isReflectionEnabled(); };
    /**
     * Causes `this` reflector to track keys used to access
     * {@link ReflectionInfo} objects.
     */
    Reflector.prototype.trackUsage = function () { this._usedKeys = new collection_1.Set(); };
    /**
     * Lists types for which reflection information was not requested since
     * {@link #trackUsage} was called. This list could later be audited as
     * potential dead code.
     */
    Reflector.prototype.listUnusedKeys = function () {
        var _this = this;
        if (this._usedKeys == null) {
            throw new exceptions_1.BaseException('Usage tracking is disabled');
        }
        var allTypes = collection_1.MapWrapper.keys(this._injectableInfo);
        return allTypes.filter(function (key) { return !collection_1.SetWrapper.has(_this._usedKeys, key); });
    };
    Reflector.prototype.registerFunction = function (func, funcInfo) {
        this._injectableInfo.set(func, funcInfo);
    };
    Reflector.prototype.registerType = function (type, typeInfo) {
        this._injectableInfo.set(type, typeInfo);
    };
    Reflector.prototype.registerGetters = function (getters) { _mergeMaps(this._getters, getters); };
    Reflector.prototype.registerSetters = function (setters) { _mergeMaps(this._setters, setters); };
    Reflector.prototype.registerMethods = function (methods) { _mergeMaps(this._methods, methods); };
    Reflector.prototype.factory = function (type) {
        if (this._containsReflectionInfo(type)) {
            var res = this._getReflectionInfo(type).factory;
            return lang_1.isPresent(res) ? res : null;
        }
        else {
            return this.reflectionCapabilities.factory(type);
        }
    };
    Reflector.prototype.parameters = function (typeOrFunc) {
        if (this._injectableInfo.has(typeOrFunc)) {
            var res = this._getReflectionInfo(typeOrFunc).parameters;
            return lang_1.isPresent(res) ? res : [];
        }
        else {
            return this.reflectionCapabilities.parameters(typeOrFunc);
        }
    };
    Reflector.prototype.annotations = function (typeOrFunc) {
        if (this._injectableInfo.has(typeOrFunc)) {
            var res = this._getReflectionInfo(typeOrFunc).annotations;
            return lang_1.isPresent(res) ? res : [];
        }
        else {
            return this.reflectionCapabilities.annotations(typeOrFunc);
        }
    };
    Reflector.prototype.propMetadata = function (typeOrFunc) {
        if (this._injectableInfo.has(typeOrFunc)) {
            var res = this._getReflectionInfo(typeOrFunc).propMetadata;
            return lang_1.isPresent(res) ? res : {};
        }
        else {
            return this.reflectionCapabilities.propMetadata(typeOrFunc);
        }
    };
    Reflector.prototype.interfaces = function (type) {
        if (this._injectableInfo.has(type)) {
            var res = this._getReflectionInfo(type).interfaces;
            return lang_1.isPresent(res) ? res : [];
        }
        else {
            return this.reflectionCapabilities.interfaces(type);
        }
    };
    Reflector.prototype.hasLifecycleHook = function (type, lcInterface, lcProperty) {
        var interfaces = this.interfaces(type);
        if (interfaces.indexOf(lcInterface) !== -1) {
            return true;
        }
        else {
            return this.reflectionCapabilities.hasLifecycleHook(type, lcInterface, lcProperty);
        }
    };
    Reflector.prototype.getter = function (name) {
        if (this._getters.has(name)) {
            return this._getters.get(name);
        }
        else {
            return this.reflectionCapabilities.getter(name);
        }
    };
    Reflector.prototype.setter = function (name) {
        if (this._setters.has(name)) {
            return this._setters.get(name);
        }
        else {
            return this.reflectionCapabilities.setter(name);
        }
    };
    Reflector.prototype.method = function (name) {
        if (this._methods.has(name)) {
            return this._methods.get(name);
        }
        else {
            return this.reflectionCapabilities.method(name);
        }
    };
    /** @internal */
    Reflector.prototype._getReflectionInfo = function (typeOrFunc) {
        if (lang_1.isPresent(this._usedKeys)) {
            this._usedKeys.add(typeOrFunc);
        }
        return this._injectableInfo.get(typeOrFunc);
    };
    /** @internal */
    Reflector.prototype._containsReflectionInfo = function (typeOrFunc) { return this._injectableInfo.has(typeOrFunc); };
    Reflector.prototype.importUri = function (type) { return this.reflectionCapabilities.importUri(type); };
    return Reflector;
}(reflector_reader_1.ReflectorReader));
exports.Reflector = Reflector;
function _mergeMaps(target, config) {
    collection_1.StringMapWrapper.forEach(config, function (v, k) { return target.set(k, v); });
}
//# sourceMappingURL=reflector.js.map