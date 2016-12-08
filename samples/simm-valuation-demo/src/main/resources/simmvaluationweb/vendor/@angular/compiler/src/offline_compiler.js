/**
 * @license
 * Copyright Google Inc. All Rights Reserved.
 *
 * Use of this source code is governed by an MIT-style license that can be
 * found in the LICENSE file at https://angular.io/license
 */
"use strict";
var core_1 = require('@angular/core');
var compile_metadata_1 = require('./compile_metadata');
var collection_1 = require('./facade/collection');
var exceptions_1 = require('./facade/exceptions');
var o = require('./output/output_ast');
var util_1 = require('./util');
var view_compiler_1 = require('./view_compiler/view_compiler');
var _COMPONENT_FACTORY_IDENTIFIER = new compile_metadata_1.CompileIdentifierMetadata({
    name: 'ComponentFactory',
    runtime: core_1.ComponentFactory,
    moduleUrl: util_1.assetUrl('core', 'linker/component_factory')
});
var SourceModule = (function () {
    function SourceModule(moduleUrl, source) {
        this.moduleUrl = moduleUrl;
        this.source = source;
    }
    return SourceModule;
}());
exports.SourceModule = SourceModule;
var StyleSheetSourceWithImports = (function () {
    function StyleSheetSourceWithImports(source, importedUrls) {
        this.source = source;
        this.importedUrls = importedUrls;
    }
    return StyleSheetSourceWithImports;
}());
exports.StyleSheetSourceWithImports = StyleSheetSourceWithImports;
var NormalizedComponentWithViewDirectives = (function () {
    function NormalizedComponentWithViewDirectives(component, directives, pipes) {
        this.component = component;
        this.directives = directives;
        this.pipes = pipes;
    }
    return NormalizedComponentWithViewDirectives;
}());
exports.NormalizedComponentWithViewDirectives = NormalizedComponentWithViewDirectives;
var OfflineCompiler = (function () {
    function OfflineCompiler(_directiveNormalizer, _templateParser, _styleCompiler, _viewCompiler, _outputEmitter) {
        this._directiveNormalizer = _directiveNormalizer;
        this._templateParser = _templateParser;
        this._styleCompiler = _styleCompiler;
        this._viewCompiler = _viewCompiler;
        this._outputEmitter = _outputEmitter;
    }
    OfflineCompiler.prototype.normalizeDirectiveMetadata = function (directive) {
        return this._directiveNormalizer.normalizeDirective(directive).asyncResult;
    };
    OfflineCompiler.prototype.compileTemplates = function (components) {
        var _this = this;
        if (components.length === 0) {
            throw new exceptions_1.BaseException('No components given');
        }
        var statements = [];
        var exportedVars = [];
        var moduleUrl = _ngfactoryModuleUrl(components[0].component.type);
        var outputSourceModules = [];
        components.forEach(function (componentWithDirs) {
            var compMeta = componentWithDirs.component;
            _assertComponent(compMeta);
            var fileSuffix = _splitLastSuffix(compMeta.type.moduleUrl)[1];
            var stylesCompileResults = _this._styleCompiler.compileComponent(compMeta);
            stylesCompileResults.externalStylesheets.forEach(function (compiledStyleSheet) {
                outputSourceModules.push(_this._codgenStyles(compiledStyleSheet, fileSuffix));
            });
            var compViewFactoryVar = _this._compileComponent(compMeta, componentWithDirs.directives, componentWithDirs.pipes, stylesCompileResults.componentStylesheet, fileSuffix, statements);
            exportedVars.push(compViewFactoryVar);
            var hostMeta = compile_metadata_1.createHostComponentMeta(compMeta.type, compMeta.selector);
            var hostViewFactoryVar = _this._compileComponent(hostMeta, [compMeta], [], null, fileSuffix, statements);
            var compFactoryVar = _componentFactoryName(compMeta.type);
            statements.push(o.variable(compFactoryVar)
                .set(o.importExpr(_COMPONENT_FACTORY_IDENTIFIER, [o.importType(compMeta.type)])
                .instantiate([
                o.literal(compMeta.selector), o.variable(hostViewFactoryVar),
                o.importExpr(compMeta.type)
            ], o.importType(_COMPONENT_FACTORY_IDENTIFIER, [o.importType(compMeta.type)], [o.TypeModifier.Const])))
                .toDeclStmt(null, [o.StmtModifier.Final]));
            exportedVars.push(compFactoryVar);
        });
        outputSourceModules.unshift(this._codegenSourceModule(moduleUrl, statements, exportedVars));
        return outputSourceModules;
    };
    OfflineCompiler.prototype._compileComponent = function (compMeta, directives, pipes, componentStyles, fileSuffix, targetStatements) {
        var parsedTemplate = this._templateParser.parse(compMeta, compMeta.template.template, directives, pipes, compMeta.type.name);
        var stylesExpr = componentStyles ? o.variable(componentStyles.stylesVar) : o.literalArr([]);
        var viewResult = this._viewCompiler.compileComponent(compMeta, parsedTemplate, stylesExpr, pipes);
        if (componentStyles) {
            collection_1.ListWrapper.addAll(targetStatements, _resolveStyleStatements(componentStyles, fileSuffix));
        }
        collection_1.ListWrapper.addAll(targetStatements, _resolveViewStatements(viewResult));
        return viewResult.viewFactoryVar;
    };
    OfflineCompiler.prototype._codgenStyles = function (stylesCompileResult, fileSuffix) {
        _resolveStyleStatements(stylesCompileResult, fileSuffix);
        return this._codegenSourceModule(_stylesModuleUrl(stylesCompileResult.meta.moduleUrl, stylesCompileResult.isShimmed, fileSuffix), stylesCompileResult.statements, [stylesCompileResult.stylesVar]);
    };
    OfflineCompiler.prototype._codegenSourceModule = function (moduleUrl, statements, exportedVars) {
        return new SourceModule(moduleUrl, this._outputEmitter.emitStatements(moduleUrl, statements, exportedVars));
    };
    return OfflineCompiler;
}());
exports.OfflineCompiler = OfflineCompiler;
function _resolveViewStatements(compileResult) {
    compileResult.dependencies.forEach(function (dep) {
        if (dep instanceof view_compiler_1.ViewFactoryDependency) {
            var vfd = dep;
            vfd.placeholder.moduleUrl = _ngfactoryModuleUrl(vfd.comp);
        }
        else if (dep instanceof view_compiler_1.ComponentFactoryDependency) {
            var cfd = dep;
            cfd.placeholder.name = _componentFactoryName(cfd.comp);
            cfd.placeholder.moduleUrl = _ngfactoryModuleUrl(cfd.comp);
        }
    });
    return compileResult.statements;
}
function _resolveStyleStatements(compileResult, fileSuffix) {
    compileResult.dependencies.forEach(function (dep) {
        dep.valuePlaceholder.moduleUrl = _stylesModuleUrl(dep.moduleUrl, dep.isShimmed, fileSuffix);
    });
    return compileResult.statements;
}
function _ngfactoryModuleUrl(comp) {
    var urlWithSuffix = _splitLastSuffix(comp.moduleUrl);
    return urlWithSuffix[0] + ".ngfactory" + urlWithSuffix[1];
}
function _componentFactoryName(comp) {
    return comp.name + "NgFactory";
}
function _stylesModuleUrl(stylesheetUrl, shim, suffix) {
    return shim ? stylesheetUrl + ".shim" + suffix : "" + stylesheetUrl + suffix;
}
function _assertComponent(meta) {
    if (!meta.isComponent) {
        throw new exceptions_1.BaseException("Could not compile '" + meta.type.name + "' because it is not a component.");
    }
}
function _splitLastSuffix(path) {
    var lastDot = path.lastIndexOf('.');
    if (lastDot !== -1) {
        return [path.substring(0, lastDot), path.substring(lastDot)];
    }
    else {
        return [path, ''];
    }
}
//# sourceMappingURL=offline_compiler.js.map