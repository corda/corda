/**
 * @license
 * Copyright Google Inc. All Rights Reserved.
 *
 * Use of this source code is governed by an MIT-style license that can be
 * found in the LICENSE file at https://angular.io/license
 */
import { BaseException } from '../facade/exceptions';
import { isArray, isBlank, isPresent } from '../facade/lang';
import { AbstractEmitterVisitor, CATCH_ERROR_VAR, CATCH_STACK_VAR, EmitterVisitorContext } from './abstract_emitter';
import * as o from './output_ast';
var _debugModuleUrl = 'asset://debug/lib';
export function debugOutputAstAsTypeScript(ast) {
    var converter = new _TsEmitterVisitor(_debugModuleUrl);
    var ctx = EmitterVisitorContext.createRoot([]);
    var asts;
    if (isArray(ast)) {
        asts = ast;
    }
    else {
        asts = [ast];
    }
    asts.forEach((ast) => {
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
            throw new BaseException(`Don't know how to print debug info for ${ast}`);
        }
    });
    return ctx.toSource();
}
export class TypeScriptEmitter {
    constructor(_importGenerator) {
        this._importGenerator = _importGenerator;
    }
    emitStatements(moduleUrl, stmts, exportedVars) {
        var converter = new _TsEmitterVisitor(moduleUrl);
        var ctx = EmitterVisitorContext.createRoot(exportedVars);
        converter.visitAllStatements(stmts, ctx);
        var srcParts = [];
        converter.importsWithPrefixes.forEach((prefix, importedModuleUrl) => {
            // Note: can't write the real word for import as it screws up system.js auto detection...
            srcParts.push(`imp` +
                `ort * as ${prefix} from '${this._importGenerator.getImportPath(moduleUrl, importedModuleUrl)}';`);
        });
        srcParts.push(ctx.toSource());
        return srcParts.join('\n');
    }
}
class _TsEmitterVisitor extends AbstractEmitterVisitor {
    constructor(_moduleUrl) {
        super(false);
        this._moduleUrl = _moduleUrl;
        this.importsWithPrefixes = new Map();
    }
    visitType(t, ctx, defaultType = 'any') {
        if (isPresent(t)) {
            t.visitType(this, ctx);
        }
        else {
            ctx.print(defaultType);
        }
    }
    visitExternalExpr(ast, ctx) {
        this._visitIdentifier(ast.value, ast.typeParams, ctx);
        return null;
    }
    visitDeclareVarStmt(stmt, ctx) {
        if (ctx.isExportedVar(stmt.name)) {
            ctx.print(`export `);
        }
        if (stmt.hasModifier(o.StmtModifier.Final)) {
            ctx.print(`const`);
        }
        else {
            ctx.print(`var`);
        }
        ctx.print(` ${stmt.name}:`);
        this.visitType(stmt.type, ctx);
        ctx.print(` = `);
        stmt.value.visitExpression(this, ctx);
        ctx.println(`;`);
        return null;
    }
    visitCastExpr(ast, ctx) {
        ctx.print(`(<`);
        ast.type.visitType(this, ctx);
        ctx.print(`>`);
        ast.value.visitExpression(this, ctx);
        ctx.print(`)`);
        return null;
    }
    visitDeclareClassStmt(stmt, ctx) {
        ctx.pushClass(stmt);
        if (ctx.isExportedVar(stmt.name)) {
            ctx.print(`export `);
        }
        ctx.print(`class ${stmt.name}`);
        if (isPresent(stmt.parent)) {
            ctx.print(` extends `);
            stmt.parent.visitExpression(this, ctx);
        }
        ctx.println(` {`);
        ctx.incIndent();
        stmt.fields.forEach((field) => this._visitClassField(field, ctx));
        if (isPresent(stmt.constructorMethod)) {
            this._visitClassConstructor(stmt, ctx);
        }
        stmt.getters.forEach((getter) => this._visitClassGetter(getter, ctx));
        stmt.methods.forEach((method) => this._visitClassMethod(method, ctx));
        ctx.decIndent();
        ctx.println(`}`);
        ctx.popClass();
        return null;
    }
    _visitClassField(field, ctx) {
        if (field.hasModifier(o.StmtModifier.Private)) {
            ctx.print(`private `);
        }
        ctx.print(field.name);
        ctx.print(':');
        this.visitType(field.type, ctx);
        ctx.println(`;`);
    }
    _visitClassGetter(getter, ctx) {
        if (getter.hasModifier(o.StmtModifier.Private)) {
            ctx.print(`private `);
        }
        ctx.print(`get ${getter.name}()`);
        ctx.print(':');
        this.visitType(getter.type, ctx);
        ctx.println(` {`);
        ctx.incIndent();
        this.visitAllStatements(getter.body, ctx);
        ctx.decIndent();
        ctx.println(`}`);
    }
    _visitClassConstructor(stmt, ctx) {
        ctx.print(`constructor(`);
        this._visitParams(stmt.constructorMethod.params, ctx);
        ctx.println(`) {`);
        ctx.incIndent();
        this.visitAllStatements(stmt.constructorMethod.body, ctx);
        ctx.decIndent();
        ctx.println(`}`);
    }
    _visitClassMethod(method, ctx) {
        if (method.hasModifier(o.StmtModifier.Private)) {
            ctx.print(`private `);
        }
        ctx.print(`${method.name}(`);
        this._visitParams(method.params, ctx);
        ctx.print(`):`);
        this.visitType(method.type, ctx, 'void');
        ctx.println(` {`);
        ctx.incIndent();
        this.visitAllStatements(method.body, ctx);
        ctx.decIndent();
        ctx.println(`}`);
    }
    visitFunctionExpr(ast, ctx) {
        ctx.print(`(`);
        this._visitParams(ast.params, ctx);
        ctx.print(`):`);
        this.visitType(ast.type, ctx, 'void');
        ctx.println(` => {`);
        ctx.incIndent();
        this.visitAllStatements(ast.statements, ctx);
        ctx.decIndent();
        ctx.print(`}`);
        return null;
    }
    visitDeclareFunctionStmt(stmt, ctx) {
        if (ctx.isExportedVar(stmt.name)) {
            ctx.print(`export `);
        }
        ctx.print(`function ${stmt.name}(`);
        this._visitParams(stmt.params, ctx);
        ctx.print(`):`);
        this.visitType(stmt.type, ctx, 'void');
        ctx.println(` {`);
        ctx.incIndent();
        this.visitAllStatements(stmt.statements, ctx);
        ctx.decIndent();
        ctx.println(`}`);
        return null;
    }
    visitTryCatchStmt(stmt, ctx) {
        ctx.println(`try {`);
        ctx.incIndent();
        this.visitAllStatements(stmt.bodyStmts, ctx);
        ctx.decIndent();
        ctx.println(`} catch (${CATCH_ERROR_VAR.name}) {`);
        ctx.incIndent();
        var catchStmts = [CATCH_STACK_VAR.set(CATCH_ERROR_VAR.prop('stack')).toDeclStmt(null, [
                o.StmtModifier.Final
            ])].concat(stmt.catchStmts);
        this.visitAllStatements(catchStmts, ctx);
        ctx.decIndent();
        ctx.println(`}`);
        return null;
    }
    visitBuiltintType(type, ctx) {
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
                throw new BaseException(`Unsupported builtin type ${type.name}`);
        }
        ctx.print(typeStr);
        return null;
    }
    visitExternalType(ast, ctx) {
        this._visitIdentifier(ast.value, ast.typeParams, ctx);
        return null;
    }
    visitArrayType(type, ctx) {
        this.visitType(type.of, ctx);
        ctx.print(`[]`);
        return null;
    }
    visitMapType(type, ctx) {
        ctx.print(`{[key: string]:`);
        this.visitType(type.valueType, ctx);
        ctx.print(`}`);
        return null;
    }
    getBuiltinMethodName(method) {
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
                throw new BaseException(`Unknown builtin method: ${method}`);
        }
        return name;
    }
    _visitParams(params, ctx) {
        this.visitAllObjects((param /** TODO #9100 */) => {
            ctx.print(param.name);
            ctx.print(':');
            this.visitType(param.type, ctx);
        }, params, ctx, ',');
    }
    _visitIdentifier(value, typeParams, ctx) {
        if (isBlank(value.name)) {
            throw new BaseException(`Internal error: unknown identifier ${value}`);
        }
        if (isPresent(value.moduleUrl) && value.moduleUrl != this._moduleUrl) {
            var prefix = this.importsWithPrefixes.get(value.moduleUrl);
            if (isBlank(prefix)) {
                prefix = `import${this.importsWithPrefixes.size}`;
                this.importsWithPrefixes.set(value.moduleUrl, prefix);
            }
            ctx.print(`${prefix}.`);
        }
        ctx.print(value.name);
        if (isPresent(typeParams) && typeParams.length > 0) {
            ctx.print(`<`);
            this.visitAllObjects((type /** TODO #9100 */) => type.visitType(this, ctx), typeParams, ctx, ',');
            ctx.print(`>`);
        }
    }
}
//# sourceMappingURL=ts_emitter.js.map