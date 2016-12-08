/**
 * @license
 * Copyright Google Inc. All Rights Reserved.
 *
 * Use of this source code is governed by an MIT-style license that can be
 * found in the LICENSE file at https://angular.io/license
 */
import * as directive_normalizer from './src/directive_normalizer';
import * as lexer from './src/expression_parser/lexer';
import * as parser from './src/expression_parser/parser';
import * as html_parser from './src/html_parser';
import * as i18n_html_parser from './src/i18n/i18n_html_parser';
import * as i18n_message from './src/i18n/message';
import * as i18n_extractor from './src/i18n/message_extractor';
import * as xmb_serializer from './src/i18n/xmb_serializer';
import * as metadata_resolver from './src/metadata_resolver';
import * as path_util from './src/output/path_util';
import * as ts_emitter from './src/output/ts_emitter';
import * as parse_util from './src/parse_util';
import * as dom_element_schema_registry from './src/schema/dom_element_schema_registry';
import * as selector from './src/selector';
import * as style_compiler from './src/style_compiler';
import * as template_parser from './src/template_parser';
import * as view_compiler from './src/view_compiler/view_compiler';
export var __compiler_private__;
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
})(__compiler_private__ || (__compiler_private__ = {}));
//# sourceMappingURL=private_export.js.map