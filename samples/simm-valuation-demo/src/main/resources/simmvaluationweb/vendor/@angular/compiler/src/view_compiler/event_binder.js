/**
 * @license
 * Copyright Google Inc. All Rights Reserved.
 *
 * Use of this source code is governed by an MIT-style license that can be
 * found in the LICENSE file at https://angular.io/license
 */
"use strict";
var collection_1 = require('../facade/collection');
var lang_1 = require('../facade/lang');
var o = require('../output/output_ast');
var compile_binding_1 = require('./compile_binding');
var compile_method_1 = require('./compile_method');
var constants_1 = require('./constants');
var expression_converter_1 = require('./expression_converter');
var CompileEventListener = (function () {
    function CompileEventListener(compileElement, eventTarget, eventName, listenerIndex) {
        this.compileElement = compileElement;
        this.eventTarget = eventTarget;
        this.eventName = eventName;
        this._hasComponentHostListener = false;
        this._actionResultExprs = [];
        this._method = new compile_method_1.CompileMethod(compileElement.view);
        this._methodName =
            "_handle_" + santitizeEventName(eventName) + "_" + compileElement.nodeIndex + "_" + listenerIndex;
        this._eventParam = new o.FnParam(constants_1.EventHandlerVars.event.name, o.importType(this.compileElement.view.genConfig.renderTypes.renderEvent));
    }
    CompileEventListener.getOrCreate = function (compileElement, eventTarget, eventName, targetEventListeners) {
        var listener = targetEventListeners.find(function (listener) { return listener.eventTarget == eventTarget && listener.eventName == eventName; });
        if (lang_1.isBlank(listener)) {
            listener = new CompileEventListener(compileElement, eventTarget, eventName, targetEventListeners.length);
            targetEventListeners.push(listener);
        }
        return listener;
    };
    CompileEventListener.prototype.addAction = function (hostEvent, directive, directiveInstance) {
        if (lang_1.isPresent(directive) && directive.isComponent) {
            this._hasComponentHostListener = true;
        }
        this._method.resetDebugInfo(this.compileElement.nodeIndex, hostEvent);
        var context = lang_1.isPresent(directiveInstance) ? directiveInstance :
            this.compileElement.view.componentContext;
        var actionStmts = expression_converter_1.convertCdStatementToIr(this.compileElement.view, context, hostEvent.handler);
        var lastIndex = actionStmts.length - 1;
        if (lastIndex >= 0) {
            var lastStatement = actionStmts[lastIndex];
            var returnExpr = convertStmtIntoExpression(lastStatement);
            var preventDefaultVar = o.variable("pd_" + this._actionResultExprs.length);
            this._actionResultExprs.push(preventDefaultVar);
            if (lang_1.isPresent(returnExpr)) {
                // Note: We need to cast the result of the method call to dynamic,
                // as it might be a void method!
                actionStmts[lastIndex] =
                    preventDefaultVar.set(returnExpr.cast(o.DYNAMIC_TYPE).notIdentical(o.literal(false)))
                        .toDeclStmt(null, [o.StmtModifier.Final]);
            }
        }
        this._method.addStmts(actionStmts);
    };
    CompileEventListener.prototype.finishMethod = function () {
        var markPathToRootStart = this._hasComponentHostListener ?
            this.compileElement.appElement.prop('componentView') :
            o.THIS_EXPR;
        var resultExpr = o.literal(true);
        this._actionResultExprs.forEach(function (expr) { resultExpr = resultExpr.and(expr); });
        var stmts = [markPathToRootStart.callMethod('markPathToRootAsCheckOnce', []).toStmt()]
            .concat(this._method.finish())
            .concat([new o.ReturnStatement(resultExpr)]);
        // private is fine here as no child view will reference the event handler...
        this.compileElement.view.eventHandlerMethods.push(new o.ClassMethod(this._methodName, [this._eventParam], stmts, o.BOOL_TYPE, [o.StmtModifier.Private]));
    };
    CompileEventListener.prototype.listenToRenderer = function () {
        var listenExpr;
        var eventListener = o.THIS_EXPR.callMethod('eventHandler', [o.THIS_EXPR.prop(this._methodName).callMethod(o.BuiltinMethod.bind, [o.THIS_EXPR])]);
        if (lang_1.isPresent(this.eventTarget)) {
            listenExpr = constants_1.ViewProperties.renderer.callMethod('listenGlobal', [o.literal(this.eventTarget), o.literal(this.eventName), eventListener]);
        }
        else {
            listenExpr = constants_1.ViewProperties.renderer.callMethod('listen', [this.compileElement.renderNode, o.literal(this.eventName), eventListener]);
        }
        var disposable = o.variable("disposable_" + this.compileElement.view.disposables.length);
        this.compileElement.view.disposables.push(disposable);
        // private is fine here as no child view will reference the event handler...
        this.compileElement.view.createMethod.addStmt(disposable.set(listenExpr).toDeclStmt(o.FUNCTION_TYPE, [o.StmtModifier.Private]));
    };
    CompileEventListener.prototype.listenToDirective = function (directiveInstance, observablePropName) {
        var subscription = o.variable("subscription_" + this.compileElement.view.subscriptions.length);
        this.compileElement.view.subscriptions.push(subscription);
        var eventListener = o.THIS_EXPR.callMethod('eventHandler', [o.THIS_EXPR.prop(this._methodName).callMethod(o.BuiltinMethod.bind, [o.THIS_EXPR])]);
        this.compileElement.view.createMethod.addStmt(subscription
            .set(directiveInstance.prop(observablePropName)
            .callMethod(o.BuiltinMethod.SubscribeObservable, [eventListener]))
            .toDeclStmt(null, [o.StmtModifier.Final]));
    };
    return CompileEventListener;
}());
exports.CompileEventListener = CompileEventListener;
function collectEventListeners(hostEvents, dirs, compileElement) {
    var eventListeners = [];
    hostEvents.forEach(function (hostEvent) {
        compileElement.view.bindings.push(new compile_binding_1.CompileBinding(compileElement, hostEvent));
        var listener = CompileEventListener.getOrCreate(compileElement, hostEvent.target, hostEvent.name, eventListeners);
        listener.addAction(hostEvent, null, null);
    });
    collection_1.ListWrapper.forEachWithIndex(dirs, function (directiveAst, i) {
        var directiveInstance = compileElement.directiveInstances[i];
        directiveAst.hostEvents.forEach(function (hostEvent) {
            compileElement.view.bindings.push(new compile_binding_1.CompileBinding(compileElement, hostEvent));
            var listener = CompileEventListener.getOrCreate(compileElement, hostEvent.target, hostEvent.name, eventListeners);
            listener.addAction(hostEvent, directiveAst.directive, directiveInstance);
        });
    });
    eventListeners.forEach(function (listener) { return listener.finishMethod(); });
    return eventListeners;
}
exports.collectEventListeners = collectEventListeners;
function bindDirectiveOutputs(directiveAst, directiveInstance, eventListeners) {
    collection_1.StringMapWrapper.forEach(directiveAst.directive.outputs, function (eventName /** TODO #9100 */, observablePropName /** TODO #9100 */) {
        eventListeners.filter(function (listener) { return listener.eventName == eventName; }).forEach(function (listener) {
            listener.listenToDirective(directiveInstance, observablePropName);
        });
    });
}
exports.bindDirectiveOutputs = bindDirectiveOutputs;
function bindRenderOutputs(eventListeners) {
    eventListeners.forEach(function (listener) { return listener.listenToRenderer(); });
}
exports.bindRenderOutputs = bindRenderOutputs;
function convertStmtIntoExpression(stmt) {
    if (stmt instanceof o.ExpressionStatement) {
        return stmt.expr;
    }
    else if (stmt instanceof o.ReturnStatement) {
        return stmt.value;
    }
    return null;
}
function santitizeEventName(name) {
    return lang_1.StringWrapper.replaceAll(name, /[^a-zA-Z_]/g, '_');
}
//# sourceMappingURL=event_binder.js.map