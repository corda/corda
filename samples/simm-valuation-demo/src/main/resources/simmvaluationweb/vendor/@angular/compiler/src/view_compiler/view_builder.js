/**
 * @license
 * Copyright Google Inc. All Rights Reserved.
 *
 * Use of this source code is governed by an MIT-style license that can be
 * found in the LICENSE file at https://angular.io/license
 */
"use strict";
var core_1 = require('@angular/core');
var core_private_1 = require('../../core_private');
var animation_compiler_1 = require('../animation/animation_compiler');
var compile_metadata_1 = require('../compile_metadata');
var collection_1 = require('../facade/collection');
var lang_1 = require('../facade/lang');
var identifiers_1 = require('../identifiers');
var o = require('../output/output_ast');
var template_ast_1 = require('../template_ast');
var compile_element_1 = require('./compile_element');
var compile_view_1 = require('./compile_view');
var constants_1 = require('./constants');
var util_1 = require('./util');
var IMPLICIT_TEMPLATE_VAR = '\$implicit';
var CLASS_ATTR = 'class';
var STYLE_ATTR = 'style';
var NG_CONTAINER_TAG = 'ng-container';
var parentRenderNodeVar = o.variable('parentRenderNode');
var rootSelectorVar = o.variable('rootSelector');
var ViewFactoryDependency = (function () {
    function ViewFactoryDependency(comp, placeholder) {
        this.comp = comp;
        this.placeholder = placeholder;
    }
    return ViewFactoryDependency;
}());
exports.ViewFactoryDependency = ViewFactoryDependency;
var ComponentFactoryDependency = (function () {
    function ComponentFactoryDependency(comp, placeholder) {
        this.comp = comp;
        this.placeholder = placeholder;
    }
    return ComponentFactoryDependency;
}());
exports.ComponentFactoryDependency = ComponentFactoryDependency;
function buildView(view, template, targetDependencies) {
    var builderVisitor = new ViewBuilderVisitor(view, targetDependencies);
    template_ast_1.templateVisitAll(builderVisitor, template, view.declarationElement.isNull() ? view.declarationElement : view.declarationElement.parent);
    return builderVisitor.nestedViewCount;
}
exports.buildView = buildView;
function finishView(view, targetStatements) {
    view.afterNodes();
    createViewTopLevelStmts(view, targetStatements);
    view.nodes.forEach(function (node) {
        if (node instanceof compile_element_1.CompileElement && node.hasEmbeddedView) {
            finishView(node.embeddedView, targetStatements);
        }
    });
}
exports.finishView = finishView;
var ViewBuilderVisitor = (function () {
    function ViewBuilderVisitor(view, targetDependencies) {
        this.view = view;
        this.targetDependencies = targetDependencies;
        this.nestedViewCount = 0;
        this._animationCompiler = new animation_compiler_1.AnimationCompiler();
    }
    ViewBuilderVisitor.prototype._isRootNode = function (parent) { return parent.view !== this.view; };
    ViewBuilderVisitor.prototype._addRootNodeAndProject = function (node) {
        var projectedNode = _getOuterContainerOrSelf(node);
        var parent = projectedNode.parent;
        var ngContentIndex = projectedNode.sourceAst.ngContentIndex;
        var vcAppEl = (node instanceof compile_element_1.CompileElement && node.hasViewContainer) ? node.appElement : null;
        if (this._isRootNode(parent)) {
            // store appElement as root node only for ViewContainers
            if (this.view.viewType !== core_private_1.ViewType.COMPONENT) {
                this.view.rootNodesOrAppElements.push(lang_1.isPresent(vcAppEl) ? vcAppEl : node.renderNode);
            }
        }
        else if (lang_1.isPresent(parent.component) && lang_1.isPresent(ngContentIndex)) {
            parent.addContentNode(ngContentIndex, lang_1.isPresent(vcAppEl) ? vcAppEl : node.renderNode);
        }
    };
    ViewBuilderVisitor.prototype._getParentRenderNode = function (parent) {
        parent = _getOuterContainerParentOrSelf(parent);
        if (this._isRootNode(parent)) {
            if (this.view.viewType === core_private_1.ViewType.COMPONENT) {
                return parentRenderNodeVar;
            }
            else {
                // root node of an embedded/host view
                return o.NULL_EXPR;
            }
        }
        else {
            return lang_1.isPresent(parent.component) &&
                parent.component.template.encapsulation !== core_1.ViewEncapsulation.Native ?
                o.NULL_EXPR :
                parent.renderNode;
        }
    };
    ViewBuilderVisitor.prototype.visitBoundText = function (ast, parent) {
        return this._visitText(ast, '', parent);
    };
    ViewBuilderVisitor.prototype.visitText = function (ast, parent) {
        return this._visitText(ast, ast.value, parent);
    };
    ViewBuilderVisitor.prototype._visitText = function (ast, value, parent) {
        var fieldName = "_text_" + this.view.nodes.length;
        this.view.fields.push(new o.ClassField(fieldName, o.importType(this.view.genConfig.renderTypes.renderText)));
        var renderNode = o.THIS_EXPR.prop(fieldName);
        var compileNode = new compile_element_1.CompileNode(parent, this.view, this.view.nodes.length, renderNode, ast);
        var createRenderNode = o.THIS_EXPR.prop(fieldName)
            .set(constants_1.ViewProperties.renderer.callMethod('createText', [
            this._getParentRenderNode(parent), o.literal(value),
            this.view.createMethod.resetDebugInfoExpr(this.view.nodes.length, ast)
        ]))
            .toStmt();
        this.view.nodes.push(compileNode);
        this.view.createMethod.addStmt(createRenderNode);
        this._addRootNodeAndProject(compileNode);
        return renderNode;
    };
    ViewBuilderVisitor.prototype.visitNgContent = function (ast, parent) {
        // the projected nodes originate from a different view, so we don't
        // have debug information for them...
        this.view.createMethod.resetDebugInfo(null, ast);
        var parentRenderNode = this._getParentRenderNode(parent);
        var nodesExpression = constants_1.ViewProperties.projectableNodes.key(o.literal(ast.index), new o.ArrayType(o.importType(this.view.genConfig.renderTypes.renderNode)));
        if (parentRenderNode !== o.NULL_EXPR) {
            this.view.createMethod.addStmt(constants_1.ViewProperties.renderer
                .callMethod('projectNodes', [
                parentRenderNode,
                o.importExpr(identifiers_1.Identifiers.flattenNestedViewRenderNodes).callFn([nodesExpression])
            ])
                .toStmt());
        }
        else if (this._isRootNode(parent)) {
            if (this.view.viewType !== core_private_1.ViewType.COMPONENT) {
                // store root nodes only for embedded/host views
                this.view.rootNodesOrAppElements.push(nodesExpression);
            }
        }
        else {
            if (lang_1.isPresent(parent.component) && lang_1.isPresent(ast.ngContentIndex)) {
                parent.addContentNode(ast.ngContentIndex, nodesExpression);
            }
        }
        return null;
    };
    ViewBuilderVisitor.prototype.visitElement = function (ast, parent) {
        var _this = this;
        var nodeIndex = this.view.nodes.length;
        var createRenderNodeExpr;
        var debugContextExpr = this.view.createMethod.resetDebugInfoExpr(nodeIndex, ast);
        if (nodeIndex === 0 && this.view.viewType === core_private_1.ViewType.HOST) {
            createRenderNodeExpr = o.THIS_EXPR.callMethod('selectOrCreateHostElement', [o.literal(ast.name), rootSelectorVar, debugContextExpr]);
        }
        else {
            if (ast.name === NG_CONTAINER_TAG) {
                createRenderNodeExpr = constants_1.ViewProperties.renderer.callMethod('createTemplateAnchor', [this._getParentRenderNode(parent), debugContextExpr]);
            }
            else {
                createRenderNodeExpr = constants_1.ViewProperties.renderer.callMethod('createElement', [this._getParentRenderNode(parent), o.literal(ast.name), debugContextExpr]);
            }
        }
        var fieldName = "_el_" + nodeIndex;
        this.view.fields.push(new o.ClassField(fieldName, o.importType(this.view.genConfig.renderTypes.renderElement)));
        this.view.createMethod.addStmt(o.THIS_EXPR.prop(fieldName).set(createRenderNodeExpr).toStmt());
        var renderNode = o.THIS_EXPR.prop(fieldName);
        var directives = ast.directives.map(function (directiveAst) { return directiveAst.directive; });
        var component = directives.find(function (directive) { return directive.isComponent; });
        var htmlAttrs = _readHtmlAttrs(ast.attrs);
        var attrNameAndValues = _mergeHtmlAndDirectiveAttrs(htmlAttrs, directives);
        for (var i = 0; i < attrNameAndValues.length; i++) {
            var attrName = attrNameAndValues[i][0];
            var attrValue = attrNameAndValues[i][1];
            this.view.createMethod.addStmt(constants_1.ViewProperties.renderer
                .callMethod('setElementAttribute', [renderNode, o.literal(attrName), o.literal(attrValue)])
                .toStmt());
        }
        var compileElement = new compile_element_1.CompileElement(parent, this.view, nodeIndex, renderNode, ast, component, directives, ast.providers, ast.hasViewContainer, false, ast.references);
        this.view.nodes.push(compileElement);
        var compViewExpr = null;
        if (lang_1.isPresent(component)) {
            var nestedComponentIdentifier = new compile_metadata_1.CompileIdentifierMetadata({ name: util_1.getViewFactoryName(component, 0) });
            this.targetDependencies.push(new ViewFactoryDependency(component.type, nestedComponentIdentifier));
            var precompileComponentIdentifiers = component.precompile.map(function (precompileComp) {
                var id = new compile_metadata_1.CompileIdentifierMetadata({ name: precompileComp.name });
                _this.targetDependencies.push(new ComponentFactoryDependency(precompileComp, id));
                return id;
            });
            compileElement.createComponentFactoryResolver(precompileComponentIdentifiers);
            compViewExpr = o.variable("compView_" + nodeIndex); // fix highlighting: `
            compileElement.setComponentView(compViewExpr);
            this.view.createMethod.addStmt(compViewExpr
                .set(o.importExpr(nestedComponentIdentifier).callFn([
                constants_1.ViewProperties.viewUtils, compileElement.injector, compileElement.appElement
            ]))
                .toDeclStmt());
        }
        compileElement.beforeChildren();
        this._addRootNodeAndProject(compileElement);
        template_ast_1.templateVisitAll(this, ast.children, compileElement);
        compileElement.afterChildren(this.view.nodes.length - nodeIndex - 1);
        if (lang_1.isPresent(compViewExpr)) {
            var codeGenContentNodes;
            if (this.view.component.type.isHost) {
                codeGenContentNodes = constants_1.ViewProperties.projectableNodes;
            }
            else {
                codeGenContentNodes = o.literalArr(compileElement.contentNodesByNgContentIndex.map(function (nodes) { return util_1.createFlatArray(nodes); }));
            }
            this.view.createMethod.addStmt(compViewExpr
                .callMethod('create', [compileElement.getComponent(), codeGenContentNodes, o.NULL_EXPR])
                .toStmt());
        }
        return null;
    };
    ViewBuilderVisitor.prototype.visitEmbeddedTemplate = function (ast, parent) {
        var nodeIndex = this.view.nodes.length;
        var fieldName = "_anchor_" + nodeIndex;
        this.view.fields.push(new o.ClassField(fieldName, o.importType(this.view.genConfig.renderTypes.renderComment)));
        this.view.createMethod.addStmt(o.THIS_EXPR.prop(fieldName)
            .set(constants_1.ViewProperties.renderer.callMethod('createTemplateAnchor', [
            this._getParentRenderNode(parent),
            this.view.createMethod.resetDebugInfoExpr(nodeIndex, ast)
        ]))
            .toStmt());
        var renderNode = o.THIS_EXPR.prop(fieldName);
        var templateVariableBindings = ast.variables.map(function (varAst) { return [varAst.value.length > 0 ? varAst.value : IMPLICIT_TEMPLATE_VAR, varAst.name]; });
        var directives = ast.directives.map(function (directiveAst) { return directiveAst.directive; });
        var compileElement = new compile_element_1.CompileElement(parent, this.view, nodeIndex, renderNode, ast, null, directives, ast.providers, ast.hasViewContainer, true, ast.references);
        this.view.nodes.push(compileElement);
        var compiledAnimations = this._animationCompiler.compileComponent(this.view.component);
        this.nestedViewCount++;
        var embeddedView = new compile_view_1.CompileView(this.view.component, this.view.genConfig, this.view.pipeMetas, o.NULL_EXPR, compiledAnimations, this.view.viewIndex + this.nestedViewCount, compileElement, templateVariableBindings);
        this.nestedViewCount += buildView(embeddedView, ast.children, this.targetDependencies);
        compileElement.beforeChildren();
        this._addRootNodeAndProject(compileElement);
        compileElement.afterChildren(0);
        return null;
    };
    ViewBuilderVisitor.prototype.visitAttr = function (ast, ctx) { return null; };
    ViewBuilderVisitor.prototype.visitDirective = function (ast, ctx) { return null; };
    ViewBuilderVisitor.prototype.visitEvent = function (ast, eventTargetAndNames) {
        return null;
    };
    ViewBuilderVisitor.prototype.visitReference = function (ast, ctx) { return null; };
    ViewBuilderVisitor.prototype.visitVariable = function (ast, ctx) { return null; };
    ViewBuilderVisitor.prototype.visitDirectiveProperty = function (ast, context) { return null; };
    ViewBuilderVisitor.prototype.visitElementProperty = function (ast, context) { return null; };
    return ViewBuilderVisitor;
}());
/**
 * Walks up the nodes while the direct parent is a container.
 *
 * Returns the outer container or the node itself when it is not a direct child of a container.
 *
 * @internal
 */
function _getOuterContainerOrSelf(node) {
    var view = node.view;
    while (_isNgContainer(node.parent, view)) {
        node = node.parent;
    }
    return node;
}
/**
 * Walks up the nodes while they are container and returns the first parent which is not.
 *
 * Returns the parent of the outer container or the node itself when it is not a container.
 *
 * @internal
 */
function _getOuterContainerParentOrSelf(el) {
    var view = el.view;
    while (_isNgContainer(el, view)) {
        el = el.parent;
    }
    return el;
}
function _isNgContainer(node, view) {
    return !node.isNull() && node.sourceAst.name === NG_CONTAINER_TAG &&
        node.view === view;
}
function _mergeHtmlAndDirectiveAttrs(declaredHtmlAttrs, directives) {
    var result = {};
    collection_1.StringMapWrapper.forEach(declaredHtmlAttrs, function (value, key) { result[key] = value; });
    directives.forEach(function (directiveMeta) {
        collection_1.StringMapWrapper.forEach(directiveMeta.hostAttributes, function (value, name) {
            var prevValue = result[name];
            result[name] = lang_1.isPresent(prevValue) ? mergeAttributeValue(name, prevValue, value) : value;
        });
    });
    return mapToKeyValueArray(result);
}
function _readHtmlAttrs(attrs) {
    var htmlAttrs = {};
    attrs.forEach(function (ast) { htmlAttrs[ast.name] = ast.value; });
    return htmlAttrs;
}
function mergeAttributeValue(attrName, attrValue1, attrValue2) {
    if (attrName == CLASS_ATTR || attrName == STYLE_ATTR) {
        return attrValue1 + " " + attrValue2;
    }
    else {
        return attrValue2;
    }
}
function mapToKeyValueArray(data) {
    var entryArray = [];
    collection_1.StringMapWrapper.forEach(data, function (value, name) {
        entryArray.push([name, value]);
    });
    // We need to sort to get a defined output order
    // for tests and for caching generated artifacts...
    collection_1.ListWrapper.sort(entryArray, function (entry1, entry2) { return lang_1.StringWrapper.compare(entry1[0], entry2[0]); });
    return entryArray;
}
function createViewTopLevelStmts(view, targetStatements) {
    var nodeDebugInfosVar = o.NULL_EXPR;
    if (view.genConfig.genDebugInfo) {
        nodeDebugInfosVar = o.variable("nodeDebugInfos_" + view.component.type.name + view.viewIndex); // fix highlighting: `
        targetStatements.push(nodeDebugInfosVar
            .set(o.literalArr(view.nodes.map(createStaticNodeDebugInfo), new o.ArrayType(new o.ExternalType(identifiers_1.Identifiers.StaticNodeDebugInfo), [o.TypeModifier.Const])))
            .toDeclStmt(null, [o.StmtModifier.Final]));
    }
    var renderCompTypeVar = o.variable("renderType_" + view.component.type.name); // fix highlighting: `
    if (view.viewIndex === 0) {
        targetStatements.push(renderCompTypeVar.set(o.NULL_EXPR)
            .toDeclStmt(o.importType(identifiers_1.Identifiers.RenderComponentType)));
    }
    var viewClass = createViewClass(view, renderCompTypeVar, nodeDebugInfosVar);
    targetStatements.push(viewClass);
    targetStatements.push(createViewFactory(view, viewClass, renderCompTypeVar));
}
function createStaticNodeDebugInfo(node) {
    var compileElement = node instanceof compile_element_1.CompileElement ? node : null;
    var providerTokens = [];
    var componentToken = o.NULL_EXPR;
    var varTokenEntries = [];
    if (lang_1.isPresent(compileElement)) {
        providerTokens = compileElement.getProviderTokens();
        if (lang_1.isPresent(compileElement.component)) {
            componentToken = util_1.createDiTokenExpression(identifiers_1.identifierToken(compileElement.component.type));
        }
        collection_1.StringMapWrapper.forEach(compileElement.referenceTokens, function (token, varName) {
            varTokenEntries.push([varName, lang_1.isPresent(token) ? util_1.createDiTokenExpression(token) : o.NULL_EXPR]);
        });
    }
    return o.importExpr(identifiers_1.Identifiers.StaticNodeDebugInfo)
        .instantiate([
        o.literalArr(providerTokens, new o.ArrayType(o.DYNAMIC_TYPE, [o.TypeModifier.Const])),
        componentToken,
        o.literalMap(varTokenEntries, new o.MapType(o.DYNAMIC_TYPE, [o.TypeModifier.Const]))
    ], o.importType(identifiers_1.Identifiers.StaticNodeDebugInfo, null, [o.TypeModifier.Const]));
}
function createViewClass(view, renderCompTypeVar, nodeDebugInfosVar) {
    var viewConstructorArgs = [
        new o.FnParam(constants_1.ViewConstructorVars.viewUtils.name, o.importType(identifiers_1.Identifiers.ViewUtils)),
        new o.FnParam(constants_1.ViewConstructorVars.parentInjector.name, o.importType(identifiers_1.Identifiers.Injector)),
        new o.FnParam(constants_1.ViewConstructorVars.declarationEl.name, o.importType(identifiers_1.Identifiers.AppElement))
    ];
    var superConstructorArgs = [
        o.variable(view.className), renderCompTypeVar, constants_1.ViewTypeEnum.fromValue(view.viewType),
        constants_1.ViewConstructorVars.viewUtils, constants_1.ViewConstructorVars.parentInjector,
        constants_1.ViewConstructorVars.declarationEl,
        constants_1.ChangeDetectorStatusEnum.fromValue(getChangeDetectionMode(view))
    ];
    if (view.genConfig.genDebugInfo) {
        superConstructorArgs.push(nodeDebugInfosVar);
    }
    var viewConstructor = new o.ClassMethod(null, viewConstructorArgs, [o.SUPER_EXPR.callFn(superConstructorArgs).toStmt()]);
    var viewMethods = [
        new o.ClassMethod('createInternal', [new o.FnParam(rootSelectorVar.name, o.STRING_TYPE)], generateCreateMethod(view), o.importType(identifiers_1.Identifiers.AppElement)),
        new o.ClassMethod('injectorGetInternal', [
            new o.FnParam(constants_1.InjectMethodVars.token.name, o.DYNAMIC_TYPE),
            // Note: Can't use o.INT_TYPE here as the method in AppView uses number
            new o.FnParam(constants_1.InjectMethodVars.requestNodeIndex.name, o.NUMBER_TYPE),
            new o.FnParam(constants_1.InjectMethodVars.notFoundResult.name, o.DYNAMIC_TYPE)
        ], addReturnValuefNotEmpty(view.injectorGetMethod.finish(), constants_1.InjectMethodVars.notFoundResult), o.DYNAMIC_TYPE),
        new o.ClassMethod('detectChangesInternal', [new o.FnParam(constants_1.DetectChangesVars.throwOnChange.name, o.BOOL_TYPE)], generateDetectChangesMethod(view)),
        new o.ClassMethod('dirtyParentQueriesInternal', [], view.dirtyParentQueriesMethod.finish()),
        new o.ClassMethod('destroyInternal', [], view.destroyMethod.finish()),
        new o.ClassMethod('detachInternal', [], view.detachMethod.finish())
    ].concat(view.eventHandlerMethods);
    var superClass = view.genConfig.genDebugInfo ? identifiers_1.Identifiers.DebugAppView : identifiers_1.Identifiers.AppView;
    var viewClass = new o.ClassStmt(view.className, o.importExpr(superClass, [getContextType(view)]), view.fields, view.getters, viewConstructor, viewMethods.filter(function (method) { return method.body.length > 0; }));
    return viewClass;
}
function createViewFactory(view, viewClass, renderCompTypeVar) {
    var viewFactoryArgs = [
        new o.FnParam(constants_1.ViewConstructorVars.viewUtils.name, o.importType(identifiers_1.Identifiers.ViewUtils)),
        new o.FnParam(constants_1.ViewConstructorVars.parentInjector.name, o.importType(identifiers_1.Identifiers.Injector)),
        new o.FnParam(constants_1.ViewConstructorVars.declarationEl.name, o.importType(identifiers_1.Identifiers.AppElement))
    ];
    var initRenderCompTypeStmts = [];
    var templateUrlInfo;
    if (view.component.template.templateUrl == view.component.type.moduleUrl) {
        templateUrlInfo =
            view.component.type.moduleUrl + " class " + view.component.type.name + " - inline template";
    }
    else {
        templateUrlInfo = view.component.template.templateUrl;
    }
    if (view.viewIndex === 0) {
        initRenderCompTypeStmts = [new o.IfStmt(renderCompTypeVar.identical(o.NULL_EXPR), [
                renderCompTypeVar
                    .set(constants_1.ViewConstructorVars.viewUtils.callMethod('createRenderComponentType', [
                    o.literal(templateUrlInfo),
                    o.literal(view.component.template.ngContentSelectors.length),
                    constants_1.ViewEncapsulationEnum.fromValue(view.component.template.encapsulation), view.styles
                ]))
                    .toStmt()
            ])];
    }
    return o
        .fn(viewFactoryArgs, initRenderCompTypeStmts.concat([new o.ReturnStatement(o.variable(viewClass.name)
            .instantiate(viewClass.constructorMethod.params.map(function (param) { return o.variable(param.name); })))]), o.importType(identifiers_1.Identifiers.AppView, [getContextType(view)]))
        .toDeclStmt(view.viewFactory.name, [o.StmtModifier.Final]);
}
function generateCreateMethod(view) {
    var parentRenderNodeExpr = o.NULL_EXPR;
    var parentRenderNodeStmts = [];
    if (view.viewType === core_private_1.ViewType.COMPONENT) {
        parentRenderNodeExpr = constants_1.ViewProperties.renderer.callMethod('createViewRoot', [o.THIS_EXPR.prop('declarationAppElement').prop('nativeElement')]);
        parentRenderNodeStmts =
            [parentRenderNodeVar.set(parentRenderNodeExpr)
                    .toDeclStmt(o.importType(view.genConfig.renderTypes.renderNode), [o.StmtModifier.Final])];
    }
    var resultExpr;
    if (view.viewType === core_private_1.ViewType.HOST) {
        resultExpr = view.nodes[0].appElement;
    }
    else {
        resultExpr = o.NULL_EXPR;
    }
    return parentRenderNodeStmts.concat(view.createMethod.finish(), [
        o.THIS_EXPR
            .callMethod('init', [
            util_1.createFlatArray(view.rootNodesOrAppElements),
            o.literalArr(view.nodes.map(function (node) { return node.renderNode; })), o.literalArr(view.disposables),
            o.literalArr(view.subscriptions)
        ])
            .toStmt(),
        new o.ReturnStatement(resultExpr)
    ]);
}
function generateDetectChangesMethod(view) {
    var stmts = [];
    if (view.detectChangesInInputsMethod.isEmpty() && view.updateContentQueriesMethod.isEmpty() &&
        view.afterContentLifecycleCallbacksMethod.isEmpty() &&
        view.detectChangesRenderPropertiesMethod.isEmpty() &&
        view.updateViewQueriesMethod.isEmpty() && view.afterViewLifecycleCallbacksMethod.isEmpty()) {
        return stmts;
    }
    collection_1.ListWrapper.addAll(stmts, view.detectChangesInInputsMethod.finish());
    stmts.push(o.THIS_EXPR.callMethod('detectContentChildrenChanges', [constants_1.DetectChangesVars.throwOnChange])
        .toStmt());
    var afterContentStmts = view.updateContentQueriesMethod.finish().concat(view.afterContentLifecycleCallbacksMethod.finish());
    if (afterContentStmts.length > 0) {
        stmts.push(new o.IfStmt(o.not(constants_1.DetectChangesVars.throwOnChange), afterContentStmts));
    }
    collection_1.ListWrapper.addAll(stmts, view.detectChangesRenderPropertiesMethod.finish());
    stmts.push(o.THIS_EXPR.callMethod('detectViewChildrenChanges', [constants_1.DetectChangesVars.throwOnChange])
        .toStmt());
    var afterViewStmts = view.updateViewQueriesMethod.finish().concat(view.afterViewLifecycleCallbacksMethod.finish());
    if (afterViewStmts.length > 0) {
        stmts.push(new o.IfStmt(o.not(constants_1.DetectChangesVars.throwOnChange), afterViewStmts));
    }
    var varStmts = [];
    var readVars = o.findReadVarNames(stmts);
    if (collection_1.SetWrapper.has(readVars, constants_1.DetectChangesVars.changed.name)) {
        varStmts.push(constants_1.DetectChangesVars.changed.set(o.literal(true)).toDeclStmt(o.BOOL_TYPE));
    }
    if (collection_1.SetWrapper.has(readVars, constants_1.DetectChangesVars.changes.name)) {
        varStmts.push(constants_1.DetectChangesVars.changes.set(o.NULL_EXPR)
            .toDeclStmt(new o.MapType(o.importType(identifiers_1.Identifiers.SimpleChange))));
    }
    if (collection_1.SetWrapper.has(readVars, constants_1.DetectChangesVars.valUnwrapper.name)) {
        varStmts.push(constants_1.DetectChangesVars.valUnwrapper.set(o.importExpr(identifiers_1.Identifiers.ValueUnwrapper).instantiate([]))
            .toDeclStmt(null, [o.StmtModifier.Final]));
    }
    return varStmts.concat(stmts);
}
function addReturnValuefNotEmpty(statements, value) {
    if (statements.length > 0) {
        return statements.concat([new o.ReturnStatement(value)]);
    }
    else {
        return statements;
    }
}
function getContextType(view) {
    if (view.viewType === core_private_1.ViewType.COMPONENT) {
        return o.importType(view.component.type);
    }
    return o.DYNAMIC_TYPE;
}
function getChangeDetectionMode(view) {
    var mode;
    if (view.viewType === core_private_1.ViewType.COMPONENT) {
        mode = core_private_1.isDefaultChangeDetectionStrategy(view.component.changeDetection) ?
            core_private_1.ChangeDetectorStatus.CheckAlways :
            core_private_1.ChangeDetectorStatus.CheckOnce;
    }
    else {
        mode = core_private_1.ChangeDetectorStatus.CheckAlways;
    }
    return mode;
}
//# sourceMappingURL=view_builder.js.map