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
var _debugModuleUrl = 'asset://debug/lib';
function debugOutputAstAsTypeScript(ast) {
    var converter = new _TsEmitterVisitor(_debugModuleUrl);
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
exports.debugOutputAstAsTypeScript = debugOutputAstAsTypeScript;
var TypeScriptEmitter = (function () {
    function TypeScriptEmitter(_importGenerator) {
        this._importGenerator = _importGenerator;
    }
    TypeScriptEmitter.prototype.emitStatements = function (moduleUrl, stmts, exportedVars) {
        var _this = this;
        var converter = new _TsEmitterVisitor(moduleUrl);
        var ctx = abstract_emitter_1.EmitterVisitorContext.createRoot(exportedVars);
        converter.visitAllStatements(stmts, ctx);
        var srcParts = [];
        converter.importsWithPrefixes.forEach(function (prefix, importedModuleUrl) {
            // Note: can't write the real word for import as it screws up system.js auto detection...
            srcParts.push("imp" +
                ("ort * as " + prefix + " from '" + _this._importGenerator.getImportPath(moduleUrl, importedModuleUrl) + "';"));
        });
        srcParts.push(ctx.toSource());
        return srcParts.join('\n');
    };
    return TypeScriptEmitter;
}());
exports.TypeScriptEmitter = TypeScriptEmitter;
var _TsEmitterVisitor = (function (_super) {
    __extends(_TsEmitterVisitor, _super);
    function _TsEmitterVisitor(_moduleUrl) {
        _super.call(this, false);
        this._moduleUrl = _moduleUrl;
        this.importsWithPrefixes = new Map();
    }
    _TsEmitterVisitor.prototype.visitType = function (t, ctx, defaultType) {
        if (defaultType === void 0) { defaultType = 'any'; }
        if (lang_1.isPresent(t)) {
            t.visitType(this, ctx);
        }
        else {
            ctx.print(defaultType);
        }
    };
    _TsEmitterVisitor.prototype.visitExternalExpr = function (ast, ctx) {
        this._visitIdentifier(ast.value, ast.typeParams, ctx);
        return null;
    };
    _TsEmitterVisitor.prototype.visitDeclareVarStmt = function (stmt, ctx) {
        if (ctx.isExportedVar(stmt.name)) {
            ctx.print("export ");
        }
        if (stmt.hasModifier(o.StmtModifier.Final)) {
            ctx.print("const");
        }
        else {
            ctx.print("var");
        }
        ctx.print(" " + stmt.name + ":");
        this.visitType(stmt.type, ctx);
        ctx.print(" = ");
        stmt.value.visitExpression(this, ctx);
        ctx.println(";");
        return null;
    };
    _TsEmitterVisitor.prototype.visitCastExpr = function (ast, ctx) {
        ctx.print("(<");
        ast.type.visitType(this, ctx);
        ctx.print(">");
        ast.value.visitExpression(this, ctx);
        ctx.print(")");
        return null;
    };
    _TsEmitterVisitor.prototype.visitDeclareClassStmt = function (stmt, ctx) {
        var _this = this;
        ctx.pushClass(stmt);
        if (ctx.isExportedVar(stmt.name)) {
            ctx.print("export ");
        }
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
    _TsEmitterVisitor.prototype._visitClassField = function (field, ctx) {
        if (field.hasModifier(o.StmtModifier.Private)) {
            ctx.print("private ");
        }
        ctx.print(field.name);
        ctx.print(':');
        this.visitType(field.type, ctx);
        ctx.println(";");
    };
    _TsEmitterVisitor.prototype._visitClassGetter = function (getter, ctx) {
        if (getter.hasModifier(o.StmtModifier.Private)) {
            ctx.print("private ");
        }
        ctx.print("get " + getter.name + "()");
        ctx.print(':');
        this.visitType(getter.type, ctx);
        ctx.println(" {");
        ctx.incIndent();
        this.visitAllStatements(getter.body, ctx);
        ctx.decIndent();
        ctx.println("}");
    };
    _TsEmitterVisitor.prototype._visitClassConstructor = function (stmt, ctx) {
        ctx.print("constructor(");
        this._visitParams(stmt.constructorMethod.params, ctx);
        ctx.println(") {");
        ctx.incIndent();
        this.visitAllStatements(stmt.constructorMethod.body, ctx);
        ctx.decIndent();
        ctx.println("}");
    };
    _TsEmitterVisitor.prototype._visitClassMethod = function (method, ctx) {
        if (method.hasModifier(o.StmtModifier.Private)) {
            ctx.print("private ");
        }
        ctx.print(method.name + "(");
        this._visitParams(method.params, ctx);
        ctx.print("):");
        this.visitType(method.type, ctx, 'void');
        ctx.println(" {");
        ctx.incIndent();
        this.visitAllStatements(method.body, ctx);
        ctx.decIndent();
        ctx.println("}");
    };
    _TsEmitterVisitor.prototype.visitFunctionExpr = function (ast, ctx) {
        ctx.print("(");
        this._visitParams(ast.params, ctx);
        ctx.print("):");
        this.visitType(ast.type, ctx, 'void');
        ctx.println(" => {");
        ctx.incIndent();
        this.visitAllStatements(ast.statements, ctx);
        ctx.decIndent();
        ctx.print("}");
        return null;
    };
    _TsEmitterVisitor.prototype.visitDeclareFunctionStmt = function (stmt, ctx) {
        if (ctx.isExportedVar(stmt.name)) {
            ctx.print("export ");
        }
        ctx.print("function " + stmt.name + "(");
        this._visitParams(stmt.params, ctx);
        ctx.print("):");
        this.visitType(stmt.type, ctx, 'void');
        ctx.println(" {");
        ctx.incIndent();
        this.visitAllStatements(stmt.statements, ctx);
        ctx.decIndent();
        ctx.println("}");
        return null;
    };
    _TsEmitterVisitor.prototype.visitTryCatchStmt = function (stmt, ctx) {
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
    _TsEmitterVisitor.prototype.visitBuiltintType = function (type, ctx) {
        var typeStr;
        switch (type.name) {
            case o.BuiltinTypeName.Bool:
                typeStr = 'boolean';
                break;
            case o.BuiltinTypeName.Dynamic:
                typeStr = 'any';
                break;
            case o.BuiltinTypeName.Function:
                typeStr = 'Function';
                break;
            case o.BuiltinTypeName.Number:
                typeStr = 'number';
                break;
            case o.BuiltinTypeName.Int:
                typeStr = 'number';
                break;
            case o.BuiltinTypeName.String:
                typeStr = 'string';
                break;
            default:
                throw new exceptions_1.BaseException("Unsupported builtin type " + type.name);
        }
        ctx.print(typeStr);
        return null;
    };
    _TsEmitterVisitor.prototype.visitExternalType = function (ast, ctx) {
        this._visitIdentifier(ast.value, ast.typeParams, ctx);
        return null;
    };
    _TsEmitterVisitor.prototype.visitArrayType = function (type, ctx) {
        this.visitType(type.of, ctx);
        ctx.print("[]");
        return null;
    };
    _TsEmitterVisitor.prototype.visitMapType = function (type, ctx) {
        ctx.print("{[key: string]:");
        this.visitType(type.valueType, ctx);
        ctx.print("}");
        return null;
    };
    _TsEmitterVisitor.prototype.getBuiltinMethodName = function (method) {
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
    _TsEmitterVisitor.prototype._visitParams = function (params, ctx) {
        var _this = this;
        this.visitAllObjects(function (param /** TODO #9100 */) {
            ctx.print(param.name);
            ctx.print(':');
            _this.visitType(param.type, ctx);
        }, params, ctx, ',');
    };
    _TsEmitterVisitor.prototype._visitIdentifier = function (value, typeParams, ctx) {
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
    return _TsEmitterVisitor;
}(abstract_emitter_1.AbstractEmitterVisitor));
//# sourceMappingURL=ts_emitter.js.map