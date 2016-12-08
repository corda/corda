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
var identifiers_1 = require('../identifiers');
var o = require('../output/output_ast');
var util_1 = require('./util');
var ViewQueryValues = (function () {
    function ViewQueryValues(view, values) {
        this.view = view;
        this.values = values;
    }
    return ViewQueryValues;
}());
var CompileQuery = (function () {
    function CompileQuery(meta, queryList, ownerDirectiveExpression, view) {
        this.meta = meta;
        this.queryList = queryList;
        this.ownerDirectiveExpression = ownerDirectiveExpression;
        this.view = view;
        this._values = new ViewQueryValues(view, []);
    }
    CompileQuery.prototype.addValue = function (value, view) {
        var currentView = view;
        var elPath = [];
        while (lang_1.isPresent(currentView) && currentView !== this.view) {
            var parentEl = currentView.declarationElement;
            elPath.unshift(parentEl);
            currentView = parentEl.view;
        }
        var queryListForDirtyExpr = util_1.getPropertyInView(this.queryList, view, this.view);
        var viewValues = this._values;
        elPath.forEach(function (el) {
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
    };
    CompileQuery.prototype._isStatic = function () {
        return !this._values.values.some(function (value) { return value instanceof ViewQueryValues; });
    };
    CompileQuery.prototype.afterChildren = function (targetStaticMethod /** TODO #9100 */, targetDynamicMethod) {
        var values = createQueryValues(this._values);
        var updateStmts = [this.queryList.callMethod('reset', [o.literalArr(values)]).toStmt()];
        if (lang_1.isPresent(this.ownerDirectiveExpression)) {
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
    };
    return CompileQuery;
}());
exports.CompileQuery = CompileQuery;
function createQueryValues(viewValues) {
    return collection_1.ListWrapper.flatten(viewValues.values.map(function (entry) {
        if (entry instanceof ViewQueryValues) {
            return mapNestedViews(entry.view.declarationElement.appElement, entry.view, createQueryValues(entry));
        }
        else {
            return entry;
        }
    }));
}
function mapNestedViews(declarationAppElement, view, expressions) {
    var adjustedExpressions = expressions.map(function (expr) {
        return o.replaceVarInExpression(o.THIS_EXPR.name, o.variable('nestedView'), expr);
    });
    return declarationAppElement.callMethod('mapNestedViews', [
        o.variable(view.className), o.fn([new o.FnParam('nestedView', view.classType)], [new o.ReturnStatement(o.literalArr(adjustedExpressions))])
    ]);
}
function createQueryList(query, directiveInstance, propertyName, compileView) {
    compileView.fields.push(new o.ClassField(propertyName, o.importType(identifiers_1.Identifiers.QueryList, [o.DYNAMIC_TYPE])));
    var expr = o.THIS_EXPR.prop(propertyName);
    compileView.createMethod.addStmt(o.THIS_EXPR.prop(propertyName)
        .set(o.importExpr(identifiers_1.Identifiers.QueryList, [o.DYNAMIC_TYPE]).instantiate([]))
        .toStmt());
    return expr;
}
exports.createQueryList = createQueryList;
function addQueryToTokenMap(map, query) {
    query.meta.selectors.forEach(function (selector) {
        var entry = map.get(selector);
        if (lang_1.isBlank(entry)) {
            entry = [];
            map.add(selector, entry);
        }
        entry.push(query);
    });
}
exports.addQueryToTokenMap = addQueryToTokenMap;
//# sourceMappingURL=compile_query.js.map