/**
 * @license
 * Copyright Google Inc. All Rights Reserved.
 *
 * Use of this source code is governed by an MIT-style license that can be
 * found in the LICENSE file at https://angular.io/license
 */
import { ComponentFactory } from '@angular/core';
import { CompileIdentifierMetadata, createHostComponentMeta } from './compile_metadata';
import { ListWrapper } from './facade/collection';
import { BaseException } from './facade/exceptions';
import * as o from './output/output_ast';
import { assetUrl } from './util';
import { ComponentFactoryDependency, ViewFactoryDependency } from './view_compiler/view_compiler';
var _COMPONENT_FACTORY_IDENTIFIER = new CompileIdentifierMetadata({
    name: 'ComponentFactory',
    runtime: ComponentFactory,
    moduleUrl: assetUrl('core', 'linker/component_factory')
});
export class SourceModule {
    constructor(moduleUrl, source) {
        this.moduleUrl = moduleUrl;
        this.source = source;
    }
}
export class StyleSheetSourceWithImports {
    constructor(source, importedUrls) {
        this.source = source;
        this.importedUrls = importedUrls;
    }
}
export class NormalizedComponentWithViewDirectives {
    constructor(component, directives, pipes) {
        this.component = component;
        this.directives = directives;
        this.pipes = pipes;
    }
}
export class OfflineCompiler {
    constructor(_directiveNormalizer, _templateParser, _styleCompiler, _viewCompiler, _outputEmitter) {
        this._directiveNormalizer = _directiveNormalizer;
        this._templateParser = _templateParser;
        this._styleCompiler = _styleCompiler;
        this._viewCompiler = _viewCompiler;
        this._outputEmitter = _outputEmitter;
    }
    normalizeDirectiveMetadata(directive) {
        return this._directiveNormalizer.normalizeDirective(directive).asyncResult;
    }
    compileTemplates(components) {
        if (components.length === 0) {
            throw new BaseException('No components given');
        }
        var statements = [];
        var exportedVars = [];
        var moduleUrl = _ngfactoryModuleUrl(components[0].component.type);
        var outputSourceModules = [];
        components.forEach(componentWithDirs => {
            var compMeta = componentWithDirs.component;
            _assertComponent(compMeta);
            var fileSuffix = _splitLastSuffix(compMeta.type.moduleUrl)[1];
            var stylesCompileResults = this._styleCompiler.compileComponent(compMeta);
            stylesCompileResults.externalStylesheets.forEach((compiledStyleSheet) => {
                outputSourceModules.push(this._codgenStyles(compiledStyleSheet, fileSuffix));
            });
            var compViewFactoryVar = this._compileComponent(compMeta, componentWithDirs.directives, componentWithDirs.pipes, stylesCompileResults.componentStylesheet, fileSuffix, statements);
            exportedVars.push(compViewFactoryVar);
            var hostMeta = createHostComponentMeta(compMeta.type, compMeta.selector);
            var hostViewFactoryVar = this._compileComponent(hostMeta, [compMeta], [], null, fileSuffix, statements);
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
    }
    _compileComponent(compMeta, directives, pipes, componentStyles, fileSuffix, targetStatements) {
        var parsedTemplate = this._templateParser.parse(compMeta, compMeta.template.template, directives, pipes, compMeta.type.name);
        var stylesExpr = componentStyles ? o.variable(componentStyles.stylesVar) : o.literalArr([]);
        var viewResult = this._viewCompiler.compileComponent(compMeta, parsedTemplate, stylesExpr, pipes);
        if (componentStyles) {
            ListWrapper.addAll(targetStatements, _resolveStyleStatements(componentStyles, fileSuffix));
        }
        ListWrapper.addAll(targetStatements, _resolveViewStatements(viewResult));
        return viewResult.viewFactoryVar;
    }
    _codgenStyles(stylesCompileResult, fileSuffix) {
        _resolveStyleStatements(stylesCompileResult, fileSuffix);
        return this._codegenSourceModule(_stylesModuleUrl(stylesCompileResult.meta.moduleUrl, stylesCompileResult.isShimmed, fileSuffix), stylesCompileResult.statements, [stylesCompileResult.stylesVar]);
    }
    _codegenSourceModule(moduleUrl, statements, exportedVars) {
        return new SourceModule(moduleUrl, this._outputEmitter.emitStatements(moduleUrl, statements, exportedVars));
    }
}
function _resolveViewStatements(compileResult) {
    compileResult.dependencies.forEach((dep) => {
        if (dep instanceof ViewFactoryDependency) {
            let vfd = dep;
            vfd.placeholder.moduleUrl = _ngfactoryModuleUrl(vfd.comp);
        }
        else if (dep instanceof ComponentFactoryDependency) {
            let cfd = dep;
            cfd.placeholder.name = _componentFactoryName(cfd.comp);
            cfd.placeholder.moduleUrl = _ngfactoryModuleUrl(cfd.comp);
        }
    });
    return compileResult.statements;
}
function _resolveStyleStatements(compileResult, fileSuffix) {
    compileResult.dependencies.forEach((dep) => {
        dep.valuePlaceholder.moduleUrl = _stylesModuleUrl(dep.moduleUrl, dep.isShimmed, fileSuffix);
    });
    return compileResult.statements;
}
function _ngfactoryModuleUrl(comp) {
    var urlWithSuffix = _splitLastSuffix(comp.moduleUrl);
    return `${urlWithSuffix[0]}.ngfactory${urlWithSuffix[1]}`;
}
function _componentFactoryName(comp) {
    return `${comp.name}NgFactory`;
}
function _stylesModuleUrl(stylesheetUrl, shim, suffix) {
    return shim ? `${stylesheetUrl}.shim${suffix}` : `${stylesheetUrl}${suffix}`;
}
function _assertComponent(meta) {
    if (!meta.isComponent) {
        throw new BaseException(`Could not compile '${meta.type.name}' because it is not a component.`);
    }
}
function _splitLastSuffix(path) {
    let lastDot = path.lastIndexOf('.');
    if (lastDot !== -1) {
        return [path.substring(0, lastDot), path.substring(lastDot)];
    }
    else {
        return [path, ''];
    }
}
//# sourceMappingURL=offline_compiler.js.map