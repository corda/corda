/**
 * @license
 * Copyright Google Inc. All Rights Reserved.
 *
 * Use of this source code is governed by an MIT-style license that can be
 * found in the LICENSE file at https://angular.io/license
 */
import { AnimationAnimateMetadata, AnimationGroupMetadata, AnimationKeyframesSequenceMetadata, AnimationStateDeclarationMetadata, AnimationStateTransitionMetadata, AnimationStyleMetadata, AnimationWithStepsMetadata, AttributeMetadata, ComponentMetadata, HostMetadata, InjectMetadata, Injectable, OptionalMetadata, Provider, QueryMetadata, SelfMetadata, SkipSelfMetadata, resolveForwardRef } from '@angular/core';
import { LIFECYCLE_HOOKS_VALUES, ReflectorReader, createProvider, isProviderLiteral, reflector } from '../core_private';
import { StringMapWrapper } from '../src/facade/collection';
import { BaseException } from '../src/facade/exceptions';
import { Type, isArray, isBlank, isPresent, isString, isStringMap, stringify } from '../src/facade/lang';
import { assertArrayOfStrings, assertInterpolationSymbols } from './assertions';
import * as cpl from './compile_metadata';
import { CompilerConfig } from './config';
import { hasLifecycleHook } from './directive_lifecycle_reflector';
import { DirectiveResolver } from './directive_resolver';
import { PipeResolver } from './pipe_resolver';
import { getUrlScheme } from './url_resolver';
import { MODULE_SUFFIX, ValueTransformer, sanitizeIdentifier, visitValue } from './util';
import { ViewResolver } from './view_resolver';
export class CompileMetadataResolver {
    constructor(_directiveResolver, _pipeResolver, _viewResolver, _config, _reflector = reflector) {
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
    sanitizeTokenName(token) {
        let identifier = stringify(token);
        if (identifier.indexOf('(') >= 0) {
            // case: anonymous functions!
            let found = this._anonymousTypes.get(token);
            if (isBlank(found)) {
                this._anonymousTypes.set(token, this._anonymousTypeIndex++);
                found = this._anonymousTypes.get(token);
            }
            identifier = `anonymous_token_${found}_`;
        }
        return sanitizeIdentifier(identifier);
    }
    clearCacheFor(compType) {
        this._directiveCache.delete(compType);
        this._pipeCache.delete(compType);
    }
    clearCache() {
        this._directiveCache.clear();
        this._pipeCache.clear();
    }
    getAnimationEntryMetadata(entry) {
        var defs = entry.definitions.map(def => this.getAnimationStateMetadata(def));
        return new cpl.CompileAnimationEntryMetadata(entry.name, defs);
    }
    getAnimationStateMetadata(value) {
        if (value instanceof AnimationStateDeclarationMetadata) {
            var styles = this.getAnimationStyleMetadata(value.styles);
            return new cpl.CompileAnimationStateDeclarationMetadata(value.stateNameExpr, styles);
        }
        else if (value instanceof AnimationStateTransitionMetadata) {
            return new cpl.CompileAnimationStateTransitionMetadata(value.stateChangeExpr, this.getAnimationMetadata(value.steps));
        }
        return null;
    }
    getAnimationStyleMetadata(value) {
        return new cpl.CompileAnimationStyleMetadata(value.offset, value.styles);
    }
    getAnimationMetadata(value) {
        if (value instanceof AnimationStyleMetadata) {
            return this.getAnimationStyleMetadata(value);
        }
        else if (value instanceof AnimationKeyframesSequenceMetadata) {
            return new cpl.CompileAnimationKeyframesSequenceMetadata(value.steps.map(entry => this.getAnimationStyleMetadata(entry)));
        }
        else if (value instanceof AnimationAnimateMetadata) {
            let animateData = this
                .getAnimationMetadata(value.styles);
            return new cpl.CompileAnimationAnimateMetadata(value.timings, animateData);
        }
        else if (value instanceof AnimationWithStepsMetadata) {
            var steps = value.steps.map(step => this.getAnimationMetadata(step));
            if (value instanceof AnimationGroupMetadata) {
                return new cpl.CompileAnimationGroupMetadata(steps);
            }
            else {
                return new cpl.CompileAnimationSequenceMetadata(steps);
            }
        }
        return null;
    }
    getDirectiveMetadata(directiveType) {
        var meta = this._directiveCache.get(directiveType);
        if (isBlank(meta)) {
            var dirMeta = this._directiveResolver.resolve(directiveType);
            var templateMeta = null;
            var changeDetectionStrategy = null;
            var viewProviders = [];
            var moduleUrl = staticTypeModuleUrl(directiveType);
            var precompileTypes = [];
            if (dirMeta instanceof ComponentMetadata) {
                var cmpMeta = dirMeta;
                var viewMeta = this._viewResolver.resolve(directiveType);
                assertArrayOfStrings('styles', viewMeta.styles);
                assertInterpolationSymbols('interpolation', viewMeta.interpolation);
                var animations = isPresent(viewMeta.animations) ?
                    viewMeta.animations.map(e => this.getAnimationEntryMetadata(e)) :
                    null;
                assertArrayOfStrings('styles', viewMeta.styles);
                assertArrayOfStrings('styleUrls', viewMeta.styleUrls);
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
                if (isPresent(dirMeta.viewProviders)) {
                    viewProviders = this.getProvidersMetadata(verifyNonBlankProviders(directiveType, dirMeta.viewProviders, 'viewProviders'));
                }
                moduleUrl = componentModuleUrl(this._reflector, directiveType, cmpMeta);
                if (cmpMeta.precompile) {
                    precompileTypes = flattenArray(cmpMeta.precompile)
                        .map((cmp) => this.getTypeMetadata(cmp, staticTypeModuleUrl(cmp)));
                }
            }
            var providers = [];
            if (isPresent(dirMeta.providers)) {
                providers = this.getProvidersMetadata(verifyNonBlankProviders(directiveType, dirMeta.providers, 'providers'));
            }
            var queries = [];
            var viewQueries = [];
            if (isPresent(dirMeta.queries)) {
                queries = this.getQueriesMetadata(dirMeta.queries, false, directiveType);
                viewQueries = this.getQueriesMetadata(dirMeta.queries, true, directiveType);
            }
            meta = cpl.CompileDirectiveMetadata.create({
                selector: dirMeta.selector,
                exportAs: dirMeta.exportAs,
                isComponent: isPresent(templateMeta),
                type: this.getTypeMetadata(directiveType, moduleUrl),
                template: templateMeta,
                changeDetection: changeDetectionStrategy,
                inputs: dirMeta.inputs,
                outputs: dirMeta.outputs,
                host: dirMeta.host,
                lifecycleHooks: LIFECYCLE_HOOKS_VALUES.filter(hook => hasLifecycleHook(hook, directiveType)),
                providers: providers,
                viewProviders: viewProviders,
                queries: queries,
                viewQueries: viewQueries,
                precompile: precompileTypes
            });
            this._directiveCache.set(directiveType, meta);
        }
        return meta;
    }
    /**
     * @param someType a symbol which may or may not be a directive type
     * @returns {cpl.CompileDirectiveMetadata} if possible, otherwise null.
     */
    maybeGetDirectiveMetadata(someType) {
        try {
            return this.getDirectiveMetadata(someType);
        }
        catch (e) {
            if (e.message.indexOf('No Directive annotation') !== -1) {
                return null;
            }
            throw e;
        }
    }
    getTypeMetadata(type, moduleUrl, dependencies = null) {
        return new cpl.CompileTypeMetadata({
            name: this.sanitizeTokenName(type),
            moduleUrl: moduleUrl,
            runtime: type,
            diDeps: this.getDependenciesMetadata(type, dependencies)
        });
    }
    getFactoryMetadata(factory, moduleUrl, dependencies = null) {
        return new cpl.CompileFactoryMetadata({
            name: this.sanitizeTokenName(factory),
            moduleUrl: moduleUrl,
            runtime: factory,
            diDeps: this.getDependenciesMetadata(factory, dependencies)
        });
    }
    getPipeMetadata(pipeType) {
        var meta = this._pipeCache.get(pipeType);
        if (isBlank(meta)) {
            var pipeMeta = this._pipeResolver.resolve(pipeType);
            meta = new cpl.CompilePipeMetadata({
                type: this.getTypeMetadata(pipeType, staticTypeModuleUrl(pipeType)),
                name: pipeMeta.name,
                pure: pipeMeta.pure,
                lifecycleHooks: LIFECYCLE_HOOKS_VALUES.filter(hook => hasLifecycleHook(hook, pipeType)),
            });
            this._pipeCache.set(pipeType, meta);
        }
        return meta;
    }
    getViewDirectivesMetadata(component) {
        var view = this._viewResolver.resolve(component);
        var directives = flattenDirectives(view, this._config.platformDirectives);
        for (var i = 0; i < directives.length; i++) {
            if (!isValidType(directives[i])) {
                throw new BaseException(`Unexpected directive value '${stringify(directives[i])}' on the View of component '${stringify(component)}'`);
            }
        }
        return directives.map(type => this.getDirectiveMetadata(type));
    }
    getViewPipesMetadata(component) {
        var view = this._viewResolver.resolve(component);
        var pipes = flattenPipes(view, this._config.platformPipes);
        for (var i = 0; i < pipes.length; i++) {
            if (!isValidType(pipes[i])) {
                throw new BaseException(`Unexpected piped value '${stringify(pipes[i])}' on the View of component '${stringify(component)}'`);
            }
        }
        return pipes.map(type => this.getPipeMetadata(type));
    }
    getDependenciesMetadata(typeOrFunc, dependencies) {
        let hasUnknownDeps = false;
        let params = isPresent(dependencies) ? dependencies : this._reflector.parameters(typeOrFunc);
        if (isBlank(params)) {
            params = [];
        }
        let dependenciesMetadata = params.map((param) => {
            let isAttribute = false;
            let isHost = false;
            let isSelf = false;
            let isSkipSelf = false;
            let isOptional = false;
            let query = null;
            let viewQuery = null;
            var token = null;
            if (isArray(param)) {
                param.forEach((paramEntry) => {
                    if (paramEntry instanceof HostMetadata) {
                        isHost = true;
                    }
                    else if (paramEntry instanceof SelfMetadata) {
                        isSelf = true;
                    }
                    else if (paramEntry instanceof SkipSelfMetadata) {
                        isSkipSelf = true;
                    }
                    else if (paramEntry instanceof OptionalMetadata) {
                        isOptional = true;
                    }
                    else if (paramEntry instanceof AttributeMetadata) {
                        isAttribute = true;
                        token = paramEntry.attributeName;
                    }
                    else if (paramEntry instanceof QueryMetadata) {
                        if (paramEntry.isViewQuery) {
                            viewQuery = paramEntry;
                        }
                        else {
                            query = paramEntry;
                        }
                    }
                    else if (paramEntry instanceof InjectMetadata) {
                        token = paramEntry.token;
                    }
                    else if (isValidType(paramEntry) && isBlank(token)) {
                        token = paramEntry;
                    }
                });
            }
            else {
                token = param;
            }
            if (isBlank(token)) {
                hasUnknownDeps = true;
                return null;
            }
            return new cpl.CompileDiDependencyMetadata({
                isAttribute: isAttribute,
                isHost: isHost,
                isSelf: isSelf,
                isSkipSelf: isSkipSelf,
                isOptional: isOptional,
                query: isPresent(query) ? this.getQueryMetadata(query, null, typeOrFunc) : null,
                viewQuery: isPresent(viewQuery) ? this.getQueryMetadata(viewQuery, null, typeOrFunc) : null,
                token: this.getTokenMetadata(token)
            });
        });
        if (hasUnknownDeps) {
            let depsTokens = dependenciesMetadata.map((dep) => { return dep ? stringify(dep.token) : '?'; })
                .join(', ');
            throw new BaseException(`Can't resolve all parameters for ${stringify(typeOrFunc)}: (${depsTokens}).`);
        }
        return dependenciesMetadata;
    }
    getTokenMetadata(token) {
        token = resolveForwardRef(token);
        var compileToken;
        if (isString(token)) {
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
    }
    getProvidersMetadata(providers) {
        return providers.map((provider) => {
            provider = resolveForwardRef(provider);
            if (isArray(provider)) {
                return this.getProvidersMetadata(provider);
            }
            else if (provider instanceof Provider) {
                return this.getProviderMetadata(provider);
            }
            else if (isProviderLiteral(provider)) {
                return this.getProviderMetadata(createProvider(provider));
            }
            else {
                return this.getTypeMetadata(provider, staticTypeModuleUrl(provider));
            }
        });
    }
    getProviderMetadata(provider) {
        var compileDeps;
        var compileTypeMetadata = null;
        var compileFactoryMetadata = null;
        if (isPresent(provider.useClass)) {
            compileTypeMetadata = this.getTypeMetadata(provider.useClass, staticTypeModuleUrl(provider.useClass), provider.dependencies);
            compileDeps = compileTypeMetadata.diDeps;
        }
        else if (isPresent(provider.useFactory)) {
            compileFactoryMetadata = this.getFactoryMetadata(provider.useFactory, staticTypeModuleUrl(provider.useFactory), provider.dependencies);
            compileDeps = compileFactoryMetadata.diDeps;
        }
        return new cpl.CompileProviderMetadata({
            token: this.getTokenMetadata(provider.token),
            useClass: compileTypeMetadata,
            useValue: convertToCompileValue(provider.useValue),
            useFactory: compileFactoryMetadata,
            useExisting: isPresent(provider.useExisting) ? this.getTokenMetadata(provider.useExisting) :
                null,
            deps: compileDeps,
            multi: provider.multi
        });
    }
    getQueriesMetadata(queries, isViewQuery, directiveType) {
        var compileQueries = [];
        StringMapWrapper.forEach(queries, (query /** TODO #9100 */, propertyName /** TODO #9100 */) => {
            if (query.isViewQuery === isViewQuery) {
                compileQueries.push(this.getQueryMetadata(query, propertyName, directiveType));
            }
        });
        return compileQueries;
    }
    getQueryMetadata(q, propertyName, typeOrFunc) {
        var selectors;
        if (q.isVarBindingQuery) {
            selectors = q.varBindings.map(varName => this.getTokenMetadata(varName));
        }
        else {
            if (!isPresent(q.selector)) {
                throw new BaseException(`Can't construct a query for the property "${propertyName}" of "${stringify(typeOrFunc)}" since the query selector wasn't defined.`);
            }
            selectors = [this.getTokenMetadata(q.selector)];
        }
        return new cpl.CompileQueryMetadata({
            selectors: selectors,
            first: q.first,
            descendants: q.descendants,
            propertyName: propertyName,
            read: isPresent(q.read) ? this.getTokenMetadata(q.read) : null
        });
    }
}
/** @nocollapse */
CompileMetadataResolver.decorators = [
    { type: Injectable },
];
/** @nocollapse */
CompileMetadataResolver.ctorParameters = [
    { type: DirectiveResolver, },
    { type: PipeResolver, },
    { type: ViewResolver, },
    { type: CompilerConfig, },
    { type: ReflectorReader, },
];
function flattenDirectives(view, platformDirectives) {
    let directives = [];
    if (isPresent(platformDirectives)) {
        flattenArray(platformDirectives, directives);
    }
    if (isPresent(view.directives)) {
        flattenArray(view.directives, directives);
    }
    return directives;
}
function flattenPipes(view, platformPipes) {
    let pipes = [];
    if (isPresent(platformPipes)) {
        flattenArray(platformPipes, pipes);
    }
    if (isPresent(view.pipes)) {
        flattenArray(view.pipes, pipes);
    }
    return pipes;
}
function flattenArray(tree, out = []) {
    for (var i = 0; i < tree.length; i++) {
        var item = resolveForwardRef(tree[i]);
        if (isArray(item)) {
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
        if (isBlank(flat[i])) {
            errMsg = flat.map(provider => isBlank(provider) ? '?' : stringify(provider)).join(', ');
            throw new BaseException(`One or more of ${providersType} for "${stringify(directiveType)}" were not defined: [${errMsg}].`);
        }
    }
    return providersTree;
}
function isStaticType(value) {
    return isStringMap(value) && isPresent(value['name']) && isPresent(value['filePath']);
}
function isValidType(value) {
    return isStaticType(value) || (value instanceof Type);
}
function staticTypeModuleUrl(value) {
    return isStaticType(value) ? value['filePath'] : null;
}
function componentModuleUrl(reflector, type, cmpMetadata) {
    if (isStaticType(type)) {
        return staticTypeModuleUrl(type);
    }
    if (isPresent(cmpMetadata.moduleId)) {
        var moduleId = cmpMetadata.moduleId;
        var scheme = getUrlScheme(moduleId);
        return isPresent(scheme) && scheme.length > 0 ? moduleId :
            `package:${moduleId}${MODULE_SUFFIX}`;
    }
    return reflector.importUri(type);
}
// Only fill CompileIdentifierMetadata.runtime if needed...
function convertToCompileValue(value) {
    return visitValue(value, new _CompileValueConverter(), null);
}
class _CompileValueConverter extends ValueTransformer {
    visitOther(value, context) {
        if (isStaticType(value)) {
            return new cpl.CompileIdentifierMetadata({ name: value['name'], moduleUrl: staticTypeModuleUrl(value) });
        }
        else {
            return new cpl.CompileIdentifierMetadata({ runtime: value });
        }
    }
}
//# sourceMappingURL=metadata_resolver.js.map