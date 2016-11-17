/**
 * @license
 * Copyright Google Inc. All Rights Reserved.
 *
 * Use of this source code is governed by an MIT-style license that can be
 * found in the LICENSE file at https://angular.io/license
 */
"use strict";
function __export(m) {
    for (var p in m) if (!exports.hasOwnProperty(p)) exports[p] = m[p];
}
var core_1 = require('@angular/core');
__export(require('./template_ast'));
var template_parser_1 = require('./template_parser');
exports.TEMPLATE_TRANSFORMS = template_parser_1.TEMPLATE_TRANSFORMS;
var config_1 = require('./config');
exports.CompilerConfig = config_1.CompilerConfig;
exports.RenderTypes = config_1.RenderTypes;
__export(require('./compile_metadata'));
__export(require('./offline_compiler'));
var runtime_compiler_1 = require('./runtime_compiler');
exports.RuntimeCompiler = runtime_compiler_1.RuntimeCompiler;
__export(require('./url_resolver'));
__export(require('./xhr'));
var view_resolver_1 = require('./view_resolver');
exports.ViewResolver = view_resolver_1.ViewResolver;
var directive_resolver_1 = require('./directive_resolver');
exports.DirectiveResolver = directive_resolver_1.DirectiveResolver;
var pipe_resolver_1 = require('./pipe_resolver');
exports.PipeResolver = pipe_resolver_1.PipeResolver;
var template_parser_2 = require('./template_parser');
var html_parser_1 = require('./html_parser');
var directive_normalizer_1 = require('./directive_normalizer');
var metadata_resolver_1 = require('./metadata_resolver');
var style_compiler_1 = require('./style_compiler');
var view_compiler_1 = require('./view_compiler/view_compiler');
var config_2 = require('./config');
var runtime_compiler_2 = require('./runtime_compiler');
var element_schema_registry_1 = require('./schema/element_schema_registry');
var dom_element_schema_registry_1 = require('./schema/dom_element_schema_registry');
var url_resolver_2 = require('./url_resolver');
var parser_1 = require('./expression_parser/parser');
var lexer_1 = require('./expression_parser/lexer');
var view_resolver_2 = require('./view_resolver');
var directive_resolver_2 = require('./directive_resolver');
var pipe_resolver_2 = require('./pipe_resolver');
/**
 * A set of providers that provide `RuntimeCompiler` and its dependencies to use for
 * template compilation.
 */
exports.COMPILER_PROVIDERS = 
/*@ts2dart_const*/ [
    lexer_1.Lexer, parser_1.Parser, html_parser_1.HtmlParser, template_parser_2.TemplateParser, directive_normalizer_1.DirectiveNormalizer, metadata_resolver_1.CompileMetadataResolver,
    url_resolver_2.DEFAULT_PACKAGE_URL_PROVIDER, style_compiler_1.StyleCompiler, view_compiler_1.ViewCompiler,
    /*@ts2dart_Provider*/ { provide: config_2.CompilerConfig, useValue: new config_2.CompilerConfig() },
    runtime_compiler_2.RuntimeCompiler,
    /*@ts2dart_Provider*/ { provide: core_1.ComponentResolver, useExisting: runtime_compiler_2.RuntimeCompiler },
    /*@ts2dart_Provider*/ { provide: core_1.Compiler, useExisting: runtime_compiler_2.RuntimeCompiler },
    dom_element_schema_registry_1.DomElementSchemaRegistry,
    /*@ts2dart_Provider*/ { provide: element_schema_registry_1.ElementSchemaRegistry, useExisting: dom_element_schema_registry_1.DomElementSchemaRegistry },
    url_resolver_2.UrlResolver, view_resolver_2.ViewResolver, directive_resolver_2.DirectiveResolver, pipe_resolver_2.PipeResolver
];
//# sourceMappingURL=compiler.js.map