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
var core_1 = require('@angular/core');
var collection_1 = require('../facade/collection');
var lang_1 = require('../facade/lang');
var identifiers_1 = require('../identifiers');
var o = require('../output/output_ast');
var template_ast_1 = require('../template_ast');
var constants_1 = require('./constants');
var compile_metadata_1 = require('../compile_metadata');
var util_1 = require('./util');
var compile_query_1 = require('./compile_query');
var compile_method_1 = require('./compile_method');
var util_2 = require('../util');
var CompileNode = (function () {
    function CompileNode(parent, view, nodeIndex, renderNode, sourceAst) {
        this.parent = parent;
        this.view = view;
        this.nodeIndex = nodeIndex;
        this.renderNode = renderNode;
        this.sourceAst = sourceAst;
    }
    CompileNode.prototype.isNull = function () { return lang_1.isBlank(this.renderNode); };
    CompileNode.prototype.isRootElement = function () { return this.view != this.parent.view; };
    return CompileNode;
}());
exports.CompileNode = CompileNode;
var CompileElement = (function (_super) {
    __extends(CompileElement, _super);
    function CompileElement(parent, view, nodeIndex, renderNode, sourceAst, component, _directives, _resolvedProvidersArray, hasViewContainer, hasEmbeddedView, references) {
        var _this = this;
        _super.call(this, parent, view, nodeIndex, renderNode, sourceAst);
        this.component = component;
        this._directives = _directives;
        this._resolvedProvidersArray = _resolvedProvidersArray;
        this.hasViewContainer = hasViewContainer;
        this.hasEmbeddedView = hasEmbeddedView;
        this._compViewExpr = null;
        this._instances = new compile_metadata_1.CompileTokenMap();
        this._queryCount = 0;
        this._queries = new compile_metadata_1.CompileTokenMap();
        this._componentConstructorViewQueryLists = [];
        this.contentNodesByNgContentIndex = null;
        this.referenceTokens = {};
        references.forEach(function (ref) { return _this.referenceTokens[ref.name] = ref.value; });
        this.elementRef = o.importExpr(identifiers_1.Identifiers.ElementRef).instantiate([this.renderNode]);
        this._instances.add(identifiers_1.identifierToken(identifiers_1.Identifiers.ElementRef), this.elementRef);
        this.injector = o.THIS_EXPR.callMethod('injector', [o.literal(this.nodeIndex)]);
        this._instances.add(identifiers_1.identifierToken(identifiers_1.Identifiers.Injector), this.injector);
        this._instances.add(identifiers_1.identifierToken(identifiers_1.Identifiers.Renderer), o.THIS_EXPR.prop('renderer'));
        if (this.hasViewContainer || this.hasEmbeddedView || lang_1.isPresent(this.component)) {
            this._createAppElement();
        }
    }
    CompileElement.createNull = function () {
        return new CompileElement(null, null, null, null, null, null, [], [], false, false, []);
    };
    CompileElement.prototype._createAppElement = function () {
        var fieldName = "_appEl_" + this.nodeIndex;
        var parentNodeIndex = this.isRootElement() ? null : this.parent.nodeIndex;
        // private is fine here as no child view will reference an AppElement
        this.view.fields.push(new o.ClassField(fieldName, o.importType(identifiers_1.Identifiers.AppElement), [o.StmtModifier.Private]));
        var statement = o.THIS_EXPR.prop(fieldName)
            .set(o.importExpr(identifiers_1.Identifiers.AppElement).instantiate([
            o.literal(this.nodeIndex), o.literal(parentNodeIndex), o.THIS_EXPR, this.renderNode
        ]))
            .toStmt();
        this.view.createMethod.addStmt(statement);
        this.appElement = o.THIS_EXPR.prop(fieldName);
        this._instances.add(identifiers_1.identifierToken(identifiers_1.Identifiers.AppElement), this.appElement);
    };
    CompileElement.prototype.createComponentFactoryResolver = function (precompileComponent) {
        if (!precompileComponent || precompileComponent.length === 0) {
            return;
        }
        var createComponentFactoryResolverExpr = o.importExpr(identifiers_1.Identifiers.CodegenComponentFactoryResolver).instantiate([
            o.literalArr(precompileComponent.map(function (precompiledComponent) { return o.importExpr(precompiledComponent); })),
            util_1.injectFromViewParentInjector(identifiers_1.identifierToken(identifiers_1.Identifiers.ComponentFactoryResolver), false)
        ]);
        var provider = new compile_metadata_1.CompileProviderMetadata({
            token: identifiers_1.identifierToken(identifiers_1.Identifiers.ComponentFactoryResolver),
            useValue: createComponentFactoryResolverExpr
        });
        // Add ComponentFactoryResolver as first provider as it does not have deps on other providers
        // ProviderAstType.PrivateService as only the component and its view can see it,
        // but nobody else
        this._resolvedProvidersArray.unshift(new template_ast_1.ProviderAst(provider.token, false, true, [provider], template_ast_1.ProviderAstType.PrivateService, this.sourceAst.sourceSpan));
    };
    CompileElement.prototype.setComponentView = function (compViewExpr) {
        this._compViewExpr = compViewExpr;
        this.contentNodesByNgContentIndex =
            collection_1.ListWrapper.createFixedSize(this.component.template.ngContentSelectors.length);
        for (var i = 0; i < this.contentNodesByNgContentIndex.length; i++) {
            this.contentNodesByNgContentIndex[i] = [];
        }
    };
    CompileElement.prototype.setEmbeddedView = function (embeddedView) {
        this.embeddedView = embeddedView;
        if (lang_1.isPresent(embeddedView)) {
            var createTemplateRefExpr = o.importExpr(identifiers_1.Identifiers.TemplateRef_).instantiate([
                this.appElement, this.embeddedView.viewFactory
            ]);
            var provider = new compile_metadata_1.CompileProviderMetadata({ token: identifiers_1.identifierToken(identifiers_1.Identifiers.TemplateRef), useValue: createTemplateRefExpr });
            // Add TemplateRef as first provider as it does not have deps on other providers
            this._resolvedProvidersArray.unshift(new template_ast_1.ProviderAst(provider.token, false, true, [provider], template_ast_1.ProviderAstType.Builtin, this.sourceAst.sourceSpan));
        }
    };
    CompileElement.prototype.beforeChildren = function () {
        var _this = this;
        if (this.hasViewContainer) {
            this._instances.add(identifiers_1.identifierToken(identifiers_1.Identifiers.ViewContainerRef), this.appElement.prop('vcRef'));
        }
        this._resolvedProviders = new compile_metadata_1.CompileTokenMap();
        this._resolvedProvidersArray.forEach(function (provider) { return _this._resolvedProviders.add(provider.token, provider); });
        // create all the provider instances, some in the view constructor,
        // some as getters. We rely on the fact that they are already sorted topologically.
        this._resolvedProviders.values().forEach(function (resolvedProvider) {
            var providerValueExpressions = resolvedProvider.providers.map(function (provider) {
                if (lang_1.isPresent(provider.useExisting)) {
                    return _this._getDependency(resolvedProvider.providerType, new compile_metadata_1.CompileDiDependencyMetadata({ token: provider.useExisting }));
                }
                else if (lang_1.isPresent(provider.useFactory)) {
                    var deps = lang_1.isPresent(provider.deps) ? provider.deps : provider.useFactory.diDeps;
                    var depsExpr = deps.map(function (dep) { return _this._getDependency(resolvedProvider.providerType, dep); });
                    return o.importExpr(provider.useFactory).callFn(depsExpr);
                }
                else if (lang_1.isPresent(provider.useClass)) {
                    var deps = lang_1.isPresent(provider.deps) ? provider.deps : provider.useClass.diDeps;
                    var depsExpr = deps.map(function (dep) { return _this._getDependency(resolvedProvider.providerType, dep); });
                    return o.importExpr(provider.useClass)
                        .instantiate(depsExpr, o.importType(provider.useClass));
                }
                else {
                    return _convertValueToOutputAst(provider.useValue);
                }
            });
            var propName = "_" + resolvedProvider.token.name + "_" + _this.nodeIndex + "_" + _this._instances.size;
            var instance = createProviderProperty(propName, resolvedProvider, providerValueExpressions, resolvedProvider.multiProvider, resolvedProvider.eager, _this);
            _this._instances.add(resolvedProvider.token, instance);
        });
        this.directiveInstances =
            this._directives.map(function (directive) { return _this._instances.get(identifiers_1.identifierToken(directive.type)); });
        for (var i = 0; i < this.directiveInstances.length; i++) {
            var directiveInstance = this.directiveInstances[i];
            var directive = this._directives[i];
            directive.queries.forEach(function (queryMeta) { _this._addQuery(queryMeta, directiveInstance); });
        }
        var queriesWithReads = [];
        this._resolvedProviders.values().forEach(function (resolvedProvider) {
            var queriesForProvider = _this._getQueriesFor(resolvedProvider.token);
            collection_1.ListWrapper.addAll(queriesWithReads, queriesForProvider.map(function (query) { return new _QueryWithRead(query, resolvedProvider.token); }));
        });
        collection_1.StringMapWrapper.forEach(this.referenceTokens, function (_, varName) {
            var token = _this.referenceTokens[varName];
            var varValue;
            if (lang_1.isPresent(token)) {
                varValue = _this._instances.get(token);
            }
            else {
                varValue = _this.renderNode;
            }
            _this.view.locals.set(varName, varValue);
            var varToken = new compile_metadata_1.CompileTokenMetadata({ value: varName });
            collection_1.ListWrapper.addAll(queriesWithReads, _this._getQueriesFor(varToken).map(function (query) { return new _QueryWithRead(query, varToken); }));
        });
        queriesWithReads.forEach(function (queryWithRead) {
            var value;
            if (lang_1.isPresent(queryWithRead.read.identifier)) {
                // query for an identifier
                value = _this._instances.get(queryWithRead.read);
            }
            else {
                // query for a reference
                var token = _this.referenceTokens[queryWithRead.read.value];
                if (lang_1.isPresent(token)) {
                    value = _this._instances.get(token);
                }
                else {
                    value = _this.elementRef;
                }
            }
            if (lang_1.isPresent(value)) {
                queryWithRead.query.addValue(value, _this.view);
            }
        });
        if (lang_1.isPresent(this.component)) {
            var componentConstructorViewQueryList = lang_1.isPresent(this.component) ?
                o.literalArr(this._componentConstructorViewQueryLists) :
                o.NULL_EXPR;
            var compExpr = lang_1.isPresent(this.getComponent()) ? this.getComponent() : o.NULL_EXPR;
            this.view.createMethod.addStmt(this.appElement
                .callMethod('initComponent', [compExpr, componentConstructorViewQueryList, this._compViewExpr])
                .toStmt());
        }
    };
    CompileElement.prototype.afterChildren = function (childNodeCount) {
        var _this = this;
        this._resolvedProviders.values().forEach(function (resolvedProvider) {
            // Note: afterChildren is called after recursing into children.
            // This is good so that an injector match in an element that is closer to a requesting element
            // matches first.
            var providerExpr = _this._instances.get(resolvedProvider.token);
            // Note: view providers are only visible on the injector of that element.
            // This is not fully correct as the rules during codegen don't allow a directive
            // to get hold of a view provdier on the same element. We still do this semantic
            // as it simplifies our model to having only one runtime injector per element.
            var providerChildNodeCount = resolvedProvider.providerType === template_ast_1.ProviderAstType.PrivateService ? 0 : childNodeCount;
            _this.view.injectorGetMethod.addStmt(createInjectInternalCondition(_this.nodeIndex, providerChildNodeCount, resolvedProvider, providerExpr));
        });
        this._queries.values().forEach(function (queries) { return queries.forEach(function (query) {
            return query.afterChildren(_this.view.createMethod, _this.view.updateContentQueriesMethod);
        }); });
    };
    CompileElement.prototype.addContentNode = function (ngContentIndex, nodeExpr) {
        this.contentNodesByNgContentIndex[ngContentIndex].push(nodeExpr);
    };
    CompileElement.prototype.getComponent = function () {
        return lang_1.isPresent(this.component) ? this._instances.get(identifiers_1.identifierToken(this.component.type)) :
            null;
    };
    CompileElement.prototype.getProviderTokens = function () {
        return this._resolvedProviders.values().map(function (resolvedProvider) { return util_1.createDiTokenExpression(resolvedProvider.token); });
    };
    CompileElement.prototype._getQueriesFor = function (token) {
        var result = [];
        var currentEl = this;
        var distance = 0;
        var queries;
        while (!currentEl.isNull()) {
            queries = currentEl._queries.get(token);
            if (lang_1.isPresent(queries)) {
                collection_1.ListWrapper.addAll(result, queries.filter(function (query) { return query.meta.descendants || distance <= 1; }));
            }
            if (currentEl._directives.length > 0) {
                distance++;
            }
            currentEl = currentEl.parent;
        }
        queries = this.view.componentView.viewQueries.get(token);
        if (lang_1.isPresent(queries)) {
            collection_1.ListWrapper.addAll(result, queries);
        }
        return result;
    };
    CompileElement.prototype._addQuery = function (queryMeta, directiveInstance) {
        var propName = "_query_" + queryMeta.selectors[0].name + "_" + this.nodeIndex + "_" + this._queryCount++;
        var queryList = compile_query_1.createQueryList(queryMeta, directiveInstance, propName, this.view);
        var query = new compile_query_1.CompileQuery(queryMeta, queryList, directiveInstance, this.view);
        compile_query_1.addQueryToTokenMap(this._queries, query);
        return query;
    };
    CompileElement.prototype._getLocalDependency = function (requestingProviderType, dep) {
        var result = null;
        // constructor content query
        if (lang_1.isBlank(result) && lang_1.isPresent(dep.query)) {
            result = this._addQuery(dep.query, null).queryList;
        }
        // constructor view query
        if (lang_1.isBlank(result) && lang_1.isPresent(dep.viewQuery)) {
            result = compile_query_1.createQueryList(dep.viewQuery, null, "_viewQuery_" + dep.viewQuery.selectors[0].name + "_" + this.nodeIndex + "_" + this._componentConstructorViewQueryLists.length, this.view);
            this._componentConstructorViewQueryLists.push(result);
        }
        if (lang_1.isPresent(dep.token)) {
            // access builtins with special visibility
            if (lang_1.isBlank(result)) {
                if (dep.token.equalsTo(identifiers_1.identifierToken(identifiers_1.Identifiers.ChangeDetectorRef))) {
                    if (requestingProviderType === template_ast_1.ProviderAstType.Component) {
                        return this._compViewExpr.prop('ref');
                    }
                    else {
                        return util_1.getPropertyInView(o.THIS_EXPR.prop('ref'), this.view, this.view.componentView);
                    }
                }
            }
            // access regular providers on the element
            if (lang_1.isBlank(result)) {
                var resolvedProvider = this._resolvedProviders.get(dep.token);
                // don't allow directives / public services to access private services.
                // only components and private services can access private services.
                if (resolvedProvider && (requestingProviderType === template_ast_1.ProviderAstType.Directive ||
                    requestingProviderType === template_ast_1.ProviderAstType.PublicService) &&
                    resolvedProvider.providerType === template_ast_1.ProviderAstType.PrivateService) {
                    return null;
                }
                result = this._instances.get(dep.token);
            }
        }
        return result;
    };
    CompileElement.prototype._getDependency = function (requestingProviderType, dep) {
        var currElement = this;
        var result = null;
        if (dep.isValue) {
            result = o.literal(dep.value);
        }
        if (lang_1.isBlank(result) && !dep.isSkipSelf) {
            result = this._getLocalDependency(requestingProviderType, dep);
        }
        // check parent elements
        while (lang_1.isBlank(result) && !currElement.parent.isNull()) {
            currElement = currElement.parent;
            result = currElement._getLocalDependency(template_ast_1.ProviderAstType.PublicService, new compile_metadata_1.CompileDiDependencyMetadata({ token: dep.token }));
        }
        if (lang_1.isBlank(result)) {
            result = util_1.injectFromViewParentInjector(dep.token, dep.isOptional);
        }
        if (lang_1.isBlank(result)) {
            result = o.NULL_EXPR;
        }
        return util_1.getPropertyInView(result, this.view, currElement.view);
    };
    return CompileElement;
}(CompileNode));
exports.CompileElement = CompileElement;
function createInjectInternalCondition(nodeIndex, childNodeCount, provider, providerExpr) {
    var indexCondition;
    if (childNodeCount > 0) {
        indexCondition = o.literal(nodeIndex)
            .lowerEquals(constants_1.InjectMethodVars.requestNodeIndex)
            .and(constants_1.InjectMethodVars.requestNodeIndex.lowerEquals(o.literal(nodeIndex + childNodeCount)));
    }
    else {
        indexCondition = o.literal(nodeIndex).identical(constants_1.InjectMethodVars.requestNodeIndex);
    }
    return new o.IfStmt(constants_1.InjectMethodVars.token.identical(util_1.createDiTokenExpression(provider.token)).and(indexCondition), [new o.ReturnStatement(providerExpr)]);
}
function createProviderProperty(propName, provider, providerValueExpressions, isMulti, isEager, compileElement) {
    var view = compileElement.view;
    var resolvedProviderValueExpr;
    var type;
    if (isMulti) {
        resolvedProviderValueExpr = o.literalArr(providerValueExpressions);
        type = new o.ArrayType(o.DYNAMIC_TYPE);
    }
    else {
        resolvedProviderValueExpr = providerValueExpressions[0];
        type = providerValueExpressions[0].type;
    }
    if (lang_1.isBlank(type)) {
        type = o.DYNAMIC_TYPE;
    }
    if (isEager) {
        view.fields.push(new o.ClassField(propName, type));
        view.createMethod.addStmt(o.THIS_EXPR.prop(propName).set(resolvedProviderValueExpr).toStmt());
    }
    else {
        var internalField = "_" + propName;
        view.fields.push(new o.ClassField(internalField, type));
        var getter = new compile_method_1.CompileMethod(view);
        getter.resetDebugInfo(compileElement.nodeIndex, compileElement.sourceAst);
        // Note: Equals is important for JS so that it also checks the undefined case!
        getter.addStmt(new o.IfStmt(o.THIS_EXPR.prop(internalField).isBlank(), [o.THIS_EXPR.prop(internalField).set(resolvedProviderValueExpr).toStmt()]));
        getter.addStmt(new o.ReturnStatement(o.THIS_EXPR.prop(internalField)));
        view.getters.push(new o.ClassGetter(propName, getter.finish(), type));
    }
    return o.THIS_EXPR.prop(propName);
}
var _QueryWithRead = (function () {
    function _QueryWithRead(query, match) {
        this.query = query;
        this.read = lang_1.isPresent(query.meta.read) ? query.meta.read : match;
    }
    return _QueryWithRead;
}());
function _convertValueToOutputAst(value) {
    return util_2.visitValue(value, new _ValueOutputAstTransformer(), null);
}
var _ValueOutputAstTransformer = (function (_super) {
    __extends(_ValueOutputAstTransformer, _super);
    function _ValueOutputAstTransformer() {
        _super.apply(this, arguments);
    }
    _ValueOutputAstTransformer.prototype.visitArray = function (arr, context) {
        var _this = this;
        return o.literalArr(arr.map(function (value) { return util_2.visitValue(value, _this, context); }));
    };
    _ValueOutputAstTransformer.prototype.visitStringMap = function (map, context) {
        var _this = this;
        var entries = [];
        collection_1.StringMapWrapper.forEach(map, function (value, key) {
            entries.push([key, util_2.visitValue(value, _this, context)]);
        });
        return o.literalMap(entries);
    };
    _ValueOutputAstTransformer.prototype.visitPrimitive = function (value, context) { return o.literal(value); };
    _ValueOutputAstTransformer.prototype.visitOther = function (value, context) {
        if (value instanceof compile_metadata_1.CompileIdentifierMetadata) {
            return o.importExpr(value);
        }
        else if (value instanceof o.Expression) {
            return value;
        }
        else {
            throw new core_1.BaseException("Illegal state: Don't now how to compile value " + value);
        }
    };
    return _ValueOutputAstTransformer;
}(util_2.ValueTransformer));
//# sourceMappingURL=compile_element.js.map