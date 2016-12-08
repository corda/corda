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
var abstract_emitter_1 = require('./abstract_emitter');
var o = require('./output_ast');
var AbstractJsEmitterVisitor = (function (_super) {
    __extends(AbstractJsEmitterVisitor, _super);
    function AbstractJsEmitterVisitor() {
        _super.call(this, false);
    }
    AbstractJsEmitterVisitor.prototype.visitDeclareClassStmt = function (stmt, ctx) {
        var _this = this;
        ctx.pushClass(stmt);
        this._visitClassConstructor(stmt, ctx);
        if (lang_1.isPresent(stmt.parent)) {
            ctx.print(stmt.name + ".prototype = Object.create(");
            stmt.parent.visitExpression(this, ctx);
            ctx.println(".prototype);");
        }
        stmt.getters.forEach(function (getter) { return _this._visitClassGetter(stmt, getter, ctx); });
        stmt.methods.forEach(function (method) { return _this._visitClassMethod(stmt, method, ctx); });
        ctx.popClass();
        return null;
    };
    AbstractJsEmitterVisitor.prototype._visitClassConstructor = function (stmt, ctx) {
        ctx.print("function " + stmt.name + "(");
        if (lang_1.isPresent(stmt.constructorMethod)) {
            this._visitParams(stmt.constructorMethod.params, ctx);
        }
        ctx.println(") {");
        ctx.incIndent();
        if (lang_1.isPresent(stmt.constructorMethod)) {
            if (stmt.constructorMethod.body.length > 0) {
                ctx.println("var self = this;");
                this.visitAllStatements(stmt.constructorMethod.body, ctx);
            }
        }
        ctx.decIndent();
        ctx.println("}");
    };
    AbstractJsEmitterVisitor.prototype._visitClassGetter = function (stmt, getter, ctx) {
        ctx.println("Object.defineProperty(" + stmt.name + ".prototype, '" + getter.name + "', { get: function() {");
        ctx.incIndent();
        if (getter.body.length > 0) {
            ctx.println("var self = this;");
            this.visitAllStatements(getter.body, ctx);
        }
        ctx.decIndent();
        ctx.println("}});");
    };
    AbstractJsEmitterVisitor.prototype._visitClassMethod = function (stmt, method, ctx) {
        ctx.print(stmt.name + ".prototype." + method.name + " = function(");
        this._visitParams(method.params, ctx);
        ctx.println(") {");
        ctx.incIndent();
        if (method.body.length > 0) {
            ctx.println("var self = this;");
            this.visitAllStatements(method.body, ctx);
        }
        ctx.decIndent();
        ctx.println("};");
    };
    AbstractJsEmitterVisitor.prototype.visitReadVarExpr = function (ast, ctx) {
        if (ast.builtin === o.BuiltinVar.This) {
            ctx.print('self');
        }
        else if (ast.builtin === o.BuiltinVar.Super) {
            throw new exceptions_1.BaseException("'super' needs to be handled at a parent ast node, not at the variable level!");
        }
        else {
            _super.prototype.visitReadVarExpr.call(this, ast, ctx);
        }
        return null;
    };
    AbstractJsEmitterVisitor.prototype.visitDeclareVarStmt = function (stmt, ctx) {
        ctx.print("var " + stmt.name + " = ");
        stmt.value.visitExpression(this, ctx);
        ctx.println(";");
        return null;
    };
    AbstractJsEmitterVisitor.prototype.visitCastExpr = function (ast, ctx) {
        ast.value.visitExpression(this, ctx);
        return null;
    };
    AbstractJsEmitterVisitor.prototype.visitInvokeFunctionExpr = function (expr, ctx) {
        var fnExpr = expr.fn;
        if (fnExpr instanceof o.ReadVarExpr && fnExpr.builtin === o.BuiltinVar.Super) {
            ctx.currentClass.parent.visitExpression(this, ctx);
            ctx.print(".call(this");
            if (expr.args.length > 0) {
                ctx.print(", ");
                this.visitAllExpressions(expr.args, ctx, ',');
            }
            ctx.print(")");
        }
        else {
            _super.prototype.visitInvokeFunctionExpr.call(this, expr, ctx);
        }
        return null;
    };
    AbstractJsEmitterVisitor.prototype.visitFunctionExpr = function (ast, ctx) {
        ctx.print("function(");
        this._visitParams(ast.params, ctx);
        ctx.println(") {");
        ctx.incIndent();
        this.visitAllStatements(ast.statements, ctx);
        ctx.decIndent();
        ctx.print("}");
        return null;
    };
    AbstractJsEmitterVisitor.prototype.visitDeclareFunctionStmt = function (stmt, ctx) {
        ctx.print("function " + stmt.name + "(");
        this._visitParams(stmt.params, ctx);
        ctx.println(") {");
        ctx.incIndent();
        this.visitAllStatements(stmt.statements, ctx);
        ctx.decIndent();
        ctx.println("}");
        return null;
    };
    AbstractJsEmitterVisitor.prototype.visitTryCatchStmt = function (stmt, ctx) {
        ctx.println("try {");
        ctx.incIndent();
        this.visitAllStatements(stmt.bodyStmts, ctx);
        ctx.decIndent();
        ctx.println("} catch (" + abstract_emitter_1.CATCH_ERROR_VAR.name + ") {");
        ctx.incIndent();
        var catchStmts = [abstract_emitter_1.CATCH_STACK_VAR.set(abstract_emitter_1.CATCH_ERROR_VAR.prop('stack')).toDeclStmt(null, [
                o.StmtModifier.Final
            ])].concat(stmt.catchStmts);
        this.visitAllStatements(catchStmts, ctx);
        ctx.decIndent();
        ctx.println("}");
        return null;
    };
    AbstractJsEmitterVisitor.prototype._visitParams = function (params, ctx) {
        this.visitAllObjects(function (param /** TODO #9100 */) { return ctx.print(param.name); }, params, ctx, ',');
    };
    AbstractJsEmitterVisitor.prototype.getBuiltinMethodName = function (method) {
        var name;
        switch (method) {
            case o.BuiltinMethod.ConcatArray:
                name = 'concat';
                break;
            case o.BuiltinMethod.SubscribeObservable:
                name = 'subscribe';
                break;
            case o.BuiltinMethod.bind:
                name = 'bind';
                break;
            default:
                throw new exceptions_1.BaseException("Unknown builtin method: " + method);
        }
        return name;
    };
    return AbstractJsEmitterVisitor;
}(abstract_emitter_1.AbstractEmitterVisitor));
exports.AbstractJsEmitterVisitor = AbstractJsEmitterVisitor;
//# sourceMappingURL=abstract_js_emitter.js.map