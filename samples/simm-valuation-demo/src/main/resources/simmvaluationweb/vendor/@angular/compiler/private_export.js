/**
 * @license
 * Copyright Google Inc. All Rights Reserved.
 *
 * Use of this source code is governed by an MIT-style license that can be
 * found in the LICENSE file at https://angular.io/license
 */
"use strict";
var directive_normalizer = require('./src/directive_normalizer');
var lexer = require('./src/expression_parser/lexer');
var parser = require('./src/expression_parser/parser');
var html_parser = require('./src/html_parser');
var i18n_html_parser = require('./src/i18n/i18n_html_parser');
var i18n_message = require('./src/i18n/message');
var i18n_extractor = require('./src/i18n/message_extractor');
var xmb_serializer = require('./src/i18n/xmb_serializer');
var metadata_resolver = require('./src/metadata_resolver');
var path_util = require('./src/output/path_util');
var ts_emitter = require('./src/output/ts_emitter');
var parse_util = require('./src/parse_util');
var dom_element_schema_registry = require('./src/schema/dom_element_schema_registry');
var selector = require('./src/selector');
var style_compiler = require('./src/style_compiler');
var template_parser = require('./src/template_parser');
var view_compiler = require('./src/view_compiler/view_compiler');
var __compiler_private__;
(function (__compiler_private__) {
    __compiler_private__.SelectorMatcher = selector.SelectorMatcher;
    __compiler_private__.CssSelector = selector.CssSelector;
    __compiler_private__.AssetUrl = path_util.AssetUrl;
    __compiler_private__.ImportGenerator = path_util.ImportGenerator;
    __compiler_private__.CompileMetadataResolver = metadata_resolver.CompileMetadataResolver;
    __compiler_private__.HtmlParser = html_parser.HtmlParser;
    __compiler_private__.I18nHtmlParser = i18n_html_parser.I18nHtmlParser;
    __compiler_private__.ExtractionResult = i18n_extractor.ExtractionResult;
    __compiler_private__.Message = i18n_message.Message;
    __compiler_private__.MessageExtractor = i18n_extractor.MessageExtractor;
    __compiler_private__.removeDuplicates = i18n_extractor.removeDuplicates;
    __compiler_private__.serializeXmb = xmb_serializer.serializeXmb;
    __compiler_private__.deserializeXmb = xmb_serializer.deserializeXmb;
    __compiler_private__.DirectiveNormalizer = directive_normalizer.DirectiveNormalizer;
    __compiler_private__.Lexer = lexer.Lexer;
    __compiler_private__.Parser = parser.Parser;
    __compiler_private__.ParseLocation = parse_util.ParseLocation;
    __compiler_private__.ParseError = parse_util.ParseError;
    __compiler_private__.ParseErrorLevel = parse_util.ParseErrorLevel;
    __compiler_private__.ParseSourceFile = parse_util.ParseSourceFile;
    __compiler_private__.ParseSourceSpan = parse_util.ParseSourceSpan;
    __compiler_private__.TemplateParser = template_parser.TemplateParser;
    __compiler_private__.DomElementSchemaRegistry = dom_element_schema_registry.DomElementSchemaRegistry;
    __compiler_private__.StyleCompiler = style_compiler.StyleCompiler;
    __compiler_private__.ViewCompiler = view_compiler.ViewCompiler;
    __compiler_private__.TypeScriptEmitter = ts_emitter.TypeScriptEmitter;
})(__compiler_private__ = exports.__compiler_private__ || (exports.__compiler_private__ = {}));
//# sourceMappingURL=private_export.js.map