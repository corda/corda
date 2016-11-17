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
var core_private_1 = require('../core_private');
var collection_1 = require('../src/facade/collection');
var exceptions_1 = require('../src/facade/exceptions');
var lang_1 = require('../src/facade/lang');
var assertions_1 = require('./assertions');
var cpl = require('./compile_metadata');
var config_1 = require('./config');
var directive_lifecycle_reflector_1 = require('./directive_lifecycle_reflector');
var directive_resolver_1 = require('./directive_resolver');
var pipe_resolver_1 = require('./pipe_resolver');
var url_resolver_1 = require('./url_resolver');
var util_1 = require('./util');
var view_resolver_1 = require('./view_resolver');
var CompileMetadataResolver = (function () {
    function CompileMetadataResolver(_directiveResolver, _pipeResolver, _viewResolver, _config, _reflector) {
        if (_reflector === void 0) { _reflector = core_private_1.reflector; }
        this._directiveResolver = _directiveResolver;
        this._pipeResolver = _pipeResolver;
        this._viewResolver = _viewResolver;
        this._config = _config;
        this._reflector = _reflector;
        this._directiveCache = new Map();
        this._pipeCache = new Map();
        this._anonymousTypes = new Map();
        this._anonymousTypeIndex = 0;
    }
    CompileMetadataResolver.prototype.sanitizeTokenName = function (token) {
        var identifier = lang_1.stringify(token);
        if (identifier.indexOf('(') >= 0) {
            // case: anonymous functions!
            var found = this._anonymousTypes.get(token);
            if (lang_1.isBlank(found)) {
                this._anonymousTypes.set(token, this._anonymousTypeIndex++);
                found = this._anonymousTypes.get(token);
            }
            identifier = "anonymous_token_" + found + "_";
        }
        return util_1.sanitizeIdentifier(identifier);
    };
    CompileMetadataResolver.prototype.clearCacheFor = function (compType) {
        this._directiveCache.delete(compType);
        this._pipeCache.delete(compType);
    };
    CompileMetadataResolver.prototype.clearCache = function () {
        this._directiveCache.clear();
        this._pipeCache.clear();
    };
    CompileMetadataResolver.prototype.getAnimationEntryMetadata = function (entry) {
        var _this = this;
        var defs = entry.definitions.map(function (def) { return _this.getAnimationStateMetadata(def); });
        return new cpl.CompileAnimationEntryMetadata(entry.name, defs);
    };
    CompileMetadataResolver.prototype.getAnimationStateMetadata = function (value) {
        if (value instanceof core_1.AnimationStateDeclarationMetadata) {
            var styles = this.getAnimationStyleMetadata(value.styles);
            return new cpl.CompileAnimationStateDeclarationMetadata(value.stateNameExpr, styles);
        }
        else if (value instanceof core_1.AnimationStateTransitionMetadata) {
            return new cpl.CompileAnimationStateTransitionMetadata(value.stateChangeExpr, this.getAnimationMetadata(value.steps));
        }
        return null;
    };
    CompileMetadataResolver.prototype.getAnimationStyleMetadata = function (value) {
        return new cpl.CompileAnimationStyleMetadata(value.offset, value.styles);
    };
    CompileMetadataResolver.prototype.getAnimationMetadata = function (value) {
        var _this = this;
        if (value instanceof core_1.AnimationStyleMetadata) {
            return this.getAnimationStyleMetadata(value);
        }
        else if (value instanceof core_1.AnimationKeyframesSequenceMetadata) {
            return new cpl.CompileAnimationKeyframesSequenceMetadata(value.steps.map(function (entry) { return _this.getAnimationStyleMetadata(entry); }));
        }
        else if (value instanceof core_1.AnimationAnimateMetadata) {
            var animateData = this
                .getAnimationMetadata(value.styles);
            return new cpl.CompileAnimationAnimateMetadata(value.timings, animateData);
        }
        else if (value instanceof core_1.AnimationWithStepsMetadata) {
            var steps = value.steps.map(function (step) { return _this.getAnimationMetadata(step); });
            if (value instanceof core_1.AnimationGroupMetadata) {
                return new cpl.CompileAnimationGroupMetadata(steps);
            }
            else {
                return new cpl.CompileAnimationSequenceMetadata(steps);
            }
        }
        return null;
    };
    CompileMetadataResolver.prototype.getDirectiveMetadata = function (directiveType) {
        var _this = this;
        var meta = this._directiveCache.get(directiveType);
        if (lang_1.isBlank(meta)) {
            var dirMeta = this._directiveResolver.resolve(directiveType);
            var templateMeta = null;
            var changeDetectionStrategy = null;
            var viewProviders = [];
            var moduleUrl = staticTypeModuleUrl(directiveType);
            var precompileTypes = [];
            if (dirMeta instanceof core_1.ComponentMetadata) {
                var cmpMeta = dirMeta;
                var viewMeta = this._viewResolver.resolve(directiveType);
                assertions_1.assertArrayOfStrings('styles', viewMeta.styles);
                assertions_1.assertInterpolationSymbols('interpolation', viewMeta.interpolation);
                var animations = lang_1.isPresent(viewMeta.animations) ?
                    viewMeta.animations.map(function (e) { return _this.getAnimationEntryMetadata(e); }) :
                    null;
                assertions_1.assertArrayOfStrings('styles', viewMeta.styles);
                assertions_1.assertArrayOfStrings('styleUrls', viewMeta.styleUrls);
                templateMeta = new cpl.CompileTemplateMetadata({
                    encapsulation: viewMeta.encapsulation,
                    template: viewMeta.template,
                    templateUrl: viewMeta.templateUrl,
                    styles: viewMeta.styles,
                    styleUrls: viewMeta.styleUrls,
                    animations: animations,
                    interpolation: viewMeta.interpolation
                });
                changeDetectionStrategy = cmpMeta.changeDetection;
                if (lang_1.isPresent(dirMeta.viewProviders)) {
                    viewProviders = this.getProvidersMetadata(verifyNonBlankProviders(directiveType, dirMeta.viewProviders, 'viewProviders'));
                }
                moduleUrl = componentModuleUrl(this._reflector, directiveType, cmpMeta);
                if (cmpMeta.precompile) {
                    precompileTypes = flattenArray(cmpMeta.precompile)
                        .map(function (cmp) { return _this.getTypeMetadata(cmp, staticTypeModuleUrl(cmp)); });
                }
            }
            var providers = [];
            if (lang_1.isPresent(dirMeta.providers)) {
                providers = this.getProvidersMetadata(verifyNonBlankProviders(directiveType, dirMeta.providers, 'providers'));
            }
            var queries = [];
            var viewQueries = [];
            if (lang_1.isPresent(dirMeta.queries)) {
                queries = this.getQueriesMetadata(dirMeta.queries, false, directiveType);
                viewQueries = this.getQueriesMetadata(dirMeta.queries, true, directiveType);
            }
            meta = cpl.CompileDirectiveMetadata.create({
                selector: dirMeta.selector,
                exportAs: dirMeta.exportAs,
                isComponent: lang_1.isPresent(templateMeta),
                type: this.getTypeMetadata(directiveType, moduleUrl),
                template: templateMeta,
                changeDetection: changeDetectionStrategy,
                inputs: dirMeta.inputs,
                outputs: dirMeta.outputs,
                host: dirMeta.host,
                lifecycleHooks: core_private_1.LIFECYCLE_HOOKS_VALUES.filter(function (hook) { return directive_lifecycle_reflector_1.hasLifecycleHook(hook, directiveType); }),
                providers: providers,
                viewProviders: viewProviders,
                queries: queries,
                viewQueries: viewQueries,
                precompile: precompileTypes
            });
            this._directiveCache.set(directiveType, meta);
        }
        return meta;
    };
    /**
     * @param someType a symbol which may or may not be a directive type
     * @returns {cpl.CompileDirectiveMetadata} if possible, otherwise null.
     */
    CompileMetadataResolver.prototype.maybeGetDirectiveMetadata = function (someType) {
        try {
            return this.getDirectiveMetadata(someType);
        }
        catch (e) {
            if (e.message.indexOf('No Directive annotation') !== -1) {
                return null;
            }
            throw e;
        }
    };
    CompileMetadataResolver.prototype.getTypeMetadata = function (type, moduleUrl, dependencies) {
        if (dependencies === void 0) { dependencies = null; }
        return new cpl.CompileTypeMetadata({
            name: this.sanitizeTokenName(type),
            moduleUrl: moduleUrl,
            runtime: type,
            diDeps: this.getDependenciesMetadata(type, dependencies)
        });
    };
    CompileMetadataResolver.prototype.getFactoryMetadata = function (factory, moduleUrl, dependencies) {
        if (dependencies === void 0) { dependencies = null; }
        return new cpl.CompileFactoryMetadata({
            name: this.sanitizeTokenName(factory),
            moduleUrl: moduleUrl,
            runtime: factory,
            diDeps: this.getDependenciesMetadata(factory, dependencies)
        });
    };
    CompileMetadataResolver.prototype.getPipeMetadata = function (pipeType) {
        var meta = this._pipeCache.get(pipeType);
        if (lang_1.isBlank(meta)) {
            var pipeMeta = this._pipeResolver.resolve(pipeType);
            meta = new cpl.CompilePipeMetadata({
                type: this.getTypeMetadata(pipeType, staticTypeModuleUrl(pipeType)),
                name: pipeMeta.name,
                pure: pipeMeta.pure,
                lifecycleHooks: core_private_1.LIFECYCLE_HOOKS_VALUES.filter(function (hook) { return directive_lifecycle_reflector_1.hasLifecycleHook(hook, pipeType); }),
            });
            this._pipeCache.set(pipeType, meta);
        }
        return meta;
    };
    CompileMetadataResolver.prototype.getViewDirectivesMetadata = function (component) {
        var _this = this;
        var view = this._viewResolver.resolve(component);
        var directives = flattenDirectives(view, this._config.platformDirectives);
        for (var i = 0; i < directives.length; i++) {
            if (!isValidType(directives[i])) {
                throw new exceptions_1.BaseException("Unexpected directive value '" + lang_1.stringify(directives[i]) + "' on the View of component '" + lang_1.stringify(component) + "'");
            }
        }
        return directives.map(function (type) { return _this.getDirectiveMetadata(type); });
    };
    CompileMetadataResolver.prototype.getViewPipesMetadata = function (component) {
        var _this = this;
        var view = this._viewResolver.resolve(component);
        var pipes = flattenPipes(view, this._config.platformPipes);
        for (var i = 0; i < pipes.length; i++) {
            if (!isValidType(pipes[i])) {
                throw new exceptions_1.BaseException("Unexpected piped value '" + lang_1.stringify(pipes[i]) + "' on the View of component '" + lang_1.stringify(component) + "'");
            }
        }
        return pipes.map(function (type) { return _this.getPipeMetadata(type); });
    };
    CompileMetadataResolver.prototype.getDependenciesMetadata = function (typeOrFunc, dependencies) {
        var _this = this;
        var hasUnknownDeps = false;
        var params = lang_1.isPresent(dependencies) ? dependencies : this._reflector.parameters(typeOrFunc);
        if (lang_1.isBlank(params)) {
            params = [];
        }
        var dependenciesMetadata = params.map(function (param) {
            var isAttribute = false;
            var isHost = false;
            var isSelf = false;
            var isSkipSelf = false;
            var isOptional = false;
            var query = null;
            var viewQuery = null;
            var token = null;
            if (lang_1.isArray(param)) {
                param.forEach(function (paramEntry) {
                    if (paramEntry instanceof core_1.HostMetadata) {
                        isHost = true;
                    }
                    else if (paramEntry instanceof core_1.SelfMetadata) {
                        isSelf = true;
                    }
                    else if (paramEntry instanceof core_1.SkipSelfMetadata) {
                        isSkipSelf = true;
                    }
                    else if (paramEntry instanceof core_1.OptionalMetadata) {
                        isOptional = true;
                    }
                    else if (paramEntry instanceof core_1.AttributeMetadata) {
                        isAttribute = true;
                        token = paramEntry.attributeName;
                    }
                    else if (paramEntry instanceof core_1.QueryMetadata) {
                        if (paramEntry.isViewQuery) {
                            viewQuery = paramEntry;
                        }
                        else {
                            query = paramEntry;
                        }
                    }
                    else if (paramEntry instanceof core_1.InjectMetadata) {
                        token = paramEntry.token;
                    }
                    else if (isValidType(paramEntry) && lang_1.isBlank(token)) {
                        token = paramEntry;
                    }
                });
            }
            else {
                token = param;
            }
            if (lang_1.isBlank(token)) {
                hasUnknownDeps = true;
                return null;
            }
            return new cpl.CompileDiDependencyMetadata({
                isAttribute: isAttribute,
                isHost: isHost,
                isSelf: isSelf,
                isSkipSelf: isSkipSelf,
                isOptional: isOptional,
                query: lang_1.isPresent(query) ? _this.getQueryMetadata(query, null, typeOrFunc) : null,
                viewQuery: lang_1.isPresent(viewQuery) ? _this.getQueryMetadata(viewQuery, null, typeOrFunc) : null,
                token: _this.getTokenMetadata(token)
            });
        });
        if (hasUnknownDeps) {
            var depsTokens = dependenciesMetadata.map(function (dep) { return dep ? lang_1.stringify(dep.token) : '?'; })
                .join(', ');
            throw new exceptions_1.BaseException("Can't resolve all parameters for " + lang_1.stringify(typeOrFunc) + ": (" + depsTokens + ").");
        }
        return dependenciesMetadata;
    };
    CompileMetadataResolver.prototype.getTokenMetadata = function (token) {
        token = core_1.resolveForwardRef(token);
        var compileToken;
        if (lang_1.isString(token)) {
            compileToken = new cpl.CompileTokenMetadata({ value: token });
        }
        else {
            compileToken = new cpl.CompileTokenMetadata({
                identifier: new cpl.CompileIdentifierMetadata({
                    runtime: token,
                    name: this.sanitizeTokenName(token),
                    moduleUrl: staticTypeModuleUrl(token)
                })
            });
        }
        return compileToken;
    };
    CompileMetadataResolver.prototype.getProvidersMetadata = function (providers) {
        var _this = this;
        return providers.map(function (provider) {
            provider = core_1.resolveForwardRef(provider);
            if (lang_1.isArray(provider)) {
                return _this.getProvidersMetadata(provider);
            }
            else if (provider instanceof core_1.Provider) {
                return _this.getProviderMetadata(provider);
            }
            else if (core_private_1.isProviderLiteral(provider)) {
                return _this.getProviderMetadata(core_private_1.createProvider(provider));
            }
            else {
                return _this.getTypeMetadata(provider, staticTypeModuleUrl(provider));
            }
        });
    };
    CompileMetadataResolver.prototype.getProviderMetadata = function (provider) {
        var compileDeps;
        var compileTypeMetadata = null;
        var compileFactoryMetadata = null;
        if (lang_1.isPresent(provider.useClass)) {
            compileTypeMetadata = this.getTypeMetadata(provider.useClass, staticTypeModuleUrl(provider.useClass), provider.dependencies);
            compileDeps = compileTypeMetadata.diDeps;
        }
        else if (lang_1.isPresent(provider.useFactory)) {
            compileFactoryMetadata = this.getFactoryMetadata(provider.useFactory, staticTypeModuleUrl(provider.useFactory), provider.dependencies);
            compileDeps = compileFactoryMetadata.diDeps;
        }
        return new cpl.CompileProviderMetadata({
            token: this.getTokenMetadata(provider.token),
            useClass: compileTypeMetadata,
            useValue: convertToCompileValue(provider.useValue),
            useFactory: compileFactoryMetadata,
            useExisting: lang_1.isPresent(provider.useExisting) ? this.getTokenMetadata(provider.useExisting) :
                null,
            deps: compileDeps,
            multi: provider.multi
        });
    };
    CompileMetadataResolver.prototype.getQueriesMetadata = function (queries, isViewQuery, directiveType) {
        var _this = this;
        var compileQueries = [];
        collection_1.StringMapWrapper.forEach(queries, function (query /** TODO #9100 */, propertyName /** TODO #9100 */) {
            if (query.isViewQuery === isViewQuery) {
                compileQueries.push(_this.getQueryMetadata(query, propertyName, directiveType));
            }
        });
        return compileQueries;
    };
    CompileMetadataResolver.prototype.getQueryMetadata = function (q, propertyName, typeOrFunc) {
        var _this = this;
        var selectors;
        if (q.isVarBindingQuery) {
            selectors = q.varBindings.map(function (varName) { return _this.getTokenMetadata(varName); });
        }
        else {
            if (!lang_1.isPresent(q.selector)) {
                throw new exceptions_1.BaseException("Can't construct a query for the property \"" + propertyName + "\" of \"" + lang_1.stringify(typeOrFunc) + "\" since the query selector wasn't defined.");
            }
            selectors = [this.getTokenMetadata(q.selector)];
        }
        return new cpl.CompileQueryMetadata({
            selectors: selectors,
            first: q.first,
            descendants: q.descendants,
            propertyName: propertyName,
            read: lang_1.isPresent(q.read) ? this.getTokenMetadata(q.read) : null
        });
    };
    /** @nocollapse */
    CompileMetadataResolver.decorators = [
        { type: core_1.Injectable },
    ];
    /** @nocollapse */
    CompileMetadataResolver.ctorParameters = [
        { type: directive_resolver_1.DirectiveResolver, },
        { type: pipe_resolver_1.PipeResolver, },
        { type: view_resolver_1.ViewResolver, },
        { type: config_1.CompilerConfig, },
        { type: core_private_1.ReflectorReader, },
    ];
    return CompileMetadataResolver;
}());
exports.CompileMetadataResolver = CompileMetadataResolver;
function flattenDirectives(view, platformDirectives) {
    var directives = [];
    if (lang_1.isPresent(platformDirectives)) {
        flattenArray(platformDirectives, directives);
    }
    if (lang_1.isPresent(view.directives)) {
        flattenArray(view.directives, directives);
    }
    return directives;
}
function flattenPipes(view, platformPipes) {
    var pipes = [];
    if (lang_1.isPresent(platformPipes)) {
        flattenArray(platformPipes, pipes);
    }
    if (lang_1.isPresent(view.pipes)) {
        flattenArray(view.pipes, pipes);
    }
    return pipes;
}
function flattenArray(tree, out) {
    if (out === void 0) { out = []; }
    for (var i = 0; i < tree.length; i++) {
        var item = core_1.resolveForwardRef(tree[i]);
        if (lang_1.isArray(item)) {
            flattenArray(item, out);
        }
        else {
            out.push(item);
        }
    }
    return out;
}
function verifyNonBlankProviders(directiveType, providersTree, providersType) {
    var flat = [];
    var errMsg;
    flattenArray(providersTree, flat);
    for (var i = 0; i < flat.length; i++) {
        if (lang_1.isBlank(flat[i])) {
            errMsg = flat.map(function (provider) { return lang_1.isBlank(provider) ? '?' : lang_1.stringify(provider); }).join(', ');
            throw new exceptions_1.BaseException("One or more of " + providersType + " for \"" + lang_1.stringify(directiveType) + "\" were not defined: [" + errMsg + "].");
        }
    }
    return providersTree;
}
function isStaticType(value) {
    return lang_1.isStringMap(value) && lang_1.isPresent(value['name']) && lang_1.isPresent(value['filePath']);
}
function isValidType(value) {
    return isStaticType(value) || (value instanceof lang_1.Type);
}
function staticTypeModuleUrl(value) {
    return isStaticType(value) ? value['filePath'] : null;
}
function componentModuleUrl(reflector, type, cmpMetadata) {
    if (isStaticType(type)) {
        return staticTypeModuleUrl(type);
    }
    if (lang_1.isPresent(cmpMetadata.moduleId)) {
        var moduleId = cmpMetadata.moduleId;
        var scheme = url_resolver_1.getUrlScheme(moduleId);
        return lang_1.isPresent(scheme) && scheme.length > 0 ? moduleId :
            "package:" + moduleId + util_1.MODULE_SUFFIX;
    }
    return reflector.importUri(type);
}
// Only fill CompileIdentifierMetadata.runtime if needed...
function convertToCompileValue(value) {
    return util_1.visitValue(value, new _CompileValueConverter(), null);
}
var _CompileValueConverter = (function (_super) {
    __extends(_CompileValueConverter, _super);
    function _CompileValueConverter() {
        _super.apply(this, arguments);
    }
    _CompileValueConverter.prototype.visitOther = function (value, context) {
        if (isStaticType(value)) {
            return new cpl.CompileIdentifierMetadata({ name: value['name'], moduleUrl: staticTypeModuleUrl(value) });
        }
        else {
            return new cpl.CompileIdentifierMetadata({ runtime: value });
        }
    };
    return _CompileValueConverter;
}(util_1.ValueTransformer));
//# sourceMappingURL=metadata_resolver.js.map