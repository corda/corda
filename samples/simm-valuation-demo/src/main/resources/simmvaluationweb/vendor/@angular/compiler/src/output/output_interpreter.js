/**
 * @license
 * Copyright Google Inc. All Rights Reserved.
 *
 * Use of this source code is governed by an MIT-style license that can be
 * found in the LICENSE file at https://angular.io/license
 */
"use strict";
var core_private_1 = require('../../core_private');
var async_1 = require('../facade/async');
var collection_1 = require('../facade/collection');
var exceptions_1 = require('../facade/exceptions');
var lang_1 = require('../facade/lang');
var dart_emitter_1 = require('./dart_emitter');
var o = require('./output_ast');
var ts_emitter_1 = require('./ts_emitter');
function interpretStatements(statements, resultVar, instanceFactory) {
    var stmtsWithReturn = statements.concat([new o.ReturnStatement(o.variable(resultVar))]);
    var ctx = new _ExecutionContext(null, null, null, null, new Map(), new Map(), new Map(), new Map(), instanceFactory);
    var visitor = new StatementInterpreter();
    var result = visitor.visitAllStatements(stmtsWithReturn, ctx);
    return lang_1.isPresent(result) ? result.value : null;
}
exports.interpretStatements = interpretStatements;
var DynamicInstance = (function () {
    function DynamicInstance() {
    }
    Object.defineProperty(DynamicInstance.prototype, "props", {
        get: function () { return exceptions_1.unimplemented(); },
        enumerable: true,
        configurable: true
    });
    Object.defineProperty(DynamicInstance.prototype, "getters", {
        get: function () { return exceptions_1.unimplemented(); },
        enumerable: true,
        configurable: true
    });
    Object.defineProperty(DynamicInstance.prototype, "methods", {
        get: function () { return exceptions_1.unimplemented(); },
        enumerable: true,
        configurable: true
    });
    Object.defineProperty(DynamicInstance.prototype, "clazz", {
        get: function () { return exceptions_1.unimplemented(); },
        enumerable: true,
        configurable: true
    });
    return DynamicInstance;
}());
exports.DynamicInstance = DynamicInstance;
function isDynamicInstance(instance) {
    if (lang_1.IS_DART) {
        return instance instanceof DynamicInstance;
    }
    else {
        return lang_1.isPresent(instance) && lang_1.isPresent(instance.props) && lang_1.isPresent(instance.getters) &&
            lang_1.isPresent(instance.methods);
    }
}
function _executeFunctionStatements(varNames, varValues, statements, ctx, visitor) {
    var childCtx = ctx.createChildWihtLocalVars();
    for (var i = 0; i < varNames.length; i++) {
        childCtx.vars.set(varNames[i], varValues[i]);
    }
    var result = visitor.visitAllStatements(statements, childCtx);
    return lang_1.isPresent(result) ? result.value : null;
}
var _ExecutionContext = (function () {
    function _ExecutionContext(parent, superClass, superInstance, className, vars, props, getters, methods, instanceFactory) {
        this.parent = parent;
        this.superClass = superClass;
        this.superInstance = superInstance;
        this.className = className;
        this.vars = vars;
        this.props = props;
        this.getters = getters;
        this.methods = methods;
        this.instanceFactory = instanceFactory;
    }
    _ExecutionContext.prototype.createChildWihtLocalVars = function () {
        return new _ExecutionContext(this, this.superClass, this.superInstance, this.className, new Map(), this.props, this.getters, this.methods, this.instanceFactory);
    };
    return _ExecutionContext;
}());
var ReturnValue = (function () {
    function ReturnValue(value) {
        this.value = value;
    }
    return ReturnValue;
}());
var _DynamicClass = (function () {
    function _DynamicClass(_classStmt, _ctx, _visitor) {
        this._classStmt = _classStmt;
        this._ctx = _ctx;
        this._visitor = _visitor;
    }
    _DynamicClass.prototype.instantiate = function (args) {
        var _this = this;
        var props = new Map();
        var getters = new Map();
        var methods = new Map();
        var superClass = this._classStmt.parent.visitExpression(this._visitor, this._ctx);
        var instanceCtx = new _ExecutionContext(this._ctx, superClass, null, this._classStmt.name, this._ctx.vars, props, getters, methods, this._ctx.instanceFactory);
        this._classStmt.fields.forEach(function (field) { props.set(field.name, null); });
        this._classStmt.getters.forEach(function (getter) {
            getters.set(getter.name, function () { return _executeFunctionStatements([], [], getter.body, instanceCtx, _this._visitor); });
        });
        this._classStmt.methods.forEach(function (method) {
            var paramNames = method.params.map(function (param) { return param.name; });
            methods.set(method.name, _declareFn(paramNames, method.body, instanceCtx, _this._visitor));
        });
        var ctorParamNames = this._classStmt.constructorMethod.params.map(function (param) { return param.name; });
        _executeFunctionStatements(ctorParamNames, args, this._classStmt.constructorMethod.body, instanceCtx, this._visitor);
        return instanceCtx.superInstance;
    };
    _DynamicClass.prototype.debugAst = function () { return this._visitor.debugAst(this._classStmt); };
    return _DynamicClass;
}());
var StatementInterpreter = (function () {
    function StatementInterpreter() {
    }
    StatementInterpreter.prototype.debugAst = function (ast) {
        return lang_1.IS_DART ? dart_emitter_1.debugOutputAstAsDart(ast) : ts_emitter_1.debugOutputAstAsTypeScript(ast);
    };
    StatementInterpreter.prototype.visitDeclareVarStmt = function (stmt, ctx) {
        ctx.vars.set(stmt.name, stmt.value.visitExpression(this, ctx));
        return null;
    };
    StatementInterpreter.prototype.visitWriteVarExpr = function (expr, ctx) {
        var value = expr.value.visitExpression(this, ctx);
        var currCtx = ctx;
        while (currCtx != null) {
            if (currCtx.vars.has(expr.name)) {
                currCtx.vars.set(expr.name, value);
                return value;
            }
            currCtx = currCtx.parent;
        }
        throw new exceptions_1.BaseException("Not declared variable " + expr.name);
    };
    StatementInterpreter.prototype.visitReadVarExpr = function (ast, ctx) {
        var varName = ast.name;
        if (lang_1.isPresent(ast.builtin)) {
            switch (ast.builtin) {
                case o.BuiltinVar.Super:
                case o.BuiltinVar.This:
                    return ctx.superInstance;
                case o.BuiltinVar.CatchError:
                    varName = CATCH_ERROR_VAR;
                    break;
                case o.BuiltinVar.CatchStack:
                    varName = CATCH_STACK_VAR;
                    break;
                default:
                    throw new exceptions_1.BaseException("Unknown builtin variable " + ast.builtin);
            }
        }
        var currCtx = ctx;
        while (currCtx != null) {
            if (currCtx.vars.has(varName)) {
                return currCtx.vars.get(varName);
            }
            currCtx = currCtx.parent;
        }
        throw new exceptions_1.BaseException("Not declared variable " + varName);
    };
    StatementInterpreter.prototype.visitWriteKeyExpr = function (expr, ctx) {
        var receiver = expr.receiver.visitExpression(this, ctx);
        var index = expr.index.visitExpression(this, ctx);
        var value = expr.value.visitExpression(this, ctx);
        receiver[index] = value;
        return value;
    };
    StatementInterpreter.prototype.visitWritePropExpr = function (expr, ctx) {
        var receiver = expr.receiver.visitExpression(this, ctx);
        var value = expr.value.visitExpression(this, ctx);
        if (isDynamicInstance(receiver)) {
            var di = receiver;
            if (di.props.has(expr.name)) {
                di.props.set(expr.name, value);
            }
            else {
                core_private_1.reflector.setter(expr.name)(receiver, value);
            }
        }
        else {
            core_private_1.reflector.setter(expr.name)(receiver, value);
        }
        return value;
    };
    StatementInterpreter.prototype.visitInvokeMethodExpr = function (expr, ctx) {
        var receiver = expr.receiver.visitExpression(this, ctx);
        var args = this.visitAllExpressions(expr.args, ctx);
        var result;
        if (lang_1.isPresent(expr.builtin)) {
            switch (expr.builtin) {
                case o.BuiltinMethod.ConcatArray:
                    result = collection_1.ListWrapper.concat(receiver, args[0]);
                    break;
                case o.BuiltinMethod.SubscribeObservable:
                    result = async_1.ObservableWrapper.subscribe(receiver, args[0]);
                    break;
                case o.BuiltinMethod.bind:
                    if (lang_1.IS_DART) {
                        result = receiver;
                    }
                    else {
                        result = receiver.bind(args[0]);
                    }
                    break;
                default:
                    throw new exceptions_1.BaseException("Unknown builtin method " + expr.builtin);
            }
        }
        else if (isDynamicInstance(receiver)) {
            var di = receiver;
            if (di.methods.has(expr.name)) {
                result = lang_1.FunctionWrapper.apply(di.methods.get(expr.name), args);
            }
            else {
                result = core_private_1.reflector.method(expr.name)(receiver, args);
            }
        }
        else {
            result = core_private_1.reflector.method(expr.name)(receiver, args);
        }
        return result;
    };
    StatementInterpreter.prototype.visitInvokeFunctionExpr = function (stmt, ctx) {
        var args = this.visitAllExpressions(stmt.args, ctx);
        var fnExpr = stmt.fn;
        if (fnExpr instanceof o.ReadVarExpr && fnExpr.builtin === o.BuiltinVar.Super) {
            ctx.superInstance = ctx.instanceFactory.createInstance(ctx.superClass, ctx.className, args, ctx.props, ctx.getters, ctx.methods);
            ctx.parent.superInstance = ctx.superInstance;
            return null;
        }
        else {
            var fn = stmt.fn.visitExpression(this, ctx);
            return lang_1.FunctionWrapper.apply(fn, args);
        }
    };
    StatementInterpreter.prototype.visitReturnStmt = function (stmt, ctx) {
        return new ReturnValue(stmt.value.visitExpression(this, ctx));
    };
    StatementInterpreter.prototype.visitDeclareClassStmt = function (stmt, ctx) {
        var clazz = new _DynamicClass(stmt, ctx, this);
        ctx.vars.set(stmt.name, clazz);
        return null;
    };
    StatementInterpreter.prototype.visitExpressionStmt = function (stmt, ctx) {
        return stmt.expr.visitExpression(this, ctx);
    };
    StatementInterpreter.prototype.visitIfStmt = function (stmt, ctx) {
        var condition = stmt.condition.visitExpression(this, ctx);
        if (condition) {
            return this.visitAllStatements(stmt.trueCase, ctx);
        }
        else if (lang_1.isPresent(stmt.falseCase)) {
            return this.visitAllStatements(stmt.falseCase, ctx);
        }
        return null;
    };
    StatementInterpreter.prototype.visitTryCatchStmt = function (stmt, ctx) {
        try {
            return this.visitAllStatements(stmt.bodyStmts, ctx);
        }
        catch (e) {
            var childCtx = ctx.createChildWihtLocalVars();
            childCtx.vars.set(CATCH_ERROR_VAR, e);
            childCtx.vars.set(CATCH_STACK_VAR, e.stack);
            return this.visitAllStatements(stmt.catchStmts, childCtx);
        }
    };
    StatementInterpreter.prototype.visitThrowStmt = function (stmt, ctx) {
        throw stmt.error.visitExpression(this, ctx);
    };
    StatementInterpreter.prototype.visitCommentStmt = function (stmt, context) { return null; };
    StatementInterpreter.prototype.visitInstantiateExpr = function (ast, ctx) {
        var args = this.visitAllExpressions(ast.args, ctx);
        var clazz = ast.classExpr.visitExpression(this, ctx);
        if (clazz instanceof _DynamicClass) {
            return clazz.instantiate(args);
        }
        else {
            return lang_1.FunctionWrapper.apply(core_private_1.reflector.factory(clazz), args);
        }
    };
    StatementInterpreter.prototype.visitLiteralExpr = function (ast, ctx) { return ast.value; };
    StatementInterpreter.prototype.visitExternalExpr = function (ast, ctx) { return ast.value.runtime; };
    StatementInterpreter.prototype.visitConditionalExpr = function (ast, ctx) {
        if (ast.condition.visitExpression(this, ctx)) {
            return ast.trueCase.visitExpression(this, ctx);
        }
        else if (lang_1.isPresent(ast.falseCase)) {
            return ast.falseCase.visitExpression(this, ctx);
        }
        return null;
    };
    StatementInterpreter.prototype.visitNotExpr = function (ast, ctx) {
        return !ast.condition.visitExpression(this, ctx);
    };
    StatementInterpreter.prototype.visitCastExpr = function (ast, ctx) {
        return ast.value.visitExpression(this, ctx);
    };
    StatementInterpreter.prototype.visitFunctionExpr = function (ast, ctx) {
        var paramNames = ast.params.map(function (param) { return param.name; });
        return _declareFn(paramNames, ast.statements, ctx, this);
    };
    StatementInterpreter.prototype.visitDeclareFunctionStmt = function (stmt, ctx) {
        var paramNames = stmt.params.map(function (param) { return param.name; });
        ctx.vars.set(stmt.name, _declareFn(paramNames, stmt.statements, ctx, this));
        return null;
    };
    StatementInterpreter.prototype.visitBinaryOperatorExpr = function (ast, ctx) {
        var _this = this;
        var lhs = function () { return ast.lhs.visitExpression(_this, ctx); };
        var rhs = function () { return ast.rhs.visitExpression(_this, ctx); };
        switch (ast.operator) {
            case o.BinaryOperator.Equals:
                return lhs() == rhs();
            case o.BinaryOperator.Identical:
                return lhs() === rhs();
            case o.BinaryOperator.NotEquals:
                return lhs() != rhs();
            case o.BinaryOperator.NotIdentical:
                return lhs() !== rhs();
            case o.BinaryOperator.And:
                return lhs() && rhs();
            case o.BinaryOperator.Or:
                return lhs() || rhs();
            case o.BinaryOperator.Plus:
                return lhs() + rhs();
            case o.BinaryOperator.Minus:
                return lhs() - rhs();
            case o.BinaryOperator.Divide:
                return lhs() / rhs();
            case o.BinaryOperator.Multiply:
                return lhs() * rhs();
            case o.BinaryOperator.Modulo:
                return lhs() % rhs();
            case o.BinaryOperator.Lower:
                return lhs() < rhs();
            case o.BinaryOperator.LowerEquals:
                return lhs() <= rhs();
            case o.BinaryOperator.Bigger:
                return lhs() > rhs();
            case o.BinaryOperator.BiggerEquals:
                return lhs() >= rhs();
            default:
                throw new exceptions_1.BaseException("Unknown operator " + ast.operator);
        }
    };
    StatementInterpreter.prototype.visitReadPropExpr = function (ast, ctx) {
        var result;
        var receiver = ast.receiver.visitExpression(this, ctx);
        if (isDynamicInstance(receiver)) {
            var di = receiver;
            if (di.props.has(ast.name)) {
                result = di.props.get(ast.name);
            }
            else if (di.getters.has(ast.name)) {
                result = di.getters.get(ast.name)();
            }
            else if (di.methods.has(ast.name)) {
                result = di.methods.get(ast.name);
            }
            else {
                result = core_private_1.reflector.getter(ast.name)(receiver);
            }
        }
        else {
            result = core_private_1.reflector.getter(ast.name)(receiver);
        }
        return result;
    };
    StatementInterpreter.prototype.visitReadKeyExpr = function (ast, ctx) {
        var receiver = ast.receiver.visitExpression(this, ctx);
        var prop = ast.index.visitExpression(this, ctx);
        return receiver[prop];
    };
    StatementInterpreter.prototype.visitLiteralArrayExpr = function (ast, ctx) {
        return this.visitAllExpressions(ast.entries, ctx);
    };
    StatementInterpreter.prototype.visitLiteralMapExpr = function (ast, ctx) {
        var _this = this;
        var result = {};
        ast.entries.forEach(function (entry) { return result[entry[0]] =
            entry[1].visitExpression(_this, ctx); });
        return result;
    };
    StatementInterpreter.prototype.visitAllExpressions = function (expressions, ctx) {
        var _this = this;
        return expressions.map(function (expr) { return expr.visitExpression(_this, ctx); });
    };
    StatementInterpreter.prototype.visitAllStatements = function (statements, ctx) {
        for (var i = 0; i < statements.length; i++) {
            var stmt = statements[i];
            var val = stmt.visitStatement(this, ctx);
            if (val instanceof ReturnValue) {
                return val;
            }
        }
        return null;
    };
    return StatementInterpreter;
}());
function _declareFn(varNames, statements, ctx, visitor) {
    switch (varNames.length) {
        case 0:
            return function () { return _executeFunctionStatements(varNames, [], statements, ctx, visitor); };
        case 1:
            return function (d0 /** TODO #9100 */) {
                return _executeFunctionStatements(varNames, [d0], statements, ctx, visitor);
            };
        case 2:
            return function (d0 /** TODO #9100 */, d1 /** TODO #9100 */) {
                return _executeFunctionStatements(varNames, [d0, d1], statements, ctx, visitor);
            };
        case 3:
            return function (d0 /** TODO #9100 */, d1 /** TODO #9100 */, d2 /** TODO #9100 */) {
                return _executeFunctionStatements(varNames, [d0, d1, d2], statements, ctx, visitor);
            };
        case 4:
            return function (d0 /** TODO #9100 */, d1 /** TODO #9100 */, d2 /** TODO #9100 */, d3 /** TODO #9100 */) {
                return _executeFunctionStatements(varNames, [d0, d1, d2, d3], statements, ctx, visitor);
            };
        case 5:
            return function (d0 /** TODO #9100 */, d1 /** TODO #9100 */, d2 /** TODO #9100 */, d3 /** TODO #9100 */, d4 /** TODO #9100 */) {
                return _executeFunctionStatements(varNames, [d0, d1, d2, d3, d4], statements, ctx, visitor);
            };
        case 6:
            return function (d0 /** TODO #9100 */, d1 /** TODO #9100 */, d2 /** TODO #9100 */, d3 /** TODO #9100 */, d4 /** TODO #9100 */, d5 /** TODO #9100 */) {
                return _executeFunctionStatements(varNames, [d0, d1, d2, d3, d4, d5], statements, ctx, visitor);
            };
        case 7:
            return function (d0 /** TODO #9100 */, d1 /** TODO #9100 */, d2 /** TODO #9100 */, d3 /** TODO #9100 */, d4 /** TODO #9100 */, d5 /** TODO #9100 */, d6 /** TODO #9100 */) {
                return _executeFunctionStatements(varNames, [d0, d1, d2, d3, d4, d5, d6], statements, ctx, visitor);
            };
        case 8:
            return function (d0 /** TODO #9100 */, d1 /** TODO #9100 */, d2 /** TODO #9100 */, d3 /** TODO #9100 */, d4 /** TODO #9100 */, d5 /** TODO #9100 */, d6 /** TODO #9100 */, d7 /** TODO #9100 */) {
                return _executeFunctionStatements(varNames, [d0, d1, d2, d3, d4, d5, d6, d7], statements, ctx, visitor);
            };
        case 9:
            return function (d0 /** TODO #9100 */, d1 /** TODO #9100 */, d2 /** TODO #9100 */, d3 /** TODO #9100 */, d4 /** TODO #9100 */, d5 /** TODO #9100 */, d6 /** TODO #9100 */, d7 /** TODO #9100 */, d8 /** TODO #9100 */) {
                return _executeFunctionStatements(varNames, [d0, d1, d2, d3, d4, d5, d6, d7, d8], statements, ctx, visitor);
            };
        case 10:
            return function (d0 /** TODO #9100 */, d1 /** TODO #9100 */, d2 /** TODO #9100 */, d3 /** TODO #9100 */, d4 /** TODO #9100 */, d5 /** TODO #9100 */, d6 /** TODO #9100 */, d7 /** TODO #9100 */, d8 /** TODO #9100 */, d9 /** TODO #9100 */) {
                return _executeFunctionStatements(varNames, [d0, d1, d2, d3, d4, d5, d6, d7, d8, d9], statements, ctx, visitor);
            };
        default:
            throw new exceptions_1.BaseException('Declaring functions with more than 10 arguments is not supported right now');
    }
}
var CATCH_ERROR_VAR = 'error';
var CATCH_STACK_VAR = 'stack';
//# sourceMappingURL=output_interpreter.js.map