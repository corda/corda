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
var o = require('./output_ast');
var abstract_emitter_1 = require('./abstract_emitter');
var _debugModuleUrl = 'asset://debug/lib';
function debugOutputAstAsDart(ast) {
    var converter = new _DartEmitterVisitor(_debugModuleUrl);
    var ctx = abstract_emitter_1.EmitterVisitorContext.createRoot([]);
    var asts;
    if (lang_1.isArray(ast)) {
        asts = ast;
    }
    else {
        asts = [ast];
    }
    asts.forEach(function (ast) {
        if (ast instanceof o.Statement) {
            ast.visitStatement(converter, ctx);
        }
        else if (ast instanceof o.Expression) {
            ast.visitExpression(converter, ctx);
        }
        else if (ast instanceof o.Type) {
            ast.visitType(converter, ctx);
        }
        else {
            throw new exceptions_1.BaseException("Don't know how to print debug info for " + ast);
        }
    });
    return ctx.toSource();
}
exports.debugOutputAstAsDart = debugOutputAstAsDart;
var DartEmitter = (function () {
    function DartEmitter(_importGenerator) {
        this._importGenerator = _importGenerator;
    }
    DartEmitter.prototype.emitStatements = function (moduleUrl, stmts, exportedVars) {
        var _this = this;
        var srcParts = [];
        // Note: We are not creating a library here as Dart does not need it.
        // Dart analzyer might complain about it though.
        var converter = new _DartEmitterVisitor(moduleUrl);
        var ctx = abstract_emitter_1.EmitterVisitorContext.createRoot(exportedVars);
        converter.visitAllStatements(stmts, ctx);
        converter.importsWithPrefixes.forEach(function (prefix, importedModuleUrl) {
            srcParts.push("import '" + _this._importGenerator.getImportPath(moduleUrl, importedModuleUrl) + "' as " + prefix + ";");
        });
        srcParts.push(ctx.toSource());
        return srcParts.join('\n');
    };
    return DartEmitter;
}());
exports.DartEmitter = DartEmitter;
var _DartEmitterVisitor = (function (_super) {
    __extends(_DartEmitterVisitor, _super);
    function _DartEmitterVisitor(_moduleUrl) {
        _super.call(this, true);
        this._moduleUrl = _moduleUrl;
        this.importsWithPrefixes = new Map();
    }
    _DartEmitterVisitor.prototype.visitExternalExpr = function (ast, ctx) {
        this._visitIdentifier(ast.value, ast.typeParams, ctx);
        return null;
    };
    _DartEmitterVisitor.prototype.visitDeclareVarStmt = function (stmt, ctx) {
        if (stmt.hasModifier(o.StmtModifier.Final)) {
            if (isConstType(stmt.type)) {
                ctx.print("const ");
            }
            else {
                ctx.print("final ");
            }
        }
        else if (lang_1.isBlank(stmt.type)) {
            ctx.print("var ");
        }
        if (lang_1.isPresent(stmt.type)) {
            stmt.type.visitType(this, ctx);
            ctx.print(" ");
        }
        ctx.print(stmt.name + " = ");
        stmt.value.visitExpression(this, ctx);
        ctx.println(";");
        return null;
    };
    _DartEmitterVisitor.prototype.visitCastExpr = function (ast, ctx) {
        ctx.print("(");
        ast.value.visitExpression(this, ctx);
        ctx.print(" as ");
        ast.type.visitType(this, ctx);
        ctx.print(")");
        return null;
    };
    _DartEmitterVisitor.prototype.visitDeclareClassStmt = function (stmt, ctx) {
        var _this = this;
        ctx.pushClass(stmt);
        ctx.print("class " + stmt.name);
        if (lang_1.isPresent(stmt.parent)) {
            ctx.print(" extends ");
            stmt.parent.visitExpression(this, ctx);
        }
        ctx.println(" {");
        ctx.incIndent();
        stmt.fields.forEach(function (field) { return _this._visitClassField(field, ctx); });
        if (lang_1.isPresent(stmt.constructorMethod)) {
            this._visitClassConstructor(stmt, ctx);
        }
        stmt.getters.forEach(function (getter) { return _this._visitClassGetter(getter, ctx); });
        stmt.methods.forEach(function (method) { return _this._visitClassMethod(method, ctx); });
        ctx.decIndent();
        ctx.println("}");
        ctx.popClass();
        return null;
    };
    _DartEmitterVisitor.prototype._visitClassField = function (field, ctx) {
        if (field.hasModifier(o.StmtModifier.Final)) {
            ctx.print("final ");
        }
        else if (lang_1.isBlank(field.type)) {
            ctx.print("var ");
        }
        if (lang_1.isPresent(field.type)) {
            field.type.visitType(this, ctx);
            ctx.print(" ");
        }
        ctx.println(field.name + ";");
    };
    _DartEmitterVisitor.prototype._visitClassGetter = function (getter, ctx) {
        if (lang_1.isPresent(getter.type)) {
            getter.type.visitType(this, ctx);
            ctx.print(" ");
        }
        ctx.println("get " + getter.name + " {");
        ctx.incIndent();
        this.visitAllStatements(getter.body, ctx);
        ctx.decIndent();
        ctx.println("}");
    };
    _DartEmitterVisitor.prototype._visitClassConstructor = function (stmt, ctx) {
        ctx.print(stmt.name + "(");
        this._visitParams(stmt.constructorMethod.params, ctx);
        ctx.print(")");
        var ctorStmts = stmt.constructorMethod.body;
        var superCtorExpr = ctorStmts.length > 0 ? getSuperConstructorCallExpr(ctorStmts[0]) : null;
        if (lang_1.isPresent(superCtorExpr)) {
            ctx.print(": ");
            superCtorExpr.visitExpression(this, ctx);
            ctorStmts = ctorStmts.slice(1);
        }
        ctx.println(" {");
        ctx.incIndent();
        this.visitAllStatements(ctorStmts, ctx);
        ctx.decIndent();
        ctx.println("}");
    };
    _DartEmitterVisitor.prototype._visitClassMethod = function (method, ctx) {
        if (lang_1.isPresent(method.type)) {
            method.type.visitType(this, ctx);
        }
        else {
            ctx.print("void");
        }
        ctx.print(" " + method.name + "(");
        this._visitParams(method.params, ctx);
        ctx.println(") {");
        ctx.incIndent();
        this.visitAllStatements(method.body, ctx);
        ctx.decIndent();
        ctx.println("}");
    };
    _DartEmitterVisitor.prototype.visitFunctionExpr = function (ast, ctx) {
        ctx.print("(");
        this._visitParams(ast.params, ctx);
        ctx.println(") {");
        ctx.incIndent();
        this.visitAllStatements(ast.statements, ctx);
        ctx.decIndent();
        ctx.print("}");
        return null;
    };
    _DartEmitterVisitor.prototype.visitDeclareFunctionStmt = function (stmt, ctx) {
        if (lang_1.isPresent(stmt.type)) {
            stmt.type.visitType(this, ctx);
        }
        else {
            ctx.print("void");
        }
        ctx.print(" " + stmt.name + "(");
        this._visitParams(stmt.params, ctx);
        ctx.println(") {");
        ctx.incIndent();
        this.visitAllStatements(stmt.statements, ctx);
        ctx.decIndent();
        ctx.println("}");
        return null;
    };
    _DartEmitterVisitor.prototype.getBuiltinMethodName = function (method) {
        var name;
        switch (method) {
            case o.BuiltinMethod.ConcatArray:
                name = '.addAll';
                break;
            case o.BuiltinMethod.SubscribeObservable:
                name = 'listen';
                break;
            case o.BuiltinMethod.bind:
                name = null;
                break;
            default:
                throw new exceptions_1.BaseException("Unknown builtin method: " + method);
        }
        return name;
    };
    _DartEmitterVisitor.prototype.visitTryCatchStmt = function (stmt, ctx) {
        ctx.println("try {");
        ctx.incIndent();
        this.visitAllStatements(stmt.bodyStmts, ctx);
        ctx.decIndent();
        ctx.println("} catch (" + abstract_emitter_1.CATCH_ERROR_VAR.name + ", " + abstract_emitter_1.CATCH_STACK_VAR.name + ") {");
        ctx.incIndent();
        this.visitAllStatements(stmt.catchStmts, ctx);
        ctx.decIndent();
        ctx.println("}");
        return null;
    };
    _DartEmitterVisitor.prototype.visitBinaryOperatorExpr = function (ast, ctx) {
        switch (ast.operator) {
            case o.BinaryOperator.Identical:
                ctx.print("identical(");
                ast.lhs.visitExpression(this, ctx);
                ctx.print(", ");
                ast.rhs.visitExpression(this, ctx);
                ctx.print(")");
                break;
            case o.BinaryOperator.NotIdentical:
                ctx.print("!identical(");
                ast.lhs.visitExpression(this, ctx);
                ctx.print(", ");
                ast.rhs.visitExpression(this, ctx);
                ctx.print(")");
                break;
            default:
                _super.prototype.visitBinaryOperatorExpr.call(this, ast, ctx);
        }
        return null;
    };
    _DartEmitterVisitor.prototype.visitLiteralArrayExpr = function (ast, ctx) {
        if (isConstType(ast.type)) {
            ctx.print("const ");
        }
        return _super.prototype.visitLiteralArrayExpr.call(this, ast, ctx);
    };
    _DartEmitterVisitor.prototype.visitLiteralMapExpr = function (ast, ctx) {
        if (isConstType(ast.type)) {
            ctx.print("const ");
        }
        if (lang_1.isPresent(ast.valueType)) {
            ctx.print("<String, ");
            ast.valueType.visitType(this, ctx);
            ctx.print(">");
        }
        return _super.prototype.visitLiteralMapExpr.call(this, ast, ctx);
    };
    _DartEmitterVisitor.prototype.visitInstantiateExpr = function (ast, ctx) {
        ctx.print(isConstType(ast.type) ? "const" : "new");
        ctx.print(' ');
        ast.classExpr.visitExpression(this, ctx);
        ctx.print("(");
        this.visitAllExpressions(ast.args, ctx, ",");
        ctx.print(")");
        return null;
    };
    _DartEmitterVisitor.prototype.visitBuiltintType = function (type, ctx) {
        var typeStr;
        switch (type.name) {
            case o.BuiltinTypeName.Bool:
                typeStr = 'bool';
                break;
            case o.BuiltinTypeName.Dynamic:
                typeStr = 'dynamic';
                break;
            case o.BuiltinTypeName.Function:
                typeStr = 'Function';
                break;
            case o.BuiltinTypeName.Number:
                typeStr = 'num';
                break;
            case o.BuiltinTypeName.Int:
                typeStr = 'int';
                break;
            case o.BuiltinTypeName.String:
                typeStr = 'String';
                break;
            default:
                throw new exceptions_1.BaseException("Unsupported builtin type " + type.name);
        }
        ctx.print(typeStr);
        return null;
    };
    _DartEmitterVisitor.prototype.visitExternalType = function (ast, ctx) {
        this._visitIdentifier(ast.value, ast.typeParams, ctx);
        return null;
    };
    _DartEmitterVisitor.prototype.visitArrayType = function (type, ctx) {
        ctx.print("List<");
        if (lang_1.isPresent(type.of)) {
            type.of.visitType(this, ctx);
        }
        else {
            ctx.print("dynamic");
        }
        ctx.print(">");
        return null;
    };
    _DartEmitterVisitor.prototype.visitMapType = function (type, ctx) {
        ctx.print("Map<String, ");
        if (lang_1.isPresent(type.valueType)) {
            type.valueType.visitType(this, ctx);
        }
        else {
            ctx.print("dynamic");
        }
        ctx.print(">");
        return null;
    };
    _DartEmitterVisitor.prototype._visitParams = function (params, ctx) {
        var _this = this;
        this.visitAllObjects(function (param /** TODO #9100 */) {
            if (lang_1.isPresent(param.type)) {
                param.type.visitType(_this, ctx);
                ctx.print(' ');
            }
            ctx.print(param.name);
        }, params, ctx, ',');
    };
    _DartEmitterVisitor.prototype._visitIdentifier = function (value, typeParams, ctx) {
        var _this = this;
        if (lang_1.isBlank(value.name)) {
            throw new exceptions_1.BaseException("Internal error: unknown identifier " + value);
        }
        if (lang_1.isPresent(value.moduleUrl) && value.moduleUrl != this._moduleUrl) {
            var prefix = this.importsWithPrefixes.get(value.moduleUrl);
            if (lang_1.isBlank(prefix)) {
                prefix = "import" + this.importsWithPrefixes.size;
                this.importsWithPrefixes.set(value.moduleUrl, prefix);
            }
            ctx.print(prefix + ".");
        }
        ctx.print(value.name);
        if (lang_1.isPresent(typeParams) && typeParams.length > 0) {
            ctx.print("<");
            this.visitAllObjects(function (type /** TODO #9100 */) { return type.visitType(_this, ctx); }, typeParams, ctx, ',');
            ctx.print(">");
        }
    };
    return _DartEmitterVisitor;
}(abstract_emitter_1.AbstractEmitterVisitor));
function getSuperConstructorCallExpr(stmt) {
    if (stmt instanceof o.ExpressionStatement) {
        var expr = stmt.expr;
        if (expr instanceof o.InvokeFunctionExpr) {
            var fn = expr.fn;
            if (fn instanceof o.ReadVarExpr) {
                if (fn.builtin === o.BuiltinVar.Super) {
                    return expr;
                }
            }
        }
    }
    return null;
}
function isConstType(type) {
    return lang_1.isPresent(type) && type.hasModifier(o.TypeModifier.Const);
}
//# sourceMappingURL=dart_emitter.js.map