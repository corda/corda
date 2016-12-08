/**
 * @license
 * Copyright Google Inc. All Rights Reserved.
 *
 * Use of this source code is governed by an MIT-style license that can be
 * found in the LICENSE file at https://angular.io/license
 */
import { BaseException } from '../facade/exceptions';
import { HtmlAttrAst, HtmlElementAst, htmlVisitAll } from '../html_ast';
import { I18nError } from './shared';
// http://cldr.unicode.org/index/cldr-spec/plural-rules
const PLURAL_CASES = ['zero', 'one', 'two', 'few', 'many', 'other'];
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
export function expandNodes(nodes) {
    const expander = new _Expander();
    return new ExpansionResult(htmlVisitAll(expander, nodes), expander.isExpanded, expander.errors);
}
export class ExpansionResult {
    constructor(nodes, expanded, errors) {
        this.nodes = nodes;
        this.expanded = expanded;
        this.errors = errors;
    }
}
/**
 * Expand expansion forms (plural, select) to directives
 *
 * @internal
 */
class _Expander {
    constructor() {
        this.isExpanded = false;
        this.errors = [];
    }
    visitElement(ast, context) {
        return new HtmlElementAst(ast.name, ast.attrs, htmlVisitAll(this, ast.children), ast.sourceSpan, ast.startSourceSpan, ast.endSourceSpan);
    }
    visitAttr(ast, context) { return ast; }
    visitText(ast, context) { return ast; }
    visitComment(ast, context) { return ast; }
    visitExpansion(ast, context) {
        this.isExpanded = true;
        return ast.type == 'plural' ? _expandPluralForm(ast, this.errors) :
            _expandDefaultForm(ast, this.errors);
    }
    visitExpansionCase(ast, context) {
        throw new BaseException('Should not be reached');
    }
}
function _expandPluralForm(ast, errors) {
    const children = ast.cases.map(c => {
        if (PLURAL_CASES.indexOf(c.value) == -1 && !c.value.match(/^=\d+$/)) {
            errors.push(new I18nError(c.valueSourceSpan, `Plural cases should be "=<number>" or one of ${PLURAL_CASES.join(", ")}`));
        }
        const expansionResult = expandNodes(c.expression);
        errors.push(...expansionResult.errors);
        return new HtmlElementAst(`template`, [new HtmlAttrAst('ngPluralCase', `${c.value}`, c.valueSourceSpan)], expansionResult.nodes, c.sourceSpan, c.sourceSpan, c.sourceSpan);
    });
    const switchAttr = new HtmlAttrAst('[ngPlural]', ast.switchValue, ast.switchValueSourceSpan);
    return new HtmlElementAst('ng-container', [switchAttr], children, ast.sourceSpan, ast.sourceSpan, ast.sourceSpan);
}
function _expandDefaultForm(ast, errors) {
    let children = ast.cases.map(c => {
        const expansionResult = expandNodes(c.expression);
        errors.push(...expansionResult.errors);
        return new HtmlElementAst(`template`, [new HtmlAttrAst('ngSwitchCase', `${c.value}`, c.valueSourceSpan)], expansionResult.nodes, c.sourceSpan, c.sourceSpan, c.sourceSpan);
    });
    const switchAttr = new HtmlAttrAst('[ngSwitch]', ast.switchValue, ast.switchValueSourceSpan);
    return new HtmlElementAst('ng-container', [switchAttr], children, ast.sourceSpan, ast.sourceSpan, ast.sourceSpan);
}
//# sourceMappingURL=expander.js.map