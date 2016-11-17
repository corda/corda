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
var _DebugState = (function () {
    function _DebugState(nodeIndex, sourceAst) {
        this.nodeIndex = nodeIndex;
        this.sourceAst = sourceAst;
    }
    return _DebugState;
}());
var NULL_DEBUG_STATE = new _DebugState(null, null);
var CompileMethod = (function () {
    function CompileMethod(_view) {
        this._view = _view;
        this._newState = NULL_DEBUG_STATE;
        this._currState = NULL_DEBUG_STATE;
        this._bodyStatements = [];
        this._debugEnabled = this._view.genConfig.genDebugInfo;
    }
    CompileMethod.prototype._updateDebugContextIfNeeded = function () {
        if (this._newState.nodeIndex !== this._currState.nodeIndex ||
            this._newState.sourceAst !== this._currState.sourceAst) {
            var expr = this._updateDebugContext(this._newState);
            if (lang_1.isPresent(expr)) {
                this._bodyStatements.push(expr.toStmt());
            }
        }
    };
    CompileMethod.prototype._updateDebugContext = function (newState) {
        this._currState = this._newState = newState;
        if (this._debugEnabled) {
            var sourceLocation = lang_1.isPresent(newState.sourceAst) ? newState.sourceAst.sourceSpan.start : null;
            return o.THIS_EXPR.callMethod('debug', [
                o.literal(newState.nodeIndex),
                lang_1.isPresent(sourceLocation) ? o.literal(sourceLocation.line) : o.NULL_EXPR,
                lang_1.isPresent(sourceLocation) ? o.literal(sourceLocation.col) : o.NULL_EXPR
            ]);
        }
        else {
            return null;
        }
    };
    CompileMethod.prototype.resetDebugInfoExpr = function (nodeIndex, templateAst) {
        var res = this._updateDebugContext(new _DebugState(nodeIndex, templateAst));
        return lang_1.isPresent(res) ? res : o.NULL_EXPR;
    };
    CompileMethod.prototype.resetDebugInfo = function (nodeIndex, templateAst) {
        this._newState = new _DebugState(nodeIndex, templateAst);
    };
    CompileMethod.prototype.addStmt = function (stmt) {
        this._updateDebugContextIfNeeded();
        this._bodyStatements.push(stmt);
    };
    CompileMethod.prototype.addStmts = function (stmts) {
        this._updateDebugContextIfNeeded();
        collection_1.ListWrapper.addAll(this._bodyStatements, stmts);
    };
    CompileMethod.prototype.finish = function () { return this._bodyStatements; };
    CompileMethod.prototype.isEmpty = function () { return this._bodyStatements.length === 0; };
    return CompileMethod;
}());
exports.CompileMethod = CompileMethod;
//# sourceMappingURL=compile_method.js.map