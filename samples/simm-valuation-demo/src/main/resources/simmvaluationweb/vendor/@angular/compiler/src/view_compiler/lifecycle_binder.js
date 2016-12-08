/**
 * @license
 * Copyright Google Inc. All Rights Reserved.
 *
 * Use of this source code is governed by an MIT-style license that can be
 * found in the LICENSE file at https://angular.io/license
 */
"use strict";
var core_private_1 = require('../../core_private');
var o = require('../output/output_ast');
var constants_1 = require('./constants');
var STATE_IS_NEVER_CHECKED = o.THIS_EXPR.prop('numberOfChecks').identical(new o.LiteralExpr(0));
var NOT_THROW_ON_CHANGES = o.not(constants_1.DetectChangesVars.throwOnChange);
function bindDirectiveDetectChangesLifecycleCallbacks(directiveAst, directiveInstance, compileElement) {
    var view = compileElement.view;
    var detectChangesInInputsMethod = view.detectChangesInInputsMethod;
    var lifecycleHooks = directiveAst.directive.lifecycleHooks;
    if (lifecycleHooks.indexOf(core_private_1.LifecycleHooks.OnChanges) !== -1 && directiveAst.inputs.length > 0) {
        detectChangesInInputsMethod.addStmt(new o.IfStmt(constants_1.DetectChangesVars.changes.notIdentical(o.NULL_EXPR), [directiveInstance.callMethod('ngOnChanges', [constants_1.DetectChangesVars.changes]).toStmt()]));
    }
    if (lifecycleHooks.indexOf(core_private_1.LifecycleHooks.OnInit) !== -1) {
        detectChangesInInputsMethod.addStmt(new o.IfStmt(STATE_IS_NEVER_CHECKED.and(NOT_THROW_ON_CHANGES), [directiveInstance.callMethod('ngOnInit', []).toStmt()]));
    }
    if (lifecycleHooks.indexOf(core_private_1.LifecycleHooks.DoCheck) !== -1) {
        detectChangesInInputsMethod.addStmt(new o.IfStmt(NOT_THROW_ON_CHANGES, [directiveInstance.callMethod('ngDoCheck', []).toStmt()]));
    }
}
exports.bindDirectiveDetectChangesLifecycleCallbacks = bindDirectiveDetectChangesLifecycleCallbacks;
function bindDirectiveAfterContentLifecycleCallbacks(directiveMeta, directiveInstance, compileElement) {
    var view = compileElement.view;
    var lifecycleHooks = directiveMeta.lifecycleHooks;
    var afterContentLifecycleCallbacksMethod = view.afterContentLifecycleCallbacksMethod;
    afterContentLifecycleCallbacksMethod.resetDebugInfo(compileElement.nodeIndex, compileElement.sourceAst);
    if (lifecycleHooks.indexOf(core_private_1.LifecycleHooks.AfterContentInit) !== -1) {
        afterContentLifecycleCallbacksMethod.addStmt(new o.IfStmt(STATE_IS_NEVER_CHECKED, [directiveInstance.callMethod('ngAfterContentInit', []).toStmt()]));
    }
    if (lifecycleHooks.indexOf(core_private_1.LifecycleHooks.AfterContentChecked) !== -1) {
        afterContentLifecycleCallbacksMethod.addStmt(directiveInstance.callMethod('ngAfterContentChecked', []).toStmt());
    }
}
exports.bindDirectiveAfterContentLifecycleCallbacks = bindDirectiveAfterContentLifecycleCallbacks;
function bindDirectiveAfterViewLifecycleCallbacks(directiveMeta, directiveInstance, compileElement) {
    var view = compileElement.view;
    var lifecycleHooks = directiveMeta.lifecycleHooks;
    var afterViewLifecycleCallbacksMethod = view.afterViewLifecycleCallbacksMethod;
    afterViewLifecycleCallbacksMethod.resetDebugInfo(compileElement.nodeIndex, compileElement.sourceAst);
    if (lifecycleHooks.indexOf(core_private_1.LifecycleHooks.AfterViewInit) !== -1) {
        afterViewLifecycleCallbacksMethod.addStmt(new o.IfStmt(STATE_IS_NEVER_CHECKED, [directiveInstance.callMethod('ngAfterViewInit', []).toStmt()]));
    }
    if (lifecycleHooks.indexOf(core_private_1.LifecycleHooks.AfterViewChecked) !== -1) {
        afterViewLifecycleCallbacksMethod.addStmt(directiveInstance.callMethod('ngAfterViewChecked', []).toStmt());
    }
}
exports.bindDirectiveAfterViewLifecycleCallbacks = bindDirectiveAfterViewLifecycleCallbacks;
function bindDirectiveDestroyLifecycleCallbacks(directiveMeta, directiveInstance, compileElement) {
    var onDestroyMethod = compileElement.view.destroyMethod;
    onDestroyMethod.resetDebugInfo(compileElement.nodeIndex, compileElement.sourceAst);
    if (directiveMeta.lifecycleHooks.indexOf(core_private_1.LifecycleHooks.OnDestroy) !== -1) {
        onDestroyMethod.addStmt(directiveInstance.callMethod('ngOnDestroy', []).toStmt());
    }
}
exports.bindDirectiveDestroyLifecycleCallbacks = bindDirectiveDestroyLifecycleCallbacks;
function bindPipeDestroyLifecycleCallbacks(pipeMeta, pipeInstance, view) {
    var onDestroyMethod = view.destroyMethod;
    if (pipeMeta.lifecycleHooks.indexOf(core_private_1.LifecycleHooks.OnDestroy) !== -1) {
        onDestroyMethod.addStmt(pipeInstance.callMethod('ngOnDestroy', []).toStmt());
    }
}
exports.bindPipeDestroyLifecycleCallbacks = bindPipeDestroyLifecycleCallbacks;
//# sourceMappingURL=lifecycle_binder.js.map