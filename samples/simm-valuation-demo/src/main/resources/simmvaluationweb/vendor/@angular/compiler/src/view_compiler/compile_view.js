/**
 * @license
 * Copyright Google Inc. All Rights Reserved.
 *
 * Use of this source code is governed by an MIT-style license that can be
 * found in the LICENSE file at https://angular.io/license
 */
"use strict";
var core_private_1 = require('../../core_private');
var compile_metadata_1 = require('../compile_metadata');
var collection_1 = require('../facade/collection');
var lang_1 = require('../facade/lang');
var identifiers_1 = require('../identifiers');
var o = require('../output/output_ast');
var compile_method_1 = require('./compile_method');
var compile_pipe_1 = require('./compile_pipe');
var compile_query_1 = require('./compile_query');
var constants_1 = require('./constants');
var util_1 = require('./util');
var CompileView = (function () {
    function CompileView(component, genConfig, pipeMetas, styles, animations, viewIndex, declarationElement, templateVariableBindings) {
        var _this = this;
        this.component = component;
        this.genConfig = genConfig;
        this.pipeMetas = pipeMetas;
        this.styles = styles;
        this.viewIndex = viewIndex;
        this.declarationElement = declarationElement;
        this.templateVariableBindings = templateVariableBindings;
        this.nodes = [];
        // root nodes or AppElements for ViewContainers
        this.rootNodesOrAppElements = [];
        this.bindings = [];
        this.classStatements = [];
        this.eventHandlerMethods = [];
        this.fields = [];
        this.getters = [];
        this.disposables = [];
        this.subscriptions = [];
        this.purePipes = new Map();
        this.pipes = [];
        this.locals = new Map();
        this.literalArrayCount = 0;
        this.literalMapCount = 0;
        this.pipeCount = 0;
        this.animations = new Map();
        animations.forEach(function (entry) { return _this.animations.set(entry.name, entry); });
        this.createMethod = new compile_method_1.CompileMethod(this);
        this.injectorGetMethod = new compile_method_1.CompileMethod(this);
        this.updateContentQueriesMethod = new compile_method_1.CompileMethod(this);
        this.dirtyParentQueriesMethod = new compile_method_1.CompileMethod(this);
        this.updateViewQueriesMethod = new compile_method_1.CompileMethod(this);
        this.detectChangesInInputsMethod = new compile_method_1.CompileMethod(this);
        this.detectChangesRenderPropertiesMethod = new compile_method_1.CompileMethod(this);
        this.afterContentLifecycleCallbacksMethod = new compile_method_1.CompileMethod(this);
        this.afterViewLifecycleCallbacksMethod = new compile_method_1.CompileMethod(this);
        this.destroyMethod = new compile_method_1.CompileMethod(this);
        this.detachMethod = new compile_method_1.CompileMethod(this);
        this.viewType = getViewType(component, viewIndex);
        this.className = "_View_" + component.type.name + viewIndex;
        this.classType = o.importType(new compile_metadata_1.CompileIdentifierMetadata({ name: this.className }));
        this.viewFactory = o.variable(util_1.getViewFactoryName(component, viewIndex));
        if (this.viewType === core_private_1.ViewType.COMPONENT || this.viewType === core_private_1.ViewType.HOST) {
            this.componentView = this;
        }
        else {
            this.componentView = this.declarationElement.view.componentView;
        }
        this.componentContext =
            util_1.getPropertyInView(o.THIS_EXPR.prop('context'), this, this.componentView);
        var viewQueries = new compile_metadata_1.CompileTokenMap();
        if (this.viewType === core_private_1.ViewType.COMPONENT) {
            var directiveInstance = o.THIS_EXPR.prop('context');
            collection_1.ListWrapper.forEachWithIndex(this.component.viewQueries, function (queryMeta, queryIndex) {
                var propName = "_viewQuery_" + queryMeta.selectors[0].name + "_" + queryIndex;
                var queryList = compile_query_1.createQueryList(queryMeta, directiveInstance, propName, _this);
                var query = new compile_query_1.CompileQuery(queryMeta, queryList, directiveInstance, _this);
                compile_query_1.addQueryToTokenMap(viewQueries, query);
            });
            var constructorViewQueryCount = 0;
            this.component.type.diDeps.forEach(function (dep) {
                if (lang_1.isPresent(dep.viewQuery)) {
                    var queryList = o.THIS_EXPR.prop('declarationAppElement')
                        .prop('componentConstructorViewQueries')
                        .key(o.literal(constructorViewQueryCount++));
                    var query = new compile_query_1.CompileQuery(dep.viewQuery, queryList, null, _this);
                    compile_query_1.addQueryToTokenMap(viewQueries, query);
                }
            });
        }
        this.viewQueries = viewQueries;
        templateVariableBindings.forEach(function (entry) { _this.locals.set(entry[1], o.THIS_EXPR.prop('context').prop(entry[0])); });
        if (!this.declarationElement.isNull()) {
            this.declarationElement.setEmbeddedView(this);
        }
    }
    CompileView.prototype.callPipe = function (name, input, args) {
        return compile_pipe_1.CompilePipe.call(this, name, [input].concat(args));
    };
    CompileView.prototype.getLocal = function (name) {
        if (name == constants_1.EventHandlerVars.event.name) {
            return constants_1.EventHandlerVars.event;
        }
        var currView = this;
        var result = currView.locals.get(name);
        while (lang_1.isBlank(result) && lang_1.isPresent(currView.declarationElement.view)) {
            currView = currView.declarationElement.view;
            result = currView.locals.get(name);
        }
        if (lang_1.isPresent(result)) {
            return util_1.getPropertyInView(result, this, currView);
        }
        else {
            return null;
        }
    };
    CompileView.prototype.createLiteralArray = function (values) {
        if (values.length === 0) {
            return o.importExpr(identifiers_1.Identifiers.EMPTY_ARRAY);
        }
        var proxyExpr = o.THIS_EXPR.prop("_arr_" + this.literalArrayCount++);
        var proxyParams = [];
        var proxyReturnEntries = [];
        for (var i = 0; i < values.length; i++) {
            var paramName = "p" + i;
            proxyParams.push(new o.FnParam(paramName));
            proxyReturnEntries.push(o.variable(paramName));
        }
        util_1.createPureProxy(o.fn(proxyParams, [new o.ReturnStatement(o.literalArr(proxyReturnEntries))], new o.ArrayType(o.DYNAMIC_TYPE)), values.length, proxyExpr, this);
        return proxyExpr.callFn(values);
    };
    CompileView.prototype.createLiteralMap = function (entries) {
        if (entries.length === 0) {
            return o.importExpr(identifiers_1.Identifiers.EMPTY_MAP);
        }
        var proxyExpr = o.THIS_EXPR.prop("_map_" + this.literalMapCount++);
        var proxyParams = [];
        var proxyReturnEntries = [];
        var values = [];
        for (var i = 0; i < entries.length; i++) {
            var paramName = "p" + i;
            proxyParams.push(new o.FnParam(paramName));
            proxyReturnEntries.push([entries[i][0], o.variable(paramName)]);
            values.push(entries[i][1]);
        }
        util_1.createPureProxy(o.fn(proxyParams, [new o.ReturnStatement(o.literalMap(proxyReturnEntries))], new o.MapType(o.DYNAMIC_TYPE)), entries.length, proxyExpr, this);
        return proxyExpr.callFn(values);
    };
    CompileView.prototype.afterNodes = function () {
        var _this = this;
        this.pipes.forEach(function (pipe) { return pipe.create(); });
        this.viewQueries.values().forEach(function (queries) { return queries.forEach(function (query) { return query.afterChildren(_this.createMethod, _this.updateViewQueriesMethod); }); });
    };
    return CompileView;
}());
exports.CompileView = CompileView;
function getViewType(component, embeddedTemplateIndex) {
    if (embeddedTemplateIndex > 0) {
        return core_private_1.ViewType.EMBEDDED;
    }
    else if (component.type.isHost) {
        return core_private_1.ViewType.HOST;
    }
    else {
        return core_private_1.ViewType.COMPONENT;
    }
}
//# sourceMappingURL=compile_view.js.map