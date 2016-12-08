/**
 * @license
 * Copyright Google Inc. All Rights Reserved.
 *
 * Use of this source code is governed by an MIT-style license that can be
 * found in the LICENSE file at https://angular.io/license
 */
import { Injectable, ViewEncapsulation } from '@angular/core';
import { MapWrapper } from '../src/facade/collection';
import { BaseException } from '../src/facade/exceptions';
import { isBlank, isPresent } from '../src/facade/lang';
import { CompileDirectiveMetadata, CompileStylesheetMetadata, CompileTemplateMetadata } from './compile_metadata';
import { CompilerConfig } from './config';
import { HtmlTextAst, htmlVisitAll } from './html_ast';
import { HtmlParser } from './html_parser';
import { extractStyleUrls, isStyleUrlResolvable } from './style_url_resolver';
import { PreparsedElementType, preparseElement } from './template_preparser';
import { UrlResolver } from './url_resolver';
import { XHR } from './xhr';
export class NormalizeDirectiveResult {
    constructor(syncResult, asyncResult) {
        this.syncResult = syncResult;
        this.asyncResult = asyncResult;
    }
}
export class DirectiveNormalizer {
    constructor(_xhr, _urlResolver, _htmlParser, _config) {
        this._xhr = _xhr;
        this._urlResolver = _urlResolver;
        this._htmlParser = _htmlParser;
        this._config = _config;
        this._xhrCache = new Map();
    }
    clearCache() { this._xhrCache.clear(); }
    clearCacheFor(normalizedDirective) {
        if (!normalizedDirective.isComponent) {
            return;
        }
        this._xhrCache.delete(normalizedDirective.template.templateUrl);
        normalizedDirective.template.externalStylesheets.forEach((stylesheet) => { this._xhrCache.delete(stylesheet.moduleUrl); });
    }
    _fetch(url) {
        var result = this._xhrCache.get(url);
        if (!result) {
            result = this._xhr.get(url);
            this._xhrCache.set(url, result);
        }
        return result;
    }
    normalizeDirective(directive) {
        if (!directive.isComponent) {
            // For non components there is nothing to be normalized yet.
            return new NormalizeDirectiveResult(directive, Promise.resolve(directive));
        }
        let normalizedTemplateSync = null;
        let normalizedTemplateAsync;
        if (isPresent(directive.template.template)) {
            normalizedTemplateSync = this.normalizeTemplateSync(directive.type, directive.template);
            normalizedTemplateAsync = Promise.resolve(normalizedTemplateSync);
        }
        else if (directive.template.templateUrl) {
            normalizedTemplateAsync = this.normalizeTemplateAsync(directive.type, directive.template);
        }
        else {
            throw new BaseException(`No template specified for component ${directive.type.name}`);
        }
        if (normalizedTemplateSync && normalizedTemplateSync.styleUrls.length === 0) {
            // sync case
            let normalizedDirective = _cloneDirectiveWithTemplate(directive, normalizedTemplateSync);
            return new NormalizeDirectiveResult(normalizedDirective, Promise.resolve(normalizedDirective));
        }
        else {
            // async case
            return new NormalizeDirectiveResult(null, normalizedTemplateAsync
                .then((normalizedTemplate) => this.normalizeExternalStylesheets(normalizedTemplate))
                .then((normalizedTemplate) => _cloneDirectiveWithTemplate(directive, normalizedTemplate)));
        }
    }
    normalizeTemplateSync(directiveType, template) {
        return this.normalizeLoadedTemplate(directiveType, template, template.template, directiveType.moduleUrl);
    }
    normalizeTemplateAsync(directiveType, template) {
        let templateUrl = this._urlResolver.resolve(directiveType.moduleUrl, template.templateUrl);
        return this._fetch(templateUrl)
            .then((value) => this.normalizeLoadedTemplate(directiveType, template, value, templateUrl));
    }
    normalizeLoadedTemplate(directiveType, templateMeta, template, templateAbsUrl) {
        var rootNodesAndErrors = this._htmlParser.parse(template, directiveType.name);
        if (rootNodesAndErrors.errors.length > 0) {
            var errorString = rootNodesAndErrors.errors.join('\n');
            throw new BaseException(`Template parse errors:\n${errorString}`);
        }
        var templateMetadataStyles = this.normalizeStylesheet(new CompileStylesheetMetadata({
            styles: templateMeta.styles,
            styleUrls: templateMeta.styleUrls,
            moduleUrl: directiveType.moduleUrl
        }));
        var visitor = new TemplatePreparseVisitor();
        htmlVisitAll(visitor, rootNodesAndErrors.rootNodes);
        var templateStyles = this.normalizeStylesheet(new CompileStylesheetMetadata({ styles: visitor.styles, styleUrls: visitor.styleUrls, moduleUrl: templateAbsUrl }));
        var allStyles = templateMetadataStyles.styles.concat(templateStyles.styles);
        var allStyleUrls = templateMetadataStyles.styleUrls.concat(templateStyles.styleUrls);
        var encapsulation = templateMeta.encapsulation;
        if (isBlank(encapsulation)) {
            encapsulation = this._config.defaultEncapsulation;
        }
        if (encapsulation === ViewEncapsulation.Emulated && allStyles.length === 0 &&
            allStyleUrls.length === 0) {
            encapsulation = ViewEncapsulation.None;
        }
        return new CompileTemplateMetadata({
            encapsulation: encapsulation,
            template: template,
            templateUrl: templateAbsUrl,
            styles: allStyles,
            styleUrls: allStyleUrls,
            externalStylesheets: templateMeta.externalStylesheets,
            ngContentSelectors: visitor.ngContentSelectors,
            animations: templateMeta.animations,
            interpolation: templateMeta.interpolation
        });
    }
    normalizeExternalStylesheets(templateMeta) {
        return this._loadMissingExternalStylesheets(templateMeta.styleUrls)
            .then((externalStylesheets) => new CompileTemplateMetadata({
            encapsulation: templateMeta.encapsulation,
            template: templateMeta.template,
            templateUrl: templateMeta.templateUrl,
            styles: templateMeta.styles,
            styleUrls: templateMeta.styleUrls,
            externalStylesheets: externalStylesheets,
            ngContentSelectors: templateMeta.ngContentSelectors,
            animations: templateMeta.animations,
            interpolation: templateMeta.interpolation
        }));
    }
    _loadMissingExternalStylesheets(styleUrls, loadedStylesheets = new Map()) {
        return Promise
            .all(styleUrls.filter((styleUrl) => !loadedStylesheets.has(styleUrl))
            .map(styleUrl => this._fetch(styleUrl).then((loadedStyle) => {
            var stylesheet = this.normalizeStylesheet(new CompileStylesheetMetadata({ styles: [loadedStyle], moduleUrl: styleUrl }));
            loadedStylesheets.set(styleUrl, stylesheet);
            return this._loadMissingExternalStylesheets(stylesheet.styleUrls, loadedStylesheets);
        })))
            .then((_) => MapWrapper.values(loadedStylesheets));
    }
    normalizeStylesheet(stylesheet) {
        var allStyleUrls = stylesheet.styleUrls.filter(isStyleUrlResolvable)
            .map(url => this._urlResolver.resolve(stylesheet.moduleUrl, url));
        var allStyles = stylesheet.styles.map(style => {
            var styleWithImports = extractStyleUrls(this._urlResolver, stylesheet.moduleUrl, style);
            allStyleUrls.push(...styleWithImports.styleUrls);
            return styleWithImports.style;
        });
        return new CompileStylesheetMetadata({ styles: allStyles, styleUrls: allStyleUrls, moduleUrl: stylesheet.moduleUrl });
    }
}
/** @nocollapse */
DirectiveNormalizer.decorators = [
    { type: Injectable },
];
/** @nocollapse */
DirectiveNormalizer.ctorParameters = [
    { type: XHR, },
    { type: UrlResolver, },
    { type: HtmlParser, },
    { type: CompilerConfig, },
];
class TemplatePreparseVisitor {
    constructor() {
        this.ngContentSelectors = [];
        this.styles = [];
        this.styleUrls = [];
        this.ngNonBindableStackCount = 0;
    }
    visitElement(ast, context) {
        var preparsedElement = preparseElement(ast);
        switch (preparsedElement.type) {
            case PreparsedElementType.NG_CONTENT:
                if (this.ngNonBindableStackCount === 0) {
                    this.ngContentSelectors.push(preparsedElement.selectAttr);
                }
                break;
            case PreparsedElementType.STYLE:
                var textContent = '';
                ast.children.forEach(child => {
                    if (child instanceof HtmlTextAst) {
                        textContent += child.value;
                    }
                });
                this.styles.push(textContent);
                break;
            case PreparsedElementType.STYLESHEET:
                this.styleUrls.push(preparsedElement.hrefAttr);
                break;
            default:
                // DDC reports this as error. See:
                // https://github.com/dart-lang/dev_compiler/issues/428
                break;
        }
        if (preparsedElement.nonBindable) {
            this.ngNonBindableStackCount++;
        }
        htmlVisitAll(this, ast.children);
        if (preparsedElement.nonBindable) {
            this.ngNonBindableStackCount--;
        }
        return null;
    }
    visitComment(ast, context) { return null; }
    visitAttr(ast, context) { return null; }
    visitText(ast, context) { return null; }
    visitExpansion(ast, context) { return null; }
    visitExpansionCase(ast, context) { return null; }
}
function _cloneDirectiveWithTemplate(directive, template) {
    return new CompileDirectiveMetadata({
        type: directive.type,
        isComponent: directive.isComponent,
        selector: directive.selector,
        exportAs: directive.exportAs,
        changeDetection: directive.changeDetection,
        inputs: directive.inputs,
        outputs: directive.outputs,
        hostListeners: directive.hostListeners,
        hostProperties: directive.hostProperties,
        hostAttributes: directive.hostAttributes,
        lifecycleHooks: directive.lifecycleHooks,
        providers: directive.providers,
        viewProviders: directive.viewProviders,
        queries: directive.queries,
        viewQueries: directive.viewQueries,
        precompile: directive.precompile,
        template: template
    });
}
//# sourceMappingURL=directive_normalizer.js.map