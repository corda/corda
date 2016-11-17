/**
 * @license
 * Copyright Google Inc. All Rights Reserved.
 *
 * Use of this source code is governed by an MIT-style license that can be
 * found in the LICENSE file at https://angular.io/license
 */
"use strict";
var exceptions_1 = require('../facade/exceptions');
var html_ast_1 = require('../html_ast');
var shared_1 = require('./shared');
// http://cldr.unicode.org/index/cldr-spec/plural-rules
var PLURAL_CASES = ['zero', 'one', 'two', 'few', 'many', 'other'];
/**
 * Expands special forms into elements.
 *
 * For example,
 *
 * ```
 * { messages.length, plural,
 *   =0 {zero}
 *   =1 {one}
 *   other {more than one}
 * }
 * ```
 *
 * will be expanded into
 *
 * ```
 * <ng-container [ngPlural]="messages.length">
 *   <template ngPluralCase="=0">zero</ng-container>
 *   <template ngPluralCase="=1">one</ng-container>
 *   <template ngPluralCase="other">more than one</ng-container>
 * </ng-container>
 * ```
 */
function expandNodes(nodes) {
    var expander = new _Expander();
    return new ExpansionResult(html_ast_1.htmlVisitAll(expander, nodes), expander.isExpanded, expander.errors);
}
exports.expandNodes = expandNodes;
var ExpansionResult = (function () {
    function ExpansionResult(nodes, expanded, errors) {
        this.nodes = nodes;
        this.expanded = expanded;
        this.errors = errors;
    }
    return ExpansionResult;
}());
exports.ExpansionResult = ExpansionResult;
/**
 * Expand expansion forms (plural, select) to directives
 *
 * @internal
 */
var _Expander = (function () {
    function _Expander() {
        this.isExpanded = false;
        this.errors = [];
    }
    _Expander.prototype.visitElement = function (ast, context) {
        return new html_ast_1.HtmlElementAst(ast.name, ast.attrs, html_ast_1.htmlVisitAll(this, ast.children), ast.sourceSpan, ast.startSourceSpan, ast.endSourceSpan);
    };
    _Expander.prototype.visitAttr = function (ast, context) { return ast; };
    _Expander.prototype.visitText = function (ast, context) { return ast; };
    _Expander.prototype.visitComment = function (ast, context) { return ast; };
    _Expander.prototype.visitExpansion = function (ast, context) {
        this.isExpanded = true;
        return ast.type == 'plural' ? _expandPluralForm(ast, this.errors) :
            _expandDefaultForm(ast, this.errors);
    };
    _Expander.prototype.visitExpansionCase = function (ast, context) {
        throw new exceptions_1.BaseException('Should not be reached');
    };
    return _Expander;
}());
function _expandPluralForm(ast, errors) {
    var children = ast.cases.map(function (c) {
        if (PLURAL_CASES.indexOf(c.value) == -1 && !c.value.match(/^=\d+$/)) {
            errors.push(new shared_1.I18nError(c.valueSourceSpan, "Plural cases should be \"=<number>\" or one of " + PLURAL_CASES.join(", ")));
        }
        var expansionResult = expandNodes(c.expression);
        errors.push.apply(errors, expansionResult.errors);
        return new html_ast_1.HtmlElementAst("template", [new html_ast_1.HtmlAttrAst('ngPluralCase', "" + c.value, c.valueSourceSpan)], expansionResult.nodes, c.sourceSpan, c.sourceSpan, c.sourceSpan);
    });
    var switchAttr = new html_ast_1.HtmlAttrAst('[ngPlural]', ast.switchValue, ast.switchValueSourceSpan);
    return new html_ast_1.HtmlElementAst('ng-container', [switchAttr], children, ast.sourceSpan, ast.sourceSpan, ast.sourceSpan);
}
function _expandDefaultForm(ast, errors) {
    var children = ast.cases.map(function (c) {
        var expansionResult = expandNodes(c.expression);
        errors.push.apply(errors, expansionResult.errors);
        return new html_ast_1.HtmlElementAst("template", [new html_ast_1.HtmlAttrAst('ngSwitchCase', "" + c.value, c.valueSourceSpan)], expansionResult.nodes, c.sourceSpan, c.sourceSpan, c.sourceSpan);
    });
    var switchAttr = new html_ast_1.HtmlAttrAst('[ngSwitch]', ast.switchValue, ast.switchValueSourceSpan);
    return new html_ast_1.HtmlElementAst('ng-container', [switchAttr], children, ast.sourceSpan, ast.sourceSpan, ast.sourceSpan);
}
//# sourceMappingURL=expander.js.map