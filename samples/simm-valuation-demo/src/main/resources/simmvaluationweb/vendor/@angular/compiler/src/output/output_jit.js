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
var lang_1 = require('../facade/lang');
var abstract_emitter_1 = require('./abstract_emitter');
var abstract_js_emitter_1 = require('./abstract_js_emitter');
var util_1 = require('../util');
function jitStatements(sourceUrl, statements, resultVar) {
    var converter = new JitEmitterVisitor();
    var ctx = abstract_emitter_1.EmitterVisitorContext.createRoot([resultVar]);
    converter.visitAllStatements(statements, ctx);
    return lang_1.evalExpression(sourceUrl, resultVar, ctx.toSource(), converter.getArgs());
}
exports.jitStatements = jitStatements;
var JitEmitterVisitor = (function (_super) {
    __extends(JitEmitterVisitor, _super);
    function JitEmitterVisitor() {
        _super.apply(this, arguments);
        this._evalArgNames = [];
        this._evalArgValues = [];
    }
    JitEmitterVisitor.prototype.getArgs = function () {
        var result = {};
        for (var i = 0; i < this._evalArgNames.length; i++) {
            result[this._evalArgNames[i]] = this._evalArgValues[i];
        }
        return result;
    };
    JitEmitterVisitor.prototype.visitExternalExpr = function (ast, ctx) {
        var value = ast.value.runtime;
        var id = this._evalArgValues.indexOf(value);
        if (id === -1) {
            id = this._evalArgValues.length;
            this._evalArgValues.push(value);
            var name = lang_1.isPresent(ast.value.name) ? util_1.sanitizeIdentifier(ast.value.name) : 'val';
            this._evalArgNames.push(util_1.sanitizeIdentifier("jit_" + name + id));
        }
        ctx.print(this._evalArgNames[id]);
        return null;
    };
    return JitEmitterVisitor;
}(abstract_js_emitter_1.AbstractJsEmitterVisitor));
//# sourceMappingURL=output_jit.js.map