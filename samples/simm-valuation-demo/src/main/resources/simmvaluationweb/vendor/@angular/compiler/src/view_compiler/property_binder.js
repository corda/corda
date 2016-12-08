/**
 * @license
 * Copyright Google Inc. All Rights Reserved.
 *
 * Use of this source code is governed by an MIT-style license that can be
 * found in the LICENSE file at https://angular.io/license
 */
"use strict";
var core_private_1 = require('../../core_private');
var lang_1 = require('../facade/lang');
var identifiers_1 = require('../identifiers');
var o = require('../output/output_ast');
var constants_1 = require('./constants');
var template_ast_1 = require('../template_ast');
var util_1 = require('../util');
var expression_converter_1 = require('./expression_converter');
var compile_binding_1 = require('./compile_binding');
var core_1 = require('@angular/core');
function createBindFieldExpr(exprIndex) {
    return o.THIS_EXPR.prop("_expr_" + exprIndex);
}
function createCurrValueExpr(exprIndex) {
    return o.variable("currVal_" + exprIndex); // fix syntax highlighting: `
}
function bind(view, currValExpr, fieldExpr, parsedExpression, context, actions, method) {
    var checkExpression = expression_converter_1.convertCdExpressionToIr(view, context, parsedExpression, constants_1.DetectChangesVars.valUnwrapper);
    if (lang_1.isBlank(checkExpression.expression)) {
        // e.g. an empty expression was given
        return;
    }
    // private is fine here as no child view will reference the cached value...
    view.fields.push(new o.ClassField(fieldExpr.name, null, [o.StmtModifier.Private]));
    view.createMethod.addStmt(o.THIS_EXPR.prop(fieldExpr.name).set(o.importExpr(identifiers_1.Identifiers.uninitialized)).toStmt());
    if (checkExpression.needsValueUnwrapper) {
        var initValueUnwrapperStmt = constants_1.DetectChangesVars.valUnwrapper.callMethod('reset', []).toStmt();
        method.addStmt(initValueUnwrapperStmt);
    }
    method.addStmt(currValExpr.set(checkExpression.expression).toDeclStmt(null, [o.StmtModifier.Final]));
    var condition = o.importExpr(identifiers_1.Identifiers.checkBinding).callFn([
        constants_1.DetectChangesVars.throwOnChange, fieldExpr, currValExpr
    ]);
    if (checkExpression.needsValueUnwrapper) {
        condition = constants_1.DetectChangesVars.valUnwrapper.prop('hasWrappedValue').or(condition);
    }
    method.addStmt(new o.IfStmt(condition, actions.concat([o.THIS_EXPR.prop(fieldExpr.name).set(currValExpr).toStmt()])));
}
function bindRenderText(boundText, compileNode, view) {
    var bindingIndex = view.bindings.length;
    view.bindings.push(new compile_binding_1.CompileBinding(compileNode, boundText));
    var currValExpr = createCurrValueExpr(bindingIndex);
    var valueField = createBindFieldExpr(bindingIndex);
    view.detectChangesRenderPropertiesMethod.resetDebugInfo(compileNode.nodeIndex, boundText);
    bind(view, currValExpr, valueField, boundText.value, view.componentContext, [o.THIS_EXPR.prop('renderer')
            .callMethod('setText', [compileNode.renderNode, currValExpr])
            .toStmt()], view.detectChangesRenderPropertiesMethod);
}
exports.bindRenderText = bindRenderText;
function bindAndWriteToRenderer(boundProps, context, compileElement) {
    var view = compileElement.view;
    var renderNode = compileElement.renderNode;
    boundProps.forEach(function (boundProp) {
        var bindingIndex = view.bindings.length;
        view.bindings.push(new compile_binding_1.CompileBinding(compileElement, boundProp));
        view.detectChangesRenderPropertiesMethod.resetDebugInfo(compileElement.nodeIndex, boundProp);
        var fieldExpr = createBindFieldExpr(bindingIndex);
        var currValExpr = createCurrValueExpr(bindingIndex);
        var renderMethod;
        var oldRenderValue = sanitizedValue(boundProp, fieldExpr);
        var renderValue = sanitizedValue(boundProp, currValExpr);
        var updateStmts = [];
        switch (boundProp.type) {
            case template_ast_1.PropertyBindingType.Property:
                if (view.genConfig.logBindingUpdate) {
                    updateStmts.push(logBindingUpdateStmt(renderNode, boundProp.name, renderValue));
                }
                updateStmts.push(o.THIS_EXPR.prop('renderer')
                    .callMethod('setElementProperty', [renderNode, o.literal(boundProp.name), renderValue])
                    .toStmt());
                break;
            case template_ast_1.PropertyBindingType.Attribute:
                renderValue =
                    renderValue.isBlank().conditional(o.NULL_EXPR, renderValue.callMethod('toString', []));
                updateStmts.push(o.THIS_EXPR.prop('renderer')
                    .callMethod('setElementAttribute', [renderNode, o.literal(boundProp.name), renderValue])
                    .toStmt());
                break;
            case template_ast_1.PropertyBindingType.Class:
                updateStmts.push(o.THIS_EXPR.prop('renderer')
                    .callMethod('setElementClass', [renderNode, o.literal(boundProp.name), renderValue])
                    .toStmt());
                break;
            case template_ast_1.PropertyBindingType.Style:
                var strValue = renderValue.callMethod('toString', []);
                if (lang_1.isPresent(boundProp.unit)) {
                    strValue = strValue.plus(o.literal(boundProp.unit));
                }
                renderValue = renderValue.isBlank().conditional(o.NULL_EXPR, strValue);
                updateStmts.push(o.THIS_EXPR.prop('renderer')
                    .callMethod('setElementStyle', [renderNode, o.literal(boundProp.name), renderValue])
                    .toStmt());
                break;
            case template_ast_1.PropertyBindingType.Animation:
                var animationName = boundProp.name;
                var animation = view.componentView.animations.get(animationName);
                if (!lang_1.isPresent(animation)) {
                    throw new core_1.BaseException("Internal Error: couldn't find an animation entry for " + boundProp.name);
                }
                // it's important to normalize the void value as `void` explicitly
                // so that the styles data can be obtained from the stringmap
                var emptyStateValue = o.literal(core_private_1.EMPTY_STATE);
                // void => ...
                var oldRenderVar = o.variable('oldRenderVar');
                updateStmts.push(oldRenderVar.set(oldRenderValue).toDeclStmt());
                updateStmts.push(new o.IfStmt(oldRenderVar.equals(o.importExpr(identifiers_1.Identifiers.uninitialized)), [oldRenderVar.set(emptyStateValue).toStmt()]));
                // ... => void
                var newRenderVar = o.variable('newRenderVar');
                updateStmts.push(newRenderVar.set(renderValue).toDeclStmt());
                updateStmts.push(new o.IfStmt(newRenderVar.equals(o.importExpr(identifiers_1.Identifiers.uninitialized)), [newRenderVar.set(emptyStateValue).toStmt()]));
                updateStmts.push(animation.fnVariable.callFn([o.THIS_EXPR, renderNode, oldRenderVar, newRenderVar])
                    .toStmt());
                view.detachMethod.addStmt(animation.fnVariable.callFn([o.THIS_EXPR, renderNode, oldRenderValue, emptyStateValue])
                    .toStmt());
                break;
        }
        bind(view, currValExpr, fieldExpr, boundProp.value, context, updateStmts, view.detectChangesRenderPropertiesMethod);
    });
}
function sanitizedValue(boundProp, renderValue) {
    var enumValue;
    switch (boundProp.securityContext) {
        case core_private_1.SecurityContext.NONE:
            return renderValue; // No sanitization needed.
        case core_private_1.SecurityContext.HTML:
            enumValue = 'HTML';
            break;
        case core_private_1.SecurityContext.STYLE:
            enumValue = 'STYLE';
            break;
        case core_private_1.SecurityContext.SCRIPT:
            enumValue = 'SCRIPT';
            break;
        case core_private_1.SecurityContext.URL:
            enumValue = 'URL';
            break;
        case core_private_1.SecurityContext.RESOURCE_URL:
            enumValue = 'RESOURCE_URL';
            break;
        default:
            throw new Error("internal error, unexpected SecurityContext " + boundProp.securityContext + ".");
    }
    var ctx = constants_1.ViewProperties.viewUtils.prop('sanitizer');
    var args = [o.importExpr(identifiers_1.Identifiers.SecurityContext).prop(enumValue), renderValue];
    return ctx.callMethod('sanitize', args);
}
function bindRenderInputs(boundProps, compileElement) {
    bindAndWriteToRenderer(boundProps, compileElement.view.componentContext, compileElement);
}
exports.bindRenderInputs = bindRenderInputs;
function bindDirectiveHostProps(directiveAst, directiveInstance, compileElement) {
    bindAndWriteToRenderer(directiveAst.hostProperties, directiveInstance, compileElement);
}
exports.bindDirectiveHostProps = bindDirectiveHostProps;
function bindDirectiveInputs(directiveAst, directiveInstance, compileElement) {
    if (directiveAst.inputs.length === 0) {
        return;
    }
    var view = compileElement.view;
    var detectChangesInInputsMethod = view.detectChangesInInputsMethod;
    detectChangesInInputsMethod.resetDebugInfo(compileElement.nodeIndex, compileElement.sourceAst);
    var lifecycleHooks = directiveAst.directive.lifecycleHooks;
    var calcChangesMap = lifecycleHooks.indexOf(core_private_1.LifecycleHooks.OnChanges) !== -1;
    var isOnPushComp = directiveAst.directive.isComponent &&
        !core_private_1.isDefaultChangeDetectionStrategy(directiveAst.directive.changeDetection);
    if (calcChangesMap) {
        detectChangesInInputsMethod.addStmt(constants_1.DetectChangesVars.changes.set(o.NULL_EXPR).toStmt());
    }
    if (isOnPushComp) {
        detectChangesInInputsMethod.addStmt(constants_1.DetectChangesVars.changed.set(o.literal(false)).toStmt());
    }
    directiveAst.inputs.forEach(function (input) {
        var bindingIndex = view.bindings.length;
        view.bindings.push(new compile_binding_1.CompileBinding(compileElement, input));
        detectChangesInInputsMethod.resetDebugInfo(compileElement.nodeIndex, input);
        var fieldExpr = createBindFieldExpr(bindingIndex);
        var currValExpr = createCurrValueExpr(bindingIndex);
        var statements = [directiveInstance.prop(input.directiveName).set(currValExpr).toStmt()];
        if (calcChangesMap) {
            statements.push(new o.IfStmt(constants_1.DetectChangesVars.changes.identical(o.NULL_EXPR), [constants_1.DetectChangesVars.changes
                    .set(o.literalMap([], new o.MapType(o.importType(identifiers_1.Identifiers.SimpleChange))))
                    .toStmt()]));
            statements.push(constants_1.DetectChangesVars.changes.key(o.literal(input.directiveName))
                .set(o.importExpr(identifiers_1.Identifiers.SimpleChange).instantiate([fieldExpr, currValExpr]))
                .toStmt());
        }
        if (isOnPushComp) {
            statements.push(constants_1.DetectChangesVars.changed.set(o.literal(true)).toStmt());
        }
        if (view.genConfig.logBindingUpdate) {
            statements.push(logBindingUpdateStmt(compileElement.renderNode, input.directiveName, currValExpr));
        }
        bind(view, currValExpr, fieldExpr, input.value, view.componentContext, statements, detectChangesInInputsMethod);
    });
    if (isOnPushComp) {
        detectChangesInInputsMethod.addStmt(new o.IfStmt(constants_1.DetectChangesVars.changed, [
            compileElement.appElement.prop('componentView').callMethod('markAsCheckOnce', []).toStmt()
        ]));
    }
}
exports.bindDirectiveInputs = bindDirectiveInputs;
function logBindingUpdateStmt(renderNode, propName, value) {
    return o.THIS_EXPR.prop('renderer')
        .callMethod('setBindingDebugInfo', [
        renderNode, o.literal("ng-reflect-" + util_1.camelCaseToDashCase(propName)),
        value.isBlank().conditional(o.NULL_EXPR, value.callMethod('toString', []))
    ])
        .toStmt();
}
//# sourceMappingURL=property_binder.js.map