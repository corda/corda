/**
 * @license
 * Copyright Google Inc. All Rights Reserved.
 *
 * Use of this source code is governed by an MIT-style license that can be
 * found in the LICENSE file at https://angular.io/license
 */
"use strict";
var lang_1 = require('../src/facade/lang');
var HtmlTextAst = (function () {
    function HtmlTextAst(value, sourceSpan) {
        this.value = value;
        this.sourceSpan = sourceSpan;
    }
    HtmlTextAst.prototype.visit = function (visitor, context) { return visitor.visitText(this, context); };
    return HtmlTextAst;
}());
exports.HtmlTextAst = HtmlTextAst;
var HtmlExpansionAst = (function () {
    function HtmlExpansionAst(switchValue, type, cases, sourceSpan, switchValueSourceSpan) {
        this.switchValue = switchValue;
        this.type = type;
        this.cases = cases;
        this.sourceSpan = sourceSpan;
        this.switchValueSourceSpan = switchValueSourceSpan;
    }
    HtmlExpansionAst.prototype.visit = function (visitor, context) {
        return visitor.visitExpansion(this, context);
    };
    return HtmlExpansionAst;
}());
exports.HtmlExpansionAst = HtmlExpansionAst;
var HtmlExpansionCaseAst = (function () {
    function HtmlExpansionCaseAst(value, expression, sourceSpan, valueSourceSpan, expSourceSpan) {
        this.value = value;
        this.expression = expression;
        this.sourceSpan = sourceSpan;
        this.valueSourceSpan = valueSourceSpan;
        this.expSourceSpan = expSourceSpan;
    }
    HtmlExpansionCaseAst.prototype.visit = function (visitor, context) {
        return visitor.visitExpansionCase(this, context);
    };
    return HtmlExpansionCaseAst;
}());
exports.HtmlExpansionCaseAst = HtmlExpansionCaseAst;
var HtmlAttrAst = (function () {
    function HtmlAttrAst(name, value, sourceSpan) {
        this.name = name;
        this.value = value;
        this.sourceSpan = sourceSpan;
    }
    HtmlAttrAst.prototype.visit = function (visitor, context) { return visitor.visitAttr(this, context); };
    return HtmlAttrAst;
}());
exports.HtmlAttrAst = HtmlAttrAst;
var HtmlElementAst = (function () {
    function HtmlElementAst(name, attrs, children, sourceSpan, startSourceSpan, endSourceSpan) {
        this.name = name;
        this.attrs = attrs;
        this.children = children;
        this.sourceSpan = sourceSpan;
        this.startSourceSpan = startSourceSpan;
        this.endSourceSpan = endSourceSpan;
    }
    HtmlElementAst.prototype.visit = function (visitor, context) { return visitor.visitElement(this, context); };
    return HtmlElementAst;
}());
exports.HtmlElementAst = HtmlElementAst;
var HtmlCommentAst = (function () {
    function HtmlCommentAst(value, sourceSpan) {
        this.value = value;
        this.sourceSpan = sourceSpan;
    }
    HtmlCommentAst.prototype.visit = function (visitor, context) { return visitor.visitComment(this, context); };
    return HtmlCommentAst;
}());
exports.HtmlCommentAst = HtmlCommentAst;
function htmlVisitAll(visitor, asts, context) {
    if (context === void 0) { context = null; }
    var result = [];
    asts.forEach(function (ast) {
        var astResult = ast.visit(visitor, context);
        if (lang_1.isPresent(astResult)) {
            result.push(astResult);
        }
    });
    return result;
}
exports.htmlVisitAll = htmlVisitAll;
//# sourceMappingURL=html_ast.js.map