/**
 * @license
 * Copyright Google Inc. All Rights Reserved.
 *
 * Use of this source code is governed by an MIT-style license that can be
 * found in the LICENSE file at https://angular.io/license
 */
"use strict";
var core_1 = require('@angular/core');
var collection_1 = require('../src/facade/collection');
var exceptions_1 = require('../src/facade/exceptions');
var lang_1 = require('../src/facade/lang');
var compile_metadata_1 = require('./compile_metadata');
var config_1 = require('./config');
var html_ast_1 = require('./html_ast');
var html_parser_1 = require('./html_parser');
var style_url_resolver_1 = require('./style_url_resolver');
var template_preparser_1 = require('./template_preparser');
var url_resolver_1 = require('./url_resolver');
var xhr_1 = require('./xhr');
var NormalizeDirectiveResult = (function () {
    function NormalizeDirectiveResult(syncResult, asyncResult) {
        this.syncResult = syncResult;
        this.asyncResult = asyncResult;
    }
    return NormalizeDirectiveResult;
}());
exports.NormalizeDirectiveResult = NormalizeDirectiveResult;
var DirectiveNormalizer = (function () {
    function DirectiveNormalizer(_xhr, _urlResolver, _htmlParser, _config) {
        this._xhr = _xhr;
        this._urlResolver = _urlResolver;
        this._htmlParser = _htmlParser;
        this._config = _config;
        this._xhrCache = new Map();
    }
    DirectiveNormalizer.prototype.clearCache = function () { this._xhrCache.clear(); };
    DirectiveNormalizer.prototype.clearCacheFor = function (normalizedDirective) {
        var _this = this;
        if (!normalizedDirective.isComponent) {
            return;
        }
        this._xhrCache.delete(normalizedDirective.template.templateUrl);
        normalizedDirective.template.externalStylesheets.forEach(function (stylesheet) { _this._xhrCache.delete(stylesheet.moduleUrl); });
    };
    DirectiveNormalizer.prototype._fetch = function (url) {
        var result = this._xhrCache.get(url);
        if (!result) {
            result = this._xhr.get(url);
            this._xhrCache.set(url, result);
        }
        return result;
    };
    DirectiveNormalizer.prototype.normalizeDirective = function (directive) {
        var _this = this;
        if (!directive.isComponent) {
            // For non components there is nothing to be normalized yet.
            return new NormalizeDirectiveResult(directive, Promise.resolve(directive));
        }
        var normalizedTemplateSync = null;
        var normalizedTemplateAsync;
        if (lang_1.isPresent(directive.template.template)) {
            normalizedTemplateSync = this.normalizeTemplateSync(directive.type, directive.template);
            normalizedTemplateAsync = Promise.resolve(normalizedTemplateSync);
        }
        else if (directive.template.templateUrl) {
            normalizedTemplateAsync = this.normalizeTemplateAsync(directive.type, directive.template);
        }
        else {
            throw new exceptions_1.BaseException("No template specified for component " + directive.type.name);
        }
        if (normalizedTemplateSync && normalizedTemplateSync.styleUrls.length === 0) {
            // sync case
            var normalizedDirective = _cloneDirectiveWithTemplate(directive, normalizedTemplateSync);
            return new NormalizeDirectiveResult(normalizedDirective, Promise.resolve(normalizedDirective));
        }
        else {
            // async case
            return new NormalizeDirectiveResult(null, normalizedTemplateAsync
                .then(function (normalizedTemplate) { return _this.normalizeExternalStylesheets(normalizedTemplate); })
                .then(function (normalizedTemplate) {
                return _cloneDirectiveWithTemplate(directive, normalizedTemplate);
            }));
        }
    };
    DirectiveNormalizer.prototype.normalizeTemplateSync = function (directiveType, template) {
        return this.normalizeLoadedTemplate(directiveType, template, template.template, directiveType.moduleUrl);
    };
    DirectiveNormalizer.prototype.normalizeTemplateAsync = function (directiveType, template) {
        var _this = this;
        var templateUrl = this._urlResolver.resolve(directiveType.moduleUrl, template.templateUrl);
        return this._fetch(templateUrl)
            .then(function (value) { return _this.normalizeLoadedTemplate(directiveType, template, value, templateUrl); });
    };
    DirectiveNormalizer.prototype.normalizeLoadedTemplate = function (directiveType, templateMeta, template, templateAbsUrl) {
        var rootNodesAndErrors = this._htmlParser.parse(template, directiveType.name);
        if (rootNodesAndErrors.errors.length > 0) {
            var errorString = rootNodesAndErrors.errors.join('\n');
            throw new exceptions_1.BaseException("Template parse errors:\n" + errorString);
        }
        var templateMetadataStyles = this.normalizeStylesheet(new compile_metadata_1.CompileStylesheetMetadata({
            styles: templateMeta.styles,
            styleUrls: templateMeta.styleUrls,
            moduleUrl: directiveType.moduleUrl
        }));
        var visitor = new TemplatePreparseVisitor();
        html_ast_1.htmlVisitAll(visitor, rootNodesAndErrors.rootNodes);
        var templateStyles = this.normalizeStylesheet(new compile_metadata_1.CompileStylesheetMetadata({ styles: visitor.styles, styleUrls: visitor.styleUrls, moduleUrl: templateAbsUrl }));
        var allStyles = templateMetadataStyles.styles.concat(templateStyles.styles);
        var allStyleUrls = templateMetadataStyles.styleUrls.concat(templateStyles.styleUrls);
        var encapsulation = templateMeta.encapsulation;
        if (lang_1.isBlank(encapsulation)) {
            encapsulation = this._config.defaultEncapsulation;
        }
        if (encapsulation === core_1.ViewEncapsulation.Emulated && allStyles.length === 0 &&
            allStyleUrls.length === 0) {
            encapsulation = core_1.ViewEncapsulation.None;
        }
        return new compile_metadata_1.CompileTemplateMetadata({
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
    };
    DirectiveNormalizer.prototype.normalizeExternalStylesheets = function (templateMeta) {
        return this._loadMissingExternalStylesheets(templateMeta.styleUrls)
            .then(function (externalStylesheets) { return new compile_metadata_1.CompileTemplateMetadata({
            encapsulation: templateMeta.encapsulation,
            template: templateMeta.template,
            templateUrl: templateMeta.templateUrl,
            styles: templateMeta.styles,
            styleUrls: templateMeta.styleUrls,
            externalStylesheets: externalStylesheets,
            ngContentSelectors: templateMeta.ngContentSelectors,
            animations: templateMeta.animations,
            interpolation: templateMeta.interpolation
        }); });
    };
    DirectiveNormalizer.prototype._loadMissingExternalStylesheets = function (styleUrls, loadedStylesheets) {
        var _this = this;
        if (loadedStylesheets === void 0) { loadedStylesheets = new Map(); }
        return Promise
            .all(styleUrls.filter(function (styleUrl) { return !loadedStylesheets.has(styleUrl); })
            .map(function (styleUrl) { return _this._fetch(styleUrl).then(function (loadedStyle) {
            var stylesheet = _this.normalizeStylesheet(new compile_metadata_1.CompileStylesheetMetadata({ styles: [loadedStyle], moduleUrl: styleUrl }));
            loadedStylesheets.set(styleUrl, stylesheet);
            return _this._loadMissingExternalStylesheets(stylesheet.styleUrls, loadedStylesheets);
        }); }))
            .then(function (_) { return collection_1.MapWrapper.values(loadedStylesheets); });
    };
    DirectiveNormalizer.prototype.normalizeStylesheet = function (stylesheet) {
        var _this = this;
        var allStyleUrls = stylesheet.styleUrls.filter(style_url_resolver_1.isStyleUrlResolvable)
            .map(function (url) { return _this._urlResolver.resolve(stylesheet.moduleUrl, url); });
        var allStyles = stylesheet.styles.map(function (style) {
            var styleWithImports = style_url_resolver_1.extractStyleUrls(_this._urlResolver, stylesheet.moduleUrl, style);
            allStyleUrls.push.apply(allStyleUrls, styleWithImports.styleUrls);
            return styleWithImports.style;
        });
        return new compile_metadata_1.CompileStylesheetMetadata({ styles: allStyles, styleUrls: allStyleUrls, moduleUrl: stylesheet.moduleUrl });
    };
    /** @nocollapse */
    DirectiveNormalizer.decorators = [
        { type: core_1.Injectable },
    ];
    /** @nocollapse */
    DirectiveNormalizer.ctorParameters = [
        { type: xhr_1.XHR, },
        { type: url_resolver_1.UrlResolver, },
        { type: html_parser_1.HtmlParser, },
        { type: config_1.CompilerConfig, },
    ];
    return DirectiveNormalizer;
}());
exports.DirectiveNormalizer = DirectiveNormalizer;
var TemplatePreparseVisitor = (function () {
    function TemplatePreparseVisitor() {
        this.ngContentSelectors = [];
        this.styles = [];
        this.styleUrls = [];
        this.ngNonBindableStackCount = 0;
    }
    TemplatePreparseVisitor.prototype.visitElement = function (ast, context) {
        var preparsedElement = template_preparser_1.preparseElement(ast);
        switch (preparsedElement.type) {
            case template_preparser_1.PreparsedElementType.NG_CONTENT:
                if (this.ngNonBindableStackCount === 0) {
                    this.ngContentSelectors.push(preparsedElement.selectAttr);
                }
                break;
            case template_preparser_1.PreparsedElementType.STYLE:
                var textContent = '';
                ast.children.forEach(function (child) {
                    if (child instanceof html_ast_1.HtmlTextAst) {
                        textContent += child.value;
                    }
                });
                this.styles.push(textContent);
                break;
            case template_preparser_1.PreparsedElementType.STYLESHEET:
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
        html_ast_1.htmlVisitAll(this, ast.children);
        if (preparsedElement.nonBindable) {
            this.ngNonBindableStackCount--;
        }
        return null;
    };
    TemplatePreparseVisitor.prototype.visitComment = function (ast, context) { return null; };
    TemplatePreparseVisitor.prototype.visitAttr = function (ast, context) { return null; };
    TemplatePreparseVisitor.prototype.visitText = function (ast, context) { return null; };
    TemplatePreparseVisitor.prototype.visitExpansion = function (ast, context) { return null; };
    TemplatePreparseVisitor.prototype.visitExpansionCase = function (ast, context) { return null; };
    return TemplatePreparseVisitor;
}());
function _cloneDirectiveWithTemplate(directive, template) {
    return new compile_metadata_1.CompileDirectiveMetadata({
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