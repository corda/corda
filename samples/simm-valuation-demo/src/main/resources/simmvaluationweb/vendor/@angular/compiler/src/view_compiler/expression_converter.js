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
var identifiers_1 = require('../identifiers');
var o = require('../output/output_ast');
var IMPLICIT_RECEIVER = o.variable('#implicit');
var ExpressionWithWrappedValueInfo = (function () {
    function ExpressionWithWrappedValueInfo(expression, needsValueUnwrapper) {
        this.expression = expression;
        this.needsValueUnwrapper = needsValueUnwrapper;
    }
    return ExpressionWithWrappedValueInfo;
}());
exports.ExpressionWithWrappedValueInfo = ExpressionWithWrappedValueInfo;
function convertCdExpressionToIr(nameResolver, implicitReceiver, expression, valueUnwrapper) {
    var visitor = new _AstToIrVisitor(nameResolver, implicitReceiver, valueUnwrapper);
    var irAst = expression.visit(visitor, _Mode.Expression);
    return new ExpressionWithWrappedValueInfo(irAst, visitor.needsValueUnwrapper);
}
exports.convertCdExpressionToIr = convertCdExpressionToIr;
function convertCdStatementToIr(nameResolver, implicitReceiver, stmt) {
    var visitor = new _AstToIrVisitor(nameResolver, implicitReceiver, null);
    var statements = [];
    flattenStatements(stmt.visit(visitor, _Mode.Statement), statements);
    return statements;
}
exports.convertCdStatementToIr = convertCdStatementToIr;
var _Mode;
(function (_Mode) {
    _Mode[_Mode["Statement"] = 0] = "Statement";
    _Mode[_Mode["Expression"] = 1] = "Expression";
})(_Mode || (_Mode = {}));
function ensureStatementMode(mode, ast) {
    if (mode !== _Mode.Statement) {
        throw new exceptions_1.BaseException("Expected a statement, but saw " + ast);
    }
}
function ensureExpressionMode(mode, ast) {
    if (mode !== _Mode.Expression) {
        throw new exceptions_1.BaseException("Expected an expression, but saw " + ast);
    }
}
function convertToStatementIfNeeded(mode, expr) {
    if (mode === _Mode.Statement) {
        return expr.toStmt();
    }
    else {
        return expr;
    }
}
var _AstToIrVisitor = (function () {
    function _AstToIrVisitor(_nameResolver, _implicitReceiver, _valueUnwrapper) {
        this._nameResolver = _nameResolver;
        this._implicitReceiver = _implicitReceiver;
        this._valueUnwrapper = _valueUnwrapper;
        this.needsValueUnwrapper = false;
    }
    _AstToIrVisitor.prototype.visitBinary = function (ast, mode) {
        var op;
        switch (ast.operation) {
            case '+':
                op = o.BinaryOperator.Plus;
                break;
            case '-':
                op = o.BinaryOperator.Minus;
                break;
            case '*':
                op = o.BinaryOperator.Multiply;
                break;
            case '/':
                op = o.BinaryOperator.Divide;
                break;
            case '%':
                op = o.BinaryOperator.Modulo;
                break;
            case '&&':
                op = o.BinaryOperator.And;
                break;
            case '||':
                op = o.BinaryOperator.Or;
                break;
            case '==':
                op = o.BinaryOperator.Equals;
                break;
            case '!=':
                op = o.BinaryOperator.NotEquals;
                break;
            case '===':
                op = o.BinaryOperator.Identical;
                break;
            case '!==':
                op = o.BinaryOperator.NotIdentical;
                break;
            case '<':
                op = o.BinaryOperator.Lower;
                break;
            case '>':
                op = o.BinaryOperator.Bigger;
                break;
            case '<=':
                op = o.BinaryOperator.LowerEquals;
                break;
            case '>=':
                op = o.BinaryOperator.BiggerEquals;
                break;
            default:
                throw new exceptions_1.BaseException("Unsupported operation " + ast.operation);
        }
        return convertToStatementIfNeeded(mode, new o.BinaryOperatorExpr(op, ast.left.visit(this, _Mode.Expression), ast.right.visit(this, _Mode.Expression)));
    };
    _AstToIrVisitor.prototype.visitChain = function (ast, mode) {
        ensureStatementMode(mode, ast);
        return this.visitAll(ast.expressions, mode);
    };
    _AstToIrVisitor.prototype.visitConditional = function (ast, mode) {
        var value = ast.condition.visit(this, _Mode.Expression);
        return convertToStatementIfNeeded(mode, value.conditional(ast.trueExp.visit(this, _Mode.Expression), ast.falseExp.visit(this, _Mode.Expression)));
    };
    _AstToIrVisitor.prototype.visitPipe = function (ast, mode) {
        var input = ast.exp.visit(this, _Mode.Expression);
        var args = this.visitAll(ast.args, _Mode.Expression);
        var value = this._nameResolver.callPipe(ast.name, input, args);
        this.needsValueUnwrapper = true;
        return convertToStatementIfNeeded(mode, this._valueUnwrapper.callMethod('unwrap', [value]));
    };
    _AstToIrVisitor.prototype.visitFunctionCall = function (ast, mode) {
        return convertToStatementIfNeeded(mode, ast.target.visit(this, _Mode.Expression).callFn(this.visitAll(ast.args, _Mode.Expression)));
    };
    _AstToIrVisitor.prototype.visitImplicitReceiver = function (ast, mode) {
        ensureExpressionMode(mode, ast);
        return IMPLICIT_RECEIVER;
    };
    _AstToIrVisitor.prototype.visitInterpolation = function (ast, mode) {
        ensureExpressionMode(mode, ast);
        var args = [o.literal(ast.expressions.length)];
        for (var i = 0; i < ast.strings.length - 1; i++) {
            args.push(o.literal(ast.strings[i]));
            args.push(ast.expressions[i].visit(this, _Mode.Expression));
        }
        args.push(o.literal(ast.strings[ast.strings.length - 1]));
        return o.importExpr(identifiers_1.Identifiers.interpolate).callFn(args);
    };
    _AstToIrVisitor.prototype.visitKeyedRead = function (ast, mode) {
        return convertToStatementIfNeeded(mode, ast.obj.visit(this, _Mode.Expression).key(ast.key.visit(this, _Mode.Expression)));
    };
    _AstToIrVisitor.prototype.visitKeyedWrite = function (ast, mode) {
        var obj = ast.obj.visit(this, _Mode.Expression);
        var key = ast.key.visit(this, _Mode.Expression);
        var value = ast.value.visit(this, _Mode.Expression);
        return convertToStatementIfNeeded(mode, obj.key(key).set(value));
    };
    _AstToIrVisitor.prototype.visitLiteralArray = function (ast, mode) {
        return convertToStatementIfNeeded(mode, this._nameResolver.createLiteralArray(this.visitAll(ast.expressions, mode)));
    };
    _AstToIrVisitor.prototype.visitLiteralMap = function (ast, mode) {
        var parts = [];
        for (var i = 0; i < ast.keys.length; i++) {
            parts.push([ast.keys[i], ast.values[i].visit(this, _Mode.Expression)]);
        }
        return convertToStatementIfNeeded(mode, this._nameResolver.createLiteralMap(parts));
    };
    _AstToIrVisitor.prototype.visitLiteralPrimitive = function (ast, mode) {
        return convertToStatementIfNeeded(mode, o.literal(ast.value));
    };
    _AstToIrVisitor.prototype.visitMethodCall = function (ast, mode) {
        var args = this.visitAll(ast.args, _Mode.Expression);
        var result = null;
        var receiver = ast.receiver.visit(this, _Mode.Expression);
        if (receiver === IMPLICIT_RECEIVER) {
            var varExpr = this._nameResolver.getLocal(ast.name);
            if (lang_1.isPresent(varExpr)) {
                result = varExpr.callFn(args);
            }
            else {
                receiver = this._implicitReceiver;
            }
        }
        if (lang_1.isBlank(result)) {
            result = receiver.callMethod(ast.name, args);
        }
        return convertToStatementIfNeeded(mode, result);
    };
    _AstToIrVisitor.prototype.visitPrefixNot = function (ast, mode) {
        return convertToStatementIfNeeded(mode, o.not(ast.expression.visit(this, _Mode.Expression)));
    };
    _AstToIrVisitor.prototype.visitPropertyRead = function (ast, mode) {
        var result = null;
        var receiver = ast.receiver.visit(this, _Mode.Expression);
        if (receiver === IMPLICIT_RECEIVER) {
            result = this._nameResolver.getLocal(ast.name);
            if (lang_1.isBlank(result)) {
                receiver = this._implicitReceiver;
            }
        }
        if (lang_1.isBlank(result)) {
            result = receiver.prop(ast.name);
        }
        return convertToStatementIfNeeded(mode, result);
    };
    _AstToIrVisitor.prototype.visitPropertyWrite = function (ast, mode) {
        var receiver = ast.receiver.visit(this, _Mode.Expression);
        if (receiver === IMPLICIT_RECEIVER) {
            var varExpr = this._nameResolver.getLocal(ast.name);
            if (lang_1.isPresent(varExpr)) {
                throw new exceptions_1.BaseException('Cannot assign to a reference or variable!');
            }
            receiver = this._implicitReceiver;
        }
        return convertToStatementIfNeeded(mode, receiver.prop(ast.name).set(ast.value.visit(this, _Mode.Expression)));
    };
    _AstToIrVisitor.prototype.visitSafePropertyRead = function (ast, mode) {
        var receiver = ast.receiver.visit(this, _Mode.Expression);
        return convertToStatementIfNeeded(mode, receiver.isBlank().conditional(o.NULL_EXPR, receiver.prop(ast.name)));
    };
    _AstToIrVisitor.prototype.visitSafeMethodCall = function (ast, mode) {
        var receiver = ast.receiver.visit(this, _Mode.Expression);
        var args = this.visitAll(ast.args, _Mode.Expression);
        return convertToStatementIfNeeded(mode, receiver.isBlank().conditional(o.NULL_EXPR, receiver.callMethod(ast.name, args)));
    };
    _AstToIrVisitor.prototype.visitAll = function (asts, mode) {
        var _this = this;
        return asts.map(function (ast) { return ast.visit(_this, mode); });
    };
    _AstToIrVisitor.prototype.visitQuote = function (ast, mode) {
        throw new exceptions_1.BaseException('Quotes are not supported for evaluation!');
    };
    return _AstToIrVisitor;
}());
function flattenStatements(arg, output) {
    if (lang_1.isArray(arg)) {
        arg.forEach(function (entry) { return flattenStatements(entry, output); });
    }
    else {
        output.push(arg);
    }
}
//# sourceMappingURL=expression_converter.js.map