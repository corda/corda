/**
 * @license
 * Copyright Google Inc. All Rights Reserved.
 *
 * Use of this source code is governed by an MIT-style license that can be
 * found in the LICENSE file at https://angular.io/license
 */
"use strict";
var collection_1 = require('./facade/collection');
var lang_1 = require('./facade/lang');
exports.MODULE_SUFFIX = lang_1.IS_DART ? '.dart' : '';
var CAMEL_CASE_REGEXP = /([A-Z])/g;
function camelCaseToDashCase(input) {
    return lang_1.StringWrapper.replaceAllMapped(input, CAMEL_CASE_REGEXP, function (m) { return '-' + m[1].toLowerCase(); });
}
exports.camelCaseToDashCase = camelCaseToDashCase;
function splitAtColon(input, defaultValues) {
    var parts = lang_1.StringWrapper.split(input.trim(), /\s*:\s*/g);
    if (parts.length > 1) {
        return parts;
    }
    else {
        return defaultValues;
    }
}
exports.splitAtColon = splitAtColon;
function sanitizeIdentifier(name) {
    return lang_1.StringWrapper.replaceAll(name, /\W/g, '_');
}
exports.sanitizeIdentifier = sanitizeIdentifier;
function visitValue(value, visitor, context) {
    if (lang_1.isArray(value)) {
        return visitor.visitArray(value, context);
    }
    else if (lang_1.isStrictStringMap(value)) {
        return visitor.visitStringMap(value, context);
    }
    else if (lang_1.isBlank(value) || lang_1.isPrimitive(value)) {
        return visitor.visitPrimitive(value, context);
    }
    else {
        return visitor.visitOther(value, context);
    }
}
exports.visitValue = visitValue;
var ValueTransformer = (function () {
    function ValueTransformer() {
    }
    ValueTransformer.prototype.visitArray = function (arr, context) {
        var _this = this;
        return arr.map(function (value) { return visitValue(value, _this, context); });
    };
    ValueTransformer.prototype.visitStringMap = function (map, context) {
        var _this = this;
        var result = {};
        collection_1.StringMapWrapper.forEach(map, function (value /** TODO #9100 */, key /** TODO #9100 */) {
            result[key] = visitValue(value, _this, context);
        });
        return result;
    };
    ValueTransformer.prototype.visitPrimitive = function (value, context) { return value; };
    ValueTransformer.prototype.visitOther = function (value, context) { return value; };
    return ValueTransformer;
}());
exports.ValueTransformer = ValueTransformer;
function assetUrl(pkg, path, type) {
    if (path === void 0) { path = null; }
    if (type === void 0) { type = 'src'; }
    if (lang_1.IS_DART) {
        if (path == null) {
            return "asset:angular2/" + pkg + "/" + pkg + ".dart";
        }
        else {
            return "asset:angular2/lib/" + pkg + "/src/" + path + ".dart";
        }
    }
    else {
        if (path == null) {
            return "asset:@angular/lib/" + pkg + "/index";
        }
        else {
            return "asset:@angular/lib/" + pkg + "/src/" + path;
        }
    }
}
exports.assetUrl = assetUrl;
//# sourceMappingURL=util.js.map