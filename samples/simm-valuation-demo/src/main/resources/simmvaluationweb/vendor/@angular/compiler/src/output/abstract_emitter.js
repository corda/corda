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
var o = require('./output_ast');
var _SINGLE_QUOTE_ESCAPE_STRING_RE = /'|\\|\n|\r|\$/g;
exports.CATCH_ERROR_VAR = o.variable('error');
exports.CATCH_STACK_VAR = o.variable('stack');
var OutputEmitter = (function () {
    function OutputEmitter() {
    }
    return OutputEmitter;
}());
exports.OutputEmitter = OutputEmitter;
var _EmittedLine = (function () {
    function _EmittedLine(indent) {
        this.indent = indent;
        this.parts = [];
    }
    return _EmittedLine;
}());
var EmitterVisitorContext = (function () {
    function EmitterVisitorContext(_exportedVars, _indent) {
        this._exportedVars = _exportedVars;
        this._indent = _indent;
        this._classes = [];
        this._lines = [new _EmittedLine(_indent)];
    }
    EmitterVisitorContext.createRoot = function (exportedVars) {
        return new EmitterVisitorContext(exportedVars, 0);
    };
    Object.defineProperty(EmitterVisitorContext.prototype, "_currentLine", {
        get: function () { return this._lines[this._lines.length - 1]; },
        enumerable: true,
        configurable: true
    });
    EmitterVisitorContext.prototype.isExportedVar = function (varName) { return this._exportedVars.indexOf(varName) !== -1; };
    EmitterVisitorContext.prototype.println = function (lastPart) {
        if (lastPart === void 0) { lastPart = ''; }
        this.print(lastPart, true);
    };
    EmitterVisitorContext.prototype.lineIsEmpty = function () { return this._currentLine.parts.length === 0; };
    EmitterVisitorContext.prototype.print = function (part, newLine) {
        if (newLine === void 0) { newLine = false; }
        if (part.length > 0) {
            this._currentLine.parts.push(part);
        }
        if (newLine) {
            this._lines.push(new _EmittedLine(this._indent));
        }
    };
    EmitterVisitorContext.prototype.removeEmptyLastLine = function () {
        if (this.lineIsEmpty()) {
            this._lines.pop();
        }
    };
    EmitterVisitorContext.prototype.incIndent = function () {
        this._indent++;
        this._currentLine.indent = this._indent;
    };
    EmitterVisitorContext.prototype.decIndent = function () {
        this._indent--;
        this._currentLine.indent = this._indent;
    };
    EmitterVisitorContext.prototype.pushClass = function (clazz) { this._classes.push(clazz); };
    EmitterVisitorContext.prototype.popClass = function () { return this._classes.pop(); };
    Object.defineProperty(EmitterVisitorContext.prototype, "currentClass", {
        get: function () {
            return this._classes.length > 0 ? this._classes[this._classes.length - 1] : null;
        },
        enumerable: true,
        configurable: true
    });
    EmitterVisitorContext.prototype.toSource = function () {
        var lines = this._lines;
        if (lines[lines.length - 1].parts.length === 0) {
            lines = lines.slice(0, lines.length - 1);
        }
        return lines
            .map(function (line) {
            if (line.parts.length > 0) {
                return _createIndent(line.indent) + line.parts.join('');
            }
            else {
                return '';
            }
        })
            .join('\n');
    };
    return EmitterVisitorContext;
}());
exports.EmitterVisitorContext = EmitterVisitorContext;
var AbstractEmitterVisitor = (function () {
    function AbstractEmitterVisitor(_escapeDollarInStrings) {
        this._escapeDollarInStrings = _escapeDollarInStrings;
    }
    AbstractEmitterVisitor.prototype.visitExpressionStmt = function (stmt, ctx) {
        stmt.expr.visitExpression(this, ctx);
        ctx.println(';');
        return null;
    };
    AbstractEmitterVisitor.prototype.visitReturnStmt = function (stmt, ctx) {
        ctx.print("return ");
        stmt.value.visitExpression(this, ctx);
        ctx.println(';');
        return null;
    };
    AbstractEmitterVisitor.prototype.visitIfStmt = function (stmt, ctx) {
        ctx.print("if (");
        stmt.condition.visitExpression(this, ctx);
        ctx.print(") {");
        var hasElseCase = lang_1.isPresent(stmt.falseCase) && stmt.falseCase.length > 0;
        if (stmt.trueCase.length <= 1 && !hasElseCase) {
            ctx.print(" ");
            this.visitAllStatements(stmt.trueCase, ctx);
            ctx.removeEmptyLastLine();
            ctx.print(" ");
        }
        else {
            ctx.println();
            ctx.incIndent();
            this.visitAllStatements(stmt.trueCase, ctx);
            ctx.decIndent();
            if (hasElseCase) {
                ctx.println("} else {");
                ctx.incIndent();
                this.visitAllStatements(stmt.falseCase, ctx);
                ctx.decIndent();
            }
        }
        ctx.println("}");
        return null;
    };
    AbstractEmitterVisitor.prototype.visitThrowStmt = function (stmt, ctx) {
        ctx.print("throw ");
        stmt.error.visitExpression(this, ctx);
        ctx.println(";");
        return null;
    };
    AbstractEmitterVisitor.prototype.visitCommentStmt = function (stmt, ctx) {
        var lines = stmt.comment.split('\n');
        lines.forEach(function (line) { ctx.println("// " + line); });
        return null;
    };
    AbstractEmitterVisitor.prototype.visitWriteVarExpr = function (expr, ctx) {
        var lineWasEmpty = ctx.lineIsEmpty();
        if (!lineWasEmpty) {
            ctx.print('(');
        }
        ctx.print(expr.name + " = ");
        expr.value.visitExpression(this, ctx);
        if (!lineWasEmpty) {
            ctx.print(')');
        }
        return null;
    };
    AbstractEmitterVisitor.prototype.visitWriteKeyExpr = function (expr, ctx) {
        var lineWasEmpty = ctx.lineIsEmpty();
        if (!lineWasEmpty) {
            ctx.print('(');
        }
        expr.receiver.visitExpression(this, ctx);
        ctx.print("[");
        expr.index.visitExpression(this, ctx);
        ctx.print("] = ");
        expr.value.visitExpression(this, ctx);
        if (!lineWasEmpty) {
            ctx.print(')');
        }
        return null;
    };
    AbstractEmitterVisitor.prototype.visitWritePropExpr = function (expr, ctx) {
        var lineWasEmpty = ctx.lineIsEmpty();
        if (!lineWasEmpty) {
            ctx.print('(');
        }
        expr.receiver.visitExpression(this, ctx);
        ctx.print("." + expr.name + " = ");
        expr.value.visitExpression(this, ctx);
        if (!lineWasEmpty) {
            ctx.print(')');
        }
        return null;
    };
    AbstractEmitterVisitor.prototype.visitInvokeMethodExpr = function (expr, ctx) {
        expr.receiver.visitExpression(this, ctx);
        var name = expr.name;
        if (lang_1.isPresent(expr.builtin)) {
            name = this.getBuiltinMethodName(expr.builtin);
            if (lang_1.isBlank(name)) {
                // some builtins just mean to skip the call.
                // e.g. `bind` in Dart.
                return null;
            }
        }
        ctx.print("." + name + "(");
        this.visitAllExpressions(expr.args, ctx, ",");
        ctx.print(")");
        return null;
    };
    AbstractEmitterVisitor.prototype.visitInvokeFunctionExpr = function (expr, ctx) {
        expr.fn.visitExpression(this, ctx);
        ctx.print("(");
        this.visitAllExpressions(expr.args, ctx, ',');
        ctx.print(")");
        return null;
    };
    AbstractEmitterVisitor.prototype.visitReadVarExpr = function (ast, ctx) {
        var varName = ast.name;
        if (lang_1.isPresent(ast.builtin)) {
            switch (ast.builtin) {
                case o.BuiltinVar.Super:
                    varName = 'super';
                    break;
                case o.BuiltinVar.This:
                    varName = 'this';
                    break;
                case o.BuiltinVar.CatchError:
                    varName = exports.CATCH_ERROR_VAR.name;
                    break;
                case o.BuiltinVar.CatchStack:
                    varName = exports.CATCH_STACK_VAR.name;
                    break;
                default:
                    throw new exceptions_1.BaseException("Unknown builtin variable " + ast.builtin);
            }
        }
        ctx.print(varName);
        return null;
    };
    AbstractEmitterVisitor.prototype.visitInstantiateExpr = function (ast, ctx) {
        ctx.print("new ");
        ast.classExpr.visitExpression(this, ctx);
        ctx.print("(");
        this.visitAllExpressions(ast.args, ctx, ',');
        ctx.print(")");
        return null;
    };
    AbstractEmitterVisitor.prototype.visitLiteralExpr = function (ast, ctx) {
        var value = ast.value;
        if (lang_1.isString(value)) {
            ctx.print(escapeSingleQuoteString(value, this._escapeDollarInStrings));
        }
        else if (lang_1.isBlank(value)) {
            ctx.print('null');
        }
        else {
            ctx.print("" + value);
        }
        return null;
    };
    AbstractEmitterVisitor.prototype.visitConditionalExpr = function (ast, ctx) {
        ctx.print("(");
        ast.condition.visitExpression(this, ctx);
        ctx.print('? ');
        ast.trueCase.visitExpression(this, ctx);
        ctx.print(': ');
        ast.falseCase.visitExpression(this, ctx);
        ctx.print(")");
        return null;
    };
    AbstractEmitterVisitor.prototype.visitNotExpr = function (ast, ctx) {
        ctx.print('!');
        ast.condition.visitExpression(this, ctx);
        return null;
    };
    AbstractEmitterVisitor.prototype.visitBinaryOperatorExpr = function (ast, ctx) {
        var opStr;
        switch (ast.operator) {
            case o.BinaryOperator.Equals:
                opStr = '==';
                break;
            case o.BinaryOperator.Identical:
                opStr = '===';
                break;
            case o.BinaryOperator.NotEquals:
                opStr = '!=';
                break;
            case o.BinaryOperator.NotIdentical:
                opStr = '!==';
                break;
            case o.BinaryOperator.And:
                opStr = '&&';
                break;
            case o.BinaryOperator.Or:
                opStr = '||';
                break;
            case o.BinaryOperator.Plus:
                opStr = '+';
                break;
            case o.BinaryOperator.Minus:
                opStr = '-';
                break;
            case o.BinaryOperator.Divide:
                opStr = '/';
                break;
            case o.BinaryOperator.Multiply:
                opStr = '*';
                break;
            case o.BinaryOperator.Modulo:
                opStr = '%';
                break;
            case o.BinaryOperator.Lower:
                opStr = '<';
                break;
            case o.BinaryOperator.LowerEquals:
                opStr = '<=';
                break;
            case o.BinaryOperator.Bigger:
                opStr = '>';
                break;
            case o.BinaryOperator.BiggerEquals:
                opStr = '>=';
                break;
            default:
                throw new exceptions_1.BaseException("Unknown operator " + ast.operator);
        }
        ctx.print("(");
        ast.lhs.visitExpression(this, ctx);
        ctx.print(" " + opStr + " ");
        ast.rhs.visitExpression(this, ctx);
        ctx.print(")");
        return null;
    };
    AbstractEmitterVisitor.prototype.visitReadPropExpr = function (ast, ctx) {
        ast.receiver.visitExpression(this, ctx);
        ctx.print(".");
        ctx.print(ast.name);
        return null;
    };
    AbstractEmitterVisitor.prototype.visitReadKeyExpr = function (ast, ctx) {
        ast.receiver.visitExpression(this, ctx);
        ctx.print("[");
        ast.index.visitExpression(this, ctx);
        ctx.print("]");
        return null;
    };
    AbstractEmitterVisitor.prototype.visitLiteralArrayExpr = function (ast, ctx) {
        var useNewLine = ast.entries.length > 1;
        ctx.print("[", useNewLine);
        ctx.incIndent();
        this.visitAllExpressions(ast.entries, ctx, ',', useNewLine);
        ctx.decIndent();
        ctx.print("]", useNewLine);
        return null;
    };
    AbstractEmitterVisitor.prototype.visitLiteralMapExpr = function (ast, ctx) {
        var _this = this;
        var useNewLine = ast.entries.length > 1;
        ctx.print("{", useNewLine);
        ctx.incIndent();
        this.visitAllObjects(function (entry /** TODO #9100 */) {
            ctx.print(escapeSingleQuoteString(entry[0], _this._escapeDollarInStrings) + ": ");
            entry[1].visitExpression(_this, ctx);
        }, ast.entries, ctx, ',', useNewLine);
        ctx.decIndent();
        ctx.print("}", useNewLine);
        return null;
    };
    AbstractEmitterVisitor.prototype.visitAllExpressions = function (expressions, ctx, separator, newLine) {
        var _this = this;
        if (newLine === void 0) { newLine = false; }
        this.visitAllObjects(function (expr /** TODO #9100 */) { return expr.visitExpression(_this, ctx); }, expressions, ctx, separator, newLine);
    };
    AbstractEmitterVisitor.prototype.visitAllObjects = function (handler, expressions, ctx, separator, newLine) {
        if (newLine === void 0) { newLine = false; }
        for (var i = 0; i < expressions.length; i++) {
            if (i > 0) {
                ctx.print(separator, newLine);
            }
            handler(expressions[i]);
        }
        if (newLine) {
            ctx.println();
        }
    };
    AbstractEmitterVisitor.prototype.visitAllStatements = function (statements, ctx) {
        var _this = this;
        statements.forEach(function (stmt) { return stmt.visitStatement(_this, ctx); });
    };
    return AbstractEmitterVisitor;
}());
exports.AbstractEmitterVisitor = AbstractEmitterVisitor;
function escapeSingleQuoteString(input, escapeDollar) {
    if (lang_1.isBlank(input)) {
        return null;
    }
    var body = lang_1.StringWrapper.replaceAllMapped(input, _SINGLE_QUOTE_ESCAPE_STRING_RE, function (match /** TODO #9100 */) {
        if (match[0] == '$') {
            return escapeDollar ? '\\$' : '$';
        }
        else if (match[0] == '\n') {
            return '\\n';
        }
        else if (match[0] == '\r') {
            return '\\r';
        }
        else {
            return "\\" + match[0];
        }
    });
    return "'" + body + "'";
}
exports.escapeSingleQuoteString = escapeSingleQuoteString;
function _createIndent(count) {
    var res = '';
    for (var i = 0; i < count; i++) {
        res += '  ';
    }
    return res;
}
//# sourceMappingURL=abstract_emitter.js.map