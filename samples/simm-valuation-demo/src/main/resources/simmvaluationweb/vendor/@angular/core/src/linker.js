/**
 * @license
 * Copyright Google Inc. All Rights Reserved.
 *
 * Use of this source code is governed by an MIT-style license that can be
 * found in the LICENSE file at https://angular.io/license
 */
"use strict";
// Public API for compiler
var compiler_1 = require('./linker/compiler');
exports.Compiler = compiler_1.Compiler;
var component_factory_1 = require('./linker/component_factory');
exports.ComponentFactory = component_factory_1.ComponentFactory;
exports.ComponentRef = component_factory_1.ComponentRef;
var component_factory_resolver_1 = require('./linker/component_factory_resolver');
exports.ComponentFactoryResolver = component_factory_resolver_1.ComponentFactoryResolver;
exports.NoComponentFactoryError = component_factory_resolver_1.NoComponentFactoryError;
var component_resolver_1 = require('./linker/component_resolver');
exports.ComponentResolver = component_resolver_1.ComponentResolver;
var dynamic_component_loader_1 = require('./linker/dynamic_component_loader');
exports.DynamicComponentLoader = dynamic_component_loader_1.DynamicComponentLoader;
var element_ref_1 = require('./linker/element_ref');
exports.ElementRef = element_ref_1.ElementRef;
var exceptions_1 = require('./linker/exceptions');
exports.ExpressionChangedAfterItHasBeenCheckedException = exceptions_1.ExpressionChangedAfterItHasBeenCheckedException;
var query_list_1 = require('./linker/query_list');
exports.QueryList = query_list_1.QueryList;
var systemjs_component_resolver_1 = require('./linker/systemjs_component_resolver');
exports.SystemJsCmpFactoryResolver = systemjs_component_resolver_1.SystemJsCmpFactoryResolver;
exports.SystemJsComponentResolver = systemjs_component_resolver_1.SystemJsComponentResolver;
var template_ref_1 = require('./linker/template_ref');
exports.TemplateRef = template_ref_1.TemplateRef;
var view_container_ref_1 = require('./linker/view_container_ref');
exports.ViewContainerRef = view_container_ref_1.ViewContainerRef;
var view_ref_1 = require('./linker/view_ref');
exports.EmbeddedViewRef = view_ref_1.EmbeddedViewRef;
exports.ViewRef = view_ref_1.ViewRef;
//# sourceMappingURL=linker.js.map