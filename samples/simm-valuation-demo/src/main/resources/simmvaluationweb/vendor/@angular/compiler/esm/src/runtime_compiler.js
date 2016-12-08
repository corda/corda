/**
 * @license
 * Copyright Google Inc. All Rights Reserved.
 *
 * Use of this source code is governed by an MIT-style license that can be
 * found in the LICENSE file at https://angular.io/license
 */
import { ComponentFactory, Injectable } from '@angular/core';
import { BaseException } from '../src/facade/exceptions';
import { IS_DART, isBlank, isString } from '../src/facade/lang';
import { PromiseWrapper } from '../src/facade/async';
import { createHostComponentMeta } from './compile_metadata';
import { StyleCompiler } from './style_compiler';
import { ViewCompiler, ViewFactoryDependency, ComponentFactoryDependency } from './view_compiler/view_compiler';
import { TemplateParser } from './template_parser';
import { DirectiveNormalizer } from './directive_normalizer';
import { CompileMetadataResolver } from './metadata_resolver';
import { CompilerConfig } from './config';
import * as ir from './output/output_ast';
import { jitStatements } from './output/output_jit';
import { interpretStatements } from './output/output_interpreter';
import { InterpretiveAppViewInstanceFactory } from './output/interpretive_view';
export class RuntimeCompiler {
    constructor(_metadataResolver, _templateNormalizer, _templateParser, _styleCompiler, _viewCompiler, _genConfig) {
        this._metadataResolver = _metadataResolver;
        this._templateNormalizer = _templateNormalizer;
        this._templateParser = _templateParser;
        this._styleCompiler = _styleCompiler;
        this._viewCompiler = _viewCompiler;
        this._genConfig = _genConfig;
        this._compiledTemplateCache = new Map();
        this._compiledHostTemplateCache = new Map();
    }
    resolveComponent(component) {
        if (isString(component)) {
            return PromiseWrapper.reject(new BaseException(`Cannot resolve component using '${component}'.`), null);
        }
        return this.compileComponentAsync(component);
    }
    compileComponentAsync(compType) {
        var templates = this._getTransitiveCompiledTemplates(compType, true);
        var loadingPromises = [];
        templates.forEach((template) => {
            if (template.loading) {
                loadingPromises.push(template.loading);
            }
        });
        return Promise.all(loadingPromises).then(() => {
            templates.forEach((template) => { this._compileTemplate(template); });
            return this._getCompiledHostTemplate(compType).proxyComponentFactory;
        });
    }
    compileComponentSync(compType) {
        var templates = this._getTransitiveCompiledTemplates(compType, true);
        templates.forEach((template) => {
            if (template.loading) {
                throw new BaseException(`Can't compile synchronously as ${template.compType.name} is still being loaded!`);
            }
        });
        templates.forEach((template) => { this._compileTemplate(template); });
        return this._getCompiledHostTemplate(compType).proxyComponentFactory;
    }
    clearCacheFor(compType) {
        this._metadataResolver.clearCacheFor(compType);
        this._compiledHostTemplateCache.delete(compType);
        var compiledTemplate = this._compiledTemplateCache.get(compType);
        if (compiledTemplate) {
            this._templateNormalizer.clearCacheFor(compiledTemplate.normalizedCompMeta);
            this._compiledTemplateCache.delete(compType);
        }
    }
    clearCache() {
        this._metadataResolver.clearCache();
        this._compiledTemplateCache.clear();
        this._compiledHostTemplateCache.clear();
        this._templateNormalizer.clearCache();
    }
    _getCompiledHostTemplate(type) {
        var compiledTemplate = this._compiledHostTemplateCache.get(type);
        if (isBlank(compiledTemplate)) {
            var compMeta = this._metadataResolver.getDirectiveMetadata(type);
            assertComponent(compMeta);
            var hostMeta = createHostComponentMeta(compMeta.type, compMeta.selector);
            compiledTemplate = new CompiledTemplate(true, compMeta.selector, compMeta.type, [], [type], [], [], this._templateNormalizer.normalizeDirective(hostMeta));
            this._compiledHostTemplateCache.set(type, compiledTemplate);
        }
        return compiledTemplate;
    }
    _getCompiledTemplate(type) {
        var compiledTemplate = this._compiledTemplateCache.get(type);
        if (isBlank(compiledTemplate)) {
            var compMeta = this._metadataResolver.getDirectiveMetadata(type);
            assertComponent(compMeta);
            var viewDirectives = [];
            var viewComponentTypes = [];
            this._metadataResolver.getViewDirectivesMetadata(type).forEach(dirOrComp => {
                if (dirOrComp.isComponent) {
                    viewComponentTypes.push(dirOrComp.type.runtime);
                }
                else {
                    viewDirectives.push(dirOrComp);
                }
            });
            var precompileComponentTypes = compMeta.precompile.map((typeMeta) => typeMeta.runtime);
            var pipes = this._metadataResolver.getViewPipesMetadata(type);
            compiledTemplate = new CompiledTemplate(false, compMeta.selector, compMeta.type, viewDirectives, viewComponentTypes, precompileComponentTypes, pipes, this._templateNormalizer.normalizeDirective(compMeta));
            this._compiledTemplateCache.set(type, compiledTemplate);
        }
        return compiledTemplate;
    }
    _getTransitiveCompiledTemplates(compType, isHost, target = new Set()) {
        var template = isHost ? this._getCompiledHostTemplate(compType) : this._getCompiledTemplate(compType);
        if (!target.has(template)) {
            target.add(template);
            template.viewComponentTypes.forEach((compType) => { this._getTransitiveCompiledTemplates(compType, false, target); });
            template.precompileHostComponentTypes.forEach((compType) => { this._getTransitiveCompiledTemplates(compType, true, target); });
        }
        return target;
    }
    _compileTemplate(template) {
        if (template.isCompiled) {
            return;
        }
        var compMeta = template.normalizedCompMeta;
        var externalStylesheetsByModuleUrl = new Map();
        var stylesCompileResult = this._styleCompiler.compileComponent(compMeta);
        stylesCompileResult.externalStylesheets.forEach((r) => { externalStylesheetsByModuleUrl.set(r.meta.moduleUrl, r); });
        this._resolveStylesCompileResult(stylesCompileResult.componentStylesheet, externalStylesheetsByModuleUrl);
        var viewCompMetas = template.viewComponentTypes.map((compType) => this._getCompiledTemplate(compType).normalizedCompMeta);
        var parsedTemplate = this._templateParser.parse(compMeta, compMeta.template.template, template.viewDirectives.concat(viewCompMetas), template.viewPipes, compMeta.type.name);
        var compileResult = this._viewCompiler.compileComponent(compMeta, parsedTemplate, ir.variable(stylesCompileResult.componentStylesheet.stylesVar), template.viewPipes);
        var depTemplates = compileResult.dependencies.map((dep) => {
            let depTemplate;
            if (dep instanceof ViewFactoryDependency) {
                let vfd = dep;
                depTemplate = this._getCompiledTemplate(vfd.comp.runtime);
                vfd.placeholder.runtime = depTemplate.proxyViewFactory;
                vfd.placeholder.name = `viewFactory_${vfd.comp.name}`;
            }
            else if (dep instanceof ComponentFactoryDependency) {
                let cfd = dep;
                depTemplate = this._getCompiledHostTemplate(cfd.comp.runtime);
                cfd.placeholder.runtime = depTemplate.proxyComponentFactory;
                cfd.placeholder.name = `compFactory_${cfd.comp.name}`;
            }
            return depTemplate;
        });
        var statements = stylesCompileResult.componentStylesheet.statements.concat(compileResult.statements);
        var factory;
        if (IS_DART || !this._genConfig.useJit) {
            factory = interpretStatements(statements, compileResult.viewFactoryVar, new InterpretiveAppViewInstanceFactory());
        }
        else {
            factory = jitStatements(`${template.compType.name}.template.js`, statements, compileResult.viewFactoryVar);
        }
        template.compiled(factory);
    }
    _resolveStylesCompileResult(result, externalStylesheetsByModuleUrl) {
        result.dependencies.forEach((dep, i) => {
            var nestedCompileResult = externalStylesheetsByModuleUrl.get(dep.moduleUrl);
            var nestedStylesArr = this._resolveAndEvalStylesCompileResult(nestedCompileResult, externalStylesheetsByModuleUrl);
            dep.valuePlaceholder.runtime = nestedStylesArr;
            dep.valuePlaceholder.name = `importedStyles${i}`;
        });
    }
    _resolveAndEvalStylesCompileResult(result, externalStylesheetsByModuleUrl) {
        this._resolveStylesCompileResult(result, externalStylesheetsByModuleUrl);
        if (IS_DART || !this._genConfig.useJit) {
            return interpretStatements(result.statements, result.stylesVar, new InterpretiveAppViewInstanceFactory());
        }
        else {
            return jitStatements(`${result.meta.moduleUrl}.css.js`, result.statements, result.stylesVar);
        }
    }
}
/** @nocollapse */
RuntimeCompiler.decorators = [
    { type: Injectable },
];
/** @nocollapse */
RuntimeCompiler.ctorParameters = [
    { type: CompileMetadataResolver, },
    { type: DirectiveNormalizer, },
    { type: TemplateParser, },
    { type: StyleCompiler, },
    { type: ViewCompiler, },
    { type: CompilerConfig, },
];
class CompiledTemplate {
    constructor(isHost, selector, compType, viewDirectives, viewComponentTypes, precompileHostComponentTypes, viewPipes, _normalizeResult) {
        this.isHost = isHost;
        this.compType = compType;
        this.viewDirectives = viewDirectives;
        this.viewComponentTypes = viewComponentTypes;
        this.precompileHostComponentTypes = precompileHostComponentTypes;
        this.viewPipes = viewPipes;
        this._normalizeResult = _normalizeResult;
        this._viewFactory = null;
        this.loading = null;
        this._normalizedCompMeta = null;
        this.isCompiled = false;
        this.isCompiledWithDeps = false;
        this.proxyViewFactory = (...args) => this._viewFactory.apply(null, args);
        this.proxyComponentFactory = isHost ?
            new ComponentFactory(selector, this.proxyViewFactory, compType.runtime) :
            null;
        if (_normalizeResult.syncResult) {
            this._normalizedCompMeta = _normalizeResult.syncResult;
        }
        else {
            this.loading = _normalizeResult.asyncResult.then((normalizedCompMeta) => {
                this._normalizedCompMeta = normalizedCompMeta;
                this.loading = null;
            });
        }
    }
    get normalizedCompMeta() {
        if (this.loading) {
            throw new BaseException(`Template is still loading for ${this.compType.name}!`);
        }
        return this._normalizedCompMeta;
    }
    compiled(viewFactory) {
        this._viewFactory = viewFactory;
        this.isCompiled = true;
    }
    depsCompiled() { this.isCompiledWithDeps = true; }
}
function assertComponent(meta) {
    if (!meta.isComponent) {
        throw new BaseException(`Could not compile '${meta.type.name}' because it is not a component.`);
    }
}
//# sourceMappingURL=runtime_compiler.js.map