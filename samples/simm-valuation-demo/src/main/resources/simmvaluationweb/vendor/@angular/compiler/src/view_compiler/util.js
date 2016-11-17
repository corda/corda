/**
 * @license
 * Copyright Google Inc. All Rights Reserved.
 *
 * Use of this source code is governed by an MIT-style license that can be
 * found in the LICENSE file at https://angular.io/license
 */
"use strict";
var exceptions_1 = require('../facade/exceptions');
var lang_1 = require('../facade/lang');
var o = require('../output/output_ast');
var identifiers_1 = require('../identifiers');
function getPropertyInView(property, callingView, definedView) {
    if (callingView === definedView) {
        return property;
    }
    else {
        var viewProp = o.THIS_EXPR;
        var currView = callingView;
        while (currView !== definedView && lang_1.isPresent(currView.declarationElement.view)) {
            currView = currView.declarationElement.view;
            viewProp = viewProp.prop('parent');
        }
        if (currView !== definedView) {
            throw new exceptions_1.BaseException("Internal error: Could not calculate a property in a parent view: " + property);
        }
        if (property instanceof o.ReadPropExpr) {
            var readPropExpr_1 = property;
            // Note: Don't cast for members of the AppView base class...
            if (definedView.fields.some(function (field) { return field.name == readPropExpr_1.name; }) ||
                definedView.getters.some(function (field) { return field.name == readPropExpr_1.name; })) {
                viewProp = viewProp.cast(definedView.classType);
            }
        }
        return o.replaceVarInExpression(o.THIS_EXPR.name, viewProp, property);
    }
}
exports.getPropertyInView = getPropertyInView;
function injectFromViewParentInjector(token, optional) {
    var args = [createDiTokenExpression(token)];
    if (optional) {
        args.push(o.NULL_EXPR);
    }
    return o.THIS_EXPR.prop('parentInjector').callMethod('get', args);
}
exports.injectFromViewParentInjector = injectFromViewParentInjector;
function getViewFactoryName(component, embeddedTemplateIndex) {
    return "viewFactory_" + component.type.name + embeddedTemplateIndex;
}
exports.getViewFactoryName = getViewFactoryName;
function createDiTokenExpression(token) {
    if (lang_1.isPresent(token.value)) {
        return o.literal(token.value);
    }
    else if (token.identifierIsInstance) {
        return o.importExpr(token.identifier)
            .instantiate([], o.importType(token.identifier, [], [o.TypeModifier.Const]));
    }
    else {
        return o.importExpr(token.identifier);
    }
}
exports.createDiTokenExpression = createDiTokenExpression;
function createFlatArray(expressions) {
    var lastNonArrayExpressions = [];
    var result = o.literalArr([]);
    for (var i = 0; i < expressions.length; i++) {
        var expr = expressions[i];
        if (expr.type instanceof o.ArrayType) {
            if (lastNonArrayExpressions.length > 0) {
                result =
                    result.callMethod(o.BuiltinMethod.ConcatArray, [o.literalArr(lastNonArrayExpressions)]);
                lastNonArrayExpressions = [];
            }
            result = result.callMethod(o.BuiltinMethod.ConcatArray, [expr]);
        }
        else {
            lastNonArrayExpressions.push(expr);
        }
    }
    if (lastNonArrayExpressions.length > 0) {
        result =
            result.callMethod(o.BuiltinMethod.ConcatArray, [o.literalArr(lastNonArrayExpressions)]);
    }
    return result;
}
exports.createFlatArray = createFlatArray;
function createPureProxy(fn, argCount, pureProxyProp, view) {
    view.fields.push(new o.ClassField(pureProxyProp.name, null));
    var pureProxyId = argCount < identifiers_1.Identifiers.pureProxies.length ? identifiers_1.Identifiers.pureProxies[argCount] : null;
    if (lang_1.isBlank(pureProxyId)) {
        throw new exceptions_1.BaseException("Unsupported number of argument for pure functions: " + argCount);
    }
    view.createMethod.addStmt(o.THIS_EXPR.prop(pureProxyProp.name).set(o.importExpr(pureProxyId).callFn([fn])).toStmt());
}
exports.createPureProxy = createPureProxy;
//# sourceMappingURL=util.js.map