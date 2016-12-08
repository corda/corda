/**
 * @license
 * Copyright Google Inc. All Rights Reserved.
 *
 * Use of this source code is governed by an MIT-style license that can be
 * found in the LICENSE file at https://angular.io/license
 */
import { ListWrapper } from '../facade/collection';
import { isBlank, isPresent } from '../facade/lang';
import { Identifiers } from '../identifiers';
import * as o from '../output/output_ast';
import { getPropertyInView } from './util';
class ViewQueryValues {
    constructor(view, values) {
        this.view = view;
        this.values = values;
    }
}
export class CompileQuery {
    constructor(meta, queryList, ownerDirectiveExpression, view) {
        this.meta = meta;
        this.queryList = queryList;
        this.ownerDirectiveExpression = ownerDirectiveExpression;
        this.view = view;
        this._values = new ViewQueryValues(view, []);
    }
    addValue(value, view) {
        var currentView = view;
        var elPath = [];
        while (isPresent(currentView) && currentView !== this.view) {
            var parentEl = currentView.declarationElement;
            elPath.unshift(parentEl);
            currentView = parentEl.view;
        }
        var queryListForDirtyExpr = getPropertyInView(this.queryList, view, this.view);
        var viewValues = this._values;
        elPath.forEach((el) => {
            var last = viewValues.values.length > 0 ? viewValues.values[viewValues.values.length - 1] : null;
            if (last instanceof ViewQueryValues && last.view === el.embeddedView) {
                viewValues = last;
            }
            else {
                var newViewValues = new ViewQueryValues(el.embeddedView, []);
                viewValues.values.push(newViewValues);
                viewValues = newViewValues;
            }
        });
        viewValues.values.push(value);
        if (elPath.length > 0) {
            view.dirtyParentQueriesMethod.addStmt(queryListForDirtyExpr.callMethod('setDirty', []).toStmt());
        }
    }
    _isStatic() {
        return !this._values.values.some(value => value instanceof ViewQueryValues);
    }
    afterChildren(targetStaticMethod /** TODO #9100 */, targetDynamicMethod) {
        var values = createQueryValues(this._values);
        var updateStmts = [this.queryList.callMethod('reset', [o.literalArr(values)]).toStmt()];
        if (isPresent(this.ownerDirectiveExpression)) {
            var valueExpr = this.meta.first ? this.queryList.prop('first') : this.queryList;
            updateStmts.push(this.ownerDirectiveExpression.prop(this.meta.propertyName).set(valueExpr).toStmt());
        }
        if (!this.meta.first) {
            updateStmts.push(this.queryList.callMethod('notifyOnChanges', []).toStmt());
        }
        if (this.meta.first && this._isStatic()) {
            // for queries that don't change and the user asked for a single element,
            // set it immediately. That is e.g. needed for querying for ViewContainerRefs, ...
            // we don't do this for QueryLists for now as this would break the timing when
            // we call QueryList listeners...
            targetStaticMethod.addStmts(updateStmts);
        }
        else {
            targetDynamicMethod.addStmt(new o.IfStmt(this.queryList.prop('dirty'), updateStmts));
        }
    }
}
function createQueryValues(viewValues) {
    return ListWrapper.flatten(viewValues.values.map((entry) => {
        if (entry instanceof ViewQueryValues) {
            return mapNestedViews(entry.view.declarationElement.appElement, entry.view, createQueryValues(entry));
        }
        else {
            return entry;
        }
    }));
}
function mapNestedViews(declarationAppElement, view, expressions) {
    var adjustedExpressions = expressions.map((expr) => {
        return o.replaceVarInExpression(o.THIS_EXPR.name, o.variable('nestedView'), expr);
    });
    return declarationAppElement.callMethod('mapNestedViews', [
        o.variable(view.className), o.fn([new o.FnParam('nestedView', view.classType)], [new o.ReturnStatement(o.literalArr(adjustedExpressions))])
    ]);
}
export function createQueryList(query, directiveInstance, propertyName, compileView) {
    compileView.fields.push(new o.ClassField(propertyName, o.importType(Identifiers.QueryList, [o.DYNAMIC_TYPE])));
    var expr = o.THIS_EXPR.prop(propertyName);
    compileView.createMethod.addStmt(o.THIS_EXPR.prop(propertyName)
        .set(o.importExpr(Identifiers.QueryList, [o.DYNAMIC_TYPE]).instantiate([]))
        .toStmt());
    return expr;
}
export function addQueryToTokenMap(map, query) {
    query.meta.selectors.forEach((selector) => {
        var entry = map.get(selector);
        if (isBlank(entry)) {
            entry = [];
            map.add(selector, entry);
        }
        entry.push(query);
    });
}
//# sourceMappingURL=compile_query.js.map