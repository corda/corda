/**
 * @license
 * Copyright Google Inc. All Rights Reserved.
 *
 * Use of this source code is governed by an MIT-style license that can be
 * found in the LICENSE file at https://angular.io/license
 */
"use strict";
var core_1 = require('@angular/core');
var exceptions_1 = require('../src/facade/exceptions');
var lang_1 = require('../src/facade/lang');
var async_1 = require('../src/facade/async');
var compile_metadata_1 = require('./compile_metadata');
var style_compiler_1 = require('./style_compiler');
var view_compiler_1 = require('./view_compiler/view_compiler');
var template_parser_1 = require('./template_parser');
var directive_normalizer_1 = require('./directive_normalizer');
var metadata_resolver_1 = require('./metadata_resolver');
var config_1 = require('./config');
var ir = require('./output/output_ast');
var output_jit_1 = require('./output/output_jit');
var output_interpreter_1 = require('./output/output_interpreter');
var interpretive_view_1 = require('./output/interpretive_view');
var RuntimeCompiler = (function () {
    function RuntimeCompiler(_metadataResolver, _templateNormalizer, _templateParser, _styleCompiler, _viewCompiler, _genConfig) {
        this._metadataResolver = _metadataResolver;
        this._templateNormalizer = _templateNormalizer;
        this._templateParser = _templateParser;
        this._styleCompiler = _styleCompiler;
        this._viewCompiler = _viewCompiler;
        this._genConfig = _genConfig;
        this._compiledTemplateCache = new Map();
        this._compiledHostTemplateCache = new Map();
    }
    RuntimeCompiler.prototype.resolveComponent = function (component) {
        if (lang_1.isString(component)) {
            return async_1.PromiseWrapper.reject(new exceptions_1.BaseException("Cannot resolve component using '" + component + "'."), null);
        }
        return this.compileComponentAsync(component);
    };
    RuntimeCompiler.prototype.compileComponentAsync = function (compType) {
        var _this = this;
        var templates = this._getTransitiveCompiledTemplates(compType, true);
        var loadingPromises = [];
        templates.forEach(function (template) {
            if (template.loading) {
                loadingPromises.push(template.loading);
            }
        });
        return Promise.all(loadingPromises).then(function () {
            templates.forEach(function (template) { _this._compileTemplate(template); });
            return _this._getCompiledHostTemplate(compType).proxyComponentFactory;
        });
    };
    RuntimeCompiler.prototype.compileComponentSync = function (compType) {
        var _this = this;
        var templates = this._getTransitiveCompiledTemplates(compType, true);
        templates.forEach(function (template) {
            if (template.loading) {
                throw new exceptions_1.BaseException("Can't compile synchronously as " + template.compType.name + " is still being loaded!");
            }
        });
        templates.forEach(function (template) { _this._compileTemplate(template); });
        return this._getCompiledHostTemplate(compType).proxyComponentFactory;
    };
    RuntimeCompiler.prototype.clearCacheFor = function (compType) {
        this._metadataResolver.clearCacheFor(compType);
        this._compiledHostTemplateCache.delete(compType);
        var compiledTemplate = this._compiledTemplateCache.get(compType);
        if (compiledTemplate) {
            this._templateNormalizer.clearCacheFor(compiledTemplate.normalizedCompMeta);
            this._compiledTemplateCache.delete(compType);
        }
    };
    RuntimeCompiler.prototype.clearCache = function () {
        this._metadataResolver.clearCache();
        this._compiledTemplateCache.clear();
        this._compiledHostTemplateCache.clear();
        this._templateNormalizer.clearCache();
    };
    RuntimeCompiler.prototype._getCompiledHostTemplate = function (type) {
        var compiledTemplate = this._compiledHostTemplateCache.get(type);
        if (lang_1.isBlank(compiledTemplate)) {
            var compMeta = this._metadataResolver.getDirectiveMetadata(type);
            assertComponent(compMeta);
            var hostMeta = compile_metadata_1.createHostComponentMeta(compMeta.type, compMeta.selector);
            compiledTemplate = new CompiledTemplate(true, compMeta.selector, compMeta.type, [], [type], [], [], this._templateNormalizer.normalizeDirective(hostMeta));
            this._compiledHostTemplateCache.set(type, compiledTemplate);
        }
        return compiledTemplate;
    };
    RuntimeCompiler.prototype._getCompiledTemplate = function (type) {
        var compiledTemplate = this._compiledTemplateCache.get(type);
        if (lang_1.isBlank(compiledTemplate)) {
            var compMeta = this._metadataResolver.getDirectiveMetadata(type);
            assertComponent(compMeta);
            var viewDirectives = [];
            var viewComponentTypes = [];
            this._metadataResolver.getViewDirectivesMetadata(type).forEach(function (dirOrComp) {
                if (dirOrComp.isComponent) {
                    viewComponentTypes.push(dirOrComp.type.runtime);
                }
                else {
                    viewDirectives.push(dirOrComp);
                }
            });
            var precompileComponentTypes = compMeta.precompile.map(function (typeMeta) { return typeMeta.runtime; });
            var pipes = this._metadataResolver.getViewPipesMetadata(type);
            compiledTemplate = new CompiledTemplate(false, compMeta.selector, compMeta.type, viewDirectives, viewComponentTypes, precompileComponentTypes, pipes, this._templateNormalizer.normalizeDirective(compMeta));
            this._compiledTemplateCache.set(type, compiledTemplate);
        }
        return compiledTemplate;
    };
    RuntimeCompiler.prototype._getTransitiveCompiledTemplates = function (compType, isHost, target) {
        var _this = this;
        if (target === void 0) { target = new Set(); }
        var template = isHost ? this._getCompiledHostTemplate(compType) : this._getCompiledTemplate(compType);
        if (!target.has(template)) {
            target.add(template);
            template.viewComponentTypes.forEach(function (compType) { _this._getTransitiveCompiledTemplates(compType, false, target); });
            template.precompileHostComponentTypes.forEach(function (compType) { _this._getTransitiveCompiledTemplates(compType, true, target); });
        }
        return target;
    };
    RuntimeCompiler.prototype._compileTemplate = function (template) {
        var _this = this;
        if (template.isCompiled) {
            return;
        }
        var compMeta = template.normalizedCompMeta;
        var externalStylesheetsByModuleUrl = new Map();
        var stylesCompileResult = this._styleCompiler.compileComponent(compMeta);
        stylesCompileResult.externalStylesheets.forEach(function (r) { externalStylesheetsByModuleUrl.set(r.meta.moduleUrl, r); });
        this._resolveStylesCompileResult(stylesCompileResult.componentStylesheet, externalStylesheetsByModuleUrl);
        var viewCompMetas = template.viewComponentTypes.map(function (compType) { return _this._getCompiledTemplate(compType).normalizedCompMeta; });
        var parsedTemplate = this._templateParser.parse(compMeta, compMeta.template.template, template.viewDirectives.concat(viewCompMetas), template.viewPipes, compMeta.type.name);
        var compileResult = this._viewCompiler.compileComponent(compMeta, parsedTemplate, ir.variable(stylesCompileResult.componentStylesheet.stylesVar), template.viewPipes);
        var depTemplates = compileResult.dependencies.map(function (dep) {
            var depTemplate;
            if (dep instanceof view_compiler_1.ViewFactoryDependency) {
                var vfd = dep;
                depTemplate = _this._getCompiledTemplate(vfd.comp.runtime);
                vfd.placeholder.runtime = depTemplate.proxyViewFactory;
                vfd.placeholder.name = "viewFactory_" + vfd.comp.name;
            }
            else if (dep instanceof view_compiler_1.ComponentFactoryDependency) {
                var cfd = dep;
                depTemplate = _this._getCompiledHostTemplate(cfd.comp.runtime);
                cfd.placeholder.runtime = depTemplate.proxyComponentFactory;
                cfd.placeholder.name = "compFactory_" + cfd.comp.name;
            }
            return depTemplate;
        });
        var statements = stylesCompileResult.componentStylesheet.statements.concat(compileResult.statements);
        var factory;
        if (lang_1.IS_DART || !this._genConfig.useJit) {
            factory = output_interpreter_1.interpretStatements(statements, compileResult.viewFactoryVar, new interpretive_view_1.InterpretiveAppViewInstanceFactory());
        }
        else {
            factory = output_jit_1.jitStatements(template.compType.name + ".template.js", statements, compileResult.viewFactoryVar);
        }
        template.compiled(factory);
    };
    RuntimeCompiler.prototype._resolveStylesCompileResult = function (result, externalStylesheetsByModuleUrl) {
        var _this = this;
        result.dependencies.forEach(function (dep, i) {
            var nestedCompileResult = externalStylesheetsByModuleUrl.get(dep.moduleUrl);
            var nestedStylesArr = _this._resolveAndEvalStylesCompileResult(nestedCompileResult, externalStylesheetsByModuleUrl);
            dep.valuePlaceholder.runtime = nestedStylesArr;
            dep.valuePlaceholder.name = "importedStyles" + i;
        });
    };
    RuntimeCompiler.prototype._resolveAndEvalStylesCompileResult = function (result, externalStylesheetsByModuleUrl) {
        this._resolveStylesCompileResult(result, externalStylesheetsByModuleUrl);
        if (lang_1.IS_DART || !this._genConfig.useJit) {
            return output_interpreter_1.interpretStatements(result.statements, result.stylesVar, new interpretive_view_1.InterpretiveAppViewInstanceFactory());
        }
        else {
            return output_jit_1.jitStatements(result.meta.moduleUrl + ".css.js", result.statements, result.stylesVar);
        }
    };
    /** @nocollapse */
    RuntimeCompiler.decorators = [
        { type: core_1.Injectable },
    ];
    /** @nocollapse */
    RuntimeCompiler.ctorParameters = [
        { type: metadata_resolver_1.CompileMetadataResolver, },
        { type: directive_normalizer_1.DirectiveNormalizer, },
        { type: template_parser_1.TemplateParser, },
        { type: style_compiler_1.StyleCompiler, },
        { type: view_compiler_1.ViewCompiler, },
        { type: config_1.CompilerConfig, },
    ];
    return RuntimeCompiler;
}());
exports.RuntimeCompiler = RuntimeCompiler;
var CompiledTemplate = (function () {
    function CompiledTemplate(isHost, selector, compType, viewDirectives, viewComponentTypes, precompileHostComponentTypes, viewPipes, _normalizeResult) {
        var _this = this;
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
        this.proxyViewFactory = function () {
            var args = [];
            for (var _i = 0; _i < arguments.length; _i++) {
                args[_i - 0] = arguments[_i];
            }
            return _this._viewFactory.apply(null, args);
        };
        this.proxyComponentFactory = isHost ?
            new core_1.ComponentFactory(selector, this.proxyViewFactory, compType.runtime) :
            null;
        if (_normalizeResult.syncResult) {
            this._normalizedCompMeta = _normalizeResult.syncResult;
        }
        else {
            this.loading = _normalizeResult.asyncResult.then(function (normalizedCompMeta) {
                _this._normalizedCompMeta = normalizedCompMeta;
                _this.loading = null;
            });
        }
    }
    Object.defineProperty(CompiledTemplate.prototype, "normalizedCompMeta", {
        get: function () {
            if (this.loading) {
                throw new exceptions_1.BaseException("Template is still loading for " + this.compType.name + "!");
            }
            return this._normalizedCompMeta;
        },
        enumerable: true,
        configurable: true
    });
    CompiledTemplate.prototype.compiled = function (viewFactory) {
        this._viewFactory = viewFactory;
        this.isCompiled = true;
    };
    CompiledTemplate.prototype.depsCompiled = function () { this.isCompiledWithDeps = true; };
    return CompiledTemplate;
}());
function assertComponent(meta) {
    if (!meta.isComponent) {
        throw new exceptions_1.BaseException("Could not compile '" + meta.type.name + "' because it is not a component.");
    }
}
//# sourceMappingURL=runtime_compiler.js.map