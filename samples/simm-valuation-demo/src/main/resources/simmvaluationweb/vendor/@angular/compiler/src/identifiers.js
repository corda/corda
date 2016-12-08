/**
 * @license
 * Copyright Google Inc. All Rights Reserved.
 *
 * Use of this source code is governed by an MIT-style license that can be
 * found in the LICENSE file at https://angular.io/license
 */
"use strict";
var core_1 = require('@angular/core');
var core_private_1 = require('../core_private');
var compile_metadata_1 = require('./compile_metadata');
var util_1 = require('./util');
var APP_VIEW_MODULE_URL = util_1.assetUrl('core', 'linker/view');
var VIEW_UTILS_MODULE_URL = util_1.assetUrl('core', 'linker/view_utils');
var CD_MODULE_URL = util_1.assetUrl('core', 'change_detection/change_detection');
// Reassign the imports to different variables so we can
// define static variables with the name of the import.
// (only needed for Dart).
var impViewUtils = core_private_1.ViewUtils;
var impAppView = core_private_1.AppView;
var impDebugAppView = core_private_1.DebugAppView;
var impDebugContext = core_private_1.DebugContext;
var impAppElement = core_private_1.AppElement;
var impElementRef = core_1.ElementRef;
var impViewContainerRef = core_1.ViewContainerRef;
var impChangeDetectorRef = core_1.ChangeDetectorRef;
var impRenderComponentType = core_1.RenderComponentType;
var impQueryList = core_1.QueryList;
var impTemplateRef = core_1.TemplateRef;
var impTemplateRef_ = core_private_1.TemplateRef_;
var impValueUnwrapper = core_private_1.ValueUnwrapper;
var impInjector = core_1.Injector;
var impViewEncapsulation = core_1.ViewEncapsulation;
var impViewType = core_private_1.ViewType;
var impChangeDetectionStrategy = core_1.ChangeDetectionStrategy;
var impStaticNodeDebugInfo = core_private_1.StaticNodeDebugInfo;
var impRenderer = core_1.Renderer;
var impSimpleChange = core_1.SimpleChange;
var impUninitialized = core_private_1.uninitialized;
var impChangeDetectorStatus = core_private_1.ChangeDetectorStatus;
var impFlattenNestedViewRenderNodes = core_private_1.flattenNestedViewRenderNodes;
var impDevModeEqual = core_private_1.devModeEqual;
var impInterpolate = core_private_1.interpolate;
var impCheckBinding = core_private_1.checkBinding;
var impCastByValue = core_private_1.castByValue;
var impEMPTY_ARRAY = core_private_1.EMPTY_ARRAY;
var impEMPTY_MAP = core_private_1.EMPTY_MAP;
var impAnimationGroupPlayer = core_private_1.AnimationGroupPlayer;
var impAnimationSequencePlayer = core_private_1.AnimationSequencePlayer;
var impAnimationKeyframe = core_private_1.AnimationKeyframe;
var impAnimationStyles = core_private_1.AnimationStyles;
var impNoOpAnimationPlayer = core_private_1.NoOpAnimationPlayer;
var ANIMATION_STYLE_UTIL_ASSET_URL = util_1.assetUrl('core', 'animation/animation_style_util');
var Identifiers = (function () {
    function Identifiers() {
    }
    Identifiers.ViewUtils = new compile_metadata_1.CompileIdentifierMetadata({ name: 'ViewUtils', moduleUrl: util_1.assetUrl('core', 'linker/view_utils'), runtime: impViewUtils });
    Identifiers.AppView = new compile_metadata_1.CompileIdentifierMetadata({ name: 'AppView', moduleUrl: APP_VIEW_MODULE_URL, runtime: impAppView });
    Identifiers.DebugAppView = new compile_metadata_1.CompileIdentifierMetadata({ name: 'DebugAppView', moduleUrl: APP_VIEW_MODULE_URL, runtime: impDebugAppView });
    Identifiers.AppElement = new compile_metadata_1.CompileIdentifierMetadata({ name: 'AppElement', moduleUrl: util_1.assetUrl('core', 'linker/element'), runtime: impAppElement });
    Identifiers.ElementRef = new compile_metadata_1.CompileIdentifierMetadata({
        name: 'ElementRef',
        moduleUrl: util_1.assetUrl('core', 'linker/element_ref'),
        runtime: impElementRef
    });
    Identifiers.ViewContainerRef = new compile_metadata_1.CompileIdentifierMetadata({
        name: 'ViewContainerRef',
        moduleUrl: util_1.assetUrl('core', 'linker/view_container_ref'),
        runtime: impViewContainerRef
    });
    Identifiers.ChangeDetectorRef = new compile_metadata_1.CompileIdentifierMetadata({
        name: 'ChangeDetectorRef',
        moduleUrl: util_1.assetUrl('core', 'change_detection/change_detector_ref'),
        runtime: impChangeDetectorRef
    });
    Identifiers.RenderComponentType = new compile_metadata_1.CompileIdentifierMetadata({
        name: 'RenderComponentType',
        moduleUrl: util_1.assetUrl('core', 'render/api'),
        runtime: impRenderComponentType
    });
    Identifiers.QueryList = new compile_metadata_1.CompileIdentifierMetadata({ name: 'QueryList', moduleUrl: util_1.assetUrl('core', 'linker/query_list'), runtime: impQueryList });
    Identifiers.TemplateRef = new compile_metadata_1.CompileIdentifierMetadata({
        name: 'TemplateRef',
        moduleUrl: util_1.assetUrl('core', 'linker/template_ref'),
        runtime: impTemplateRef
    });
    Identifiers.TemplateRef_ = new compile_metadata_1.CompileIdentifierMetadata({
        name: 'TemplateRef_',
        moduleUrl: util_1.assetUrl('core', 'linker/template_ref'),
        runtime: impTemplateRef_
    });
    Identifiers.CodegenComponentFactoryResolver = new compile_metadata_1.CompileIdentifierMetadata({
        name: 'CodegenComponentFactoryResolver',
        moduleUrl: util_1.assetUrl('core', 'linker/component_factory_resolver'),
        runtime: core_private_1.CodegenComponentFactoryResolver
    });
    Identifiers.ComponentFactoryResolver = new compile_metadata_1.CompileIdentifierMetadata({
        name: 'ComponentFactoryResolver',
        moduleUrl: util_1.assetUrl('core', 'linker/component_factory_resolver'),
        runtime: core_1.ComponentFactoryResolver
    });
    Identifiers.ValueUnwrapper = new compile_metadata_1.CompileIdentifierMetadata({ name: 'ValueUnwrapper', moduleUrl: CD_MODULE_URL, runtime: impValueUnwrapper });
    Identifiers.Injector = new compile_metadata_1.CompileIdentifierMetadata({ name: 'Injector', moduleUrl: util_1.assetUrl('core', 'di/injector'), runtime: impInjector });
    Identifiers.ViewEncapsulation = new compile_metadata_1.CompileIdentifierMetadata({
        name: 'ViewEncapsulation',
        moduleUrl: util_1.assetUrl('core', 'metadata/view'),
        runtime: impViewEncapsulation
    });
    Identifiers.ViewType = new compile_metadata_1.CompileIdentifierMetadata({ name: 'ViewType', moduleUrl: util_1.assetUrl('core', 'linker/view_type'), runtime: impViewType });
    Identifiers.ChangeDetectionStrategy = new compile_metadata_1.CompileIdentifierMetadata({
        name: 'ChangeDetectionStrategy',
        moduleUrl: CD_MODULE_URL,
        runtime: impChangeDetectionStrategy
    });
    Identifiers.StaticNodeDebugInfo = new compile_metadata_1.CompileIdentifierMetadata({
        name: 'StaticNodeDebugInfo',
        moduleUrl: util_1.assetUrl('core', 'linker/debug_context'),
        runtime: impStaticNodeDebugInfo
    });
    Identifiers.DebugContext = new compile_metadata_1.CompileIdentifierMetadata({
        name: 'DebugContext',
        moduleUrl: util_1.assetUrl('core', 'linker/debug_context'),
        runtime: impDebugContext
    });
    Identifiers.Renderer = new compile_metadata_1.CompileIdentifierMetadata({ name: 'Renderer', moduleUrl: util_1.assetUrl('core', 'render/api'), runtime: impRenderer });
    Identifiers.SimpleChange = new compile_metadata_1.CompileIdentifierMetadata({ name: 'SimpleChange', moduleUrl: CD_MODULE_URL, runtime: impSimpleChange });
    Identifiers.uninitialized = new compile_metadata_1.CompileIdentifierMetadata({ name: 'uninitialized', moduleUrl: CD_MODULE_URL, runtime: impUninitialized });
    Identifiers.ChangeDetectorStatus = new compile_metadata_1.CompileIdentifierMetadata({ name: 'ChangeDetectorStatus', moduleUrl: CD_MODULE_URL, runtime: impChangeDetectorStatus });
    Identifiers.checkBinding = new compile_metadata_1.CompileIdentifierMetadata({ name: 'checkBinding', moduleUrl: VIEW_UTILS_MODULE_URL, runtime: impCheckBinding });
    Identifiers.flattenNestedViewRenderNodes = new compile_metadata_1.CompileIdentifierMetadata({
        name: 'flattenNestedViewRenderNodes',
        moduleUrl: VIEW_UTILS_MODULE_URL,
        runtime: impFlattenNestedViewRenderNodes
    });
    Identifiers.devModeEqual = new compile_metadata_1.CompileIdentifierMetadata({ name: 'devModeEqual', moduleUrl: CD_MODULE_URL, runtime: impDevModeEqual });
    Identifiers.interpolate = new compile_metadata_1.CompileIdentifierMetadata({ name: 'interpolate', moduleUrl: VIEW_UTILS_MODULE_URL, runtime: impInterpolate });
    Identifiers.castByValue = new compile_metadata_1.CompileIdentifierMetadata({ name: 'castByValue', moduleUrl: VIEW_UTILS_MODULE_URL, runtime: impCastByValue });
    Identifiers.EMPTY_ARRAY = new compile_metadata_1.CompileIdentifierMetadata({ name: 'EMPTY_ARRAY', moduleUrl: VIEW_UTILS_MODULE_URL, runtime: impEMPTY_ARRAY });
    Identifiers.EMPTY_MAP = new compile_metadata_1.CompileIdentifierMetadata({ name: 'EMPTY_MAP', moduleUrl: VIEW_UTILS_MODULE_URL, runtime: impEMPTY_MAP });
    Identifiers.pureProxies = [
        null,
        new compile_metadata_1.CompileIdentifierMetadata({ name: 'pureProxy1', moduleUrl: VIEW_UTILS_MODULE_URL, runtime: core_private_1.pureProxy1 }),
        new compile_metadata_1.CompileIdentifierMetadata({ name: 'pureProxy2', moduleUrl: VIEW_UTILS_MODULE_URL, runtime: core_private_1.pureProxy2 }),
        new compile_metadata_1.CompileIdentifierMetadata({ name: 'pureProxy3', moduleUrl: VIEW_UTILS_MODULE_URL, runtime: core_private_1.pureProxy3 }),
        new compile_metadata_1.CompileIdentifierMetadata({ name: 'pureProxy4', moduleUrl: VIEW_UTILS_MODULE_URL, runtime: core_private_1.pureProxy4 }),
        new compile_metadata_1.CompileIdentifierMetadata({ name: 'pureProxy5', moduleUrl: VIEW_UTILS_MODULE_URL, runtime: core_private_1.pureProxy5 }),
        new compile_metadata_1.CompileIdentifierMetadata({ name: 'pureProxy6', moduleUrl: VIEW_UTILS_MODULE_URL, runtime: core_private_1.pureProxy6 }),
        new compile_metadata_1.CompileIdentifierMetadata({ name: 'pureProxy7', moduleUrl: VIEW_UTILS_MODULE_URL, runtime: core_private_1.pureProxy7 }),
        new compile_metadata_1.CompileIdentifierMetadata({ name: 'pureProxy8', moduleUrl: VIEW_UTILS_MODULE_URL, runtime: core_private_1.pureProxy8 }),
        new compile_metadata_1.CompileIdentifierMetadata({ name: 'pureProxy9', moduleUrl: VIEW_UTILS_MODULE_URL, runtime: core_private_1.pureProxy9 }),
        new compile_metadata_1.CompileIdentifierMetadata({ name: 'pureProxy10', moduleUrl: VIEW_UTILS_MODULE_URL, runtime: core_private_1.pureProxy10 }),
    ];
    Identifiers.SecurityContext = new compile_metadata_1.CompileIdentifierMetadata({
        name: 'SecurityContext',
        moduleUrl: util_1.assetUrl('core', 'security'),
        runtime: core_private_1.SecurityContext,
    });
    Identifiers.AnimationKeyframe = new compile_metadata_1.CompileIdentifierMetadata({
        name: 'AnimationKeyframe',
        moduleUrl: util_1.assetUrl('core', 'animation/animation_keyframe'),
        runtime: impAnimationKeyframe
    });
    Identifiers.AnimationStyles = new compile_metadata_1.CompileIdentifierMetadata({
        name: 'AnimationStyles',
        moduleUrl: util_1.assetUrl('core', 'animation/animation_styles'),
        runtime: impAnimationStyles
    });
    Identifiers.NoOpAnimationPlayer = new compile_metadata_1.CompileIdentifierMetadata({
        name: 'NoOpAnimationPlayer',
        moduleUrl: util_1.assetUrl('core', 'animation/animation_player'),
        runtime: impNoOpAnimationPlayer
    });
    Identifiers.AnimationGroupPlayer = new compile_metadata_1.CompileIdentifierMetadata({
        name: 'AnimationGroupPlayer',
        moduleUrl: util_1.assetUrl('core', 'animation/animation_group_player'),
        runtime: impAnimationGroupPlayer
    });
    Identifiers.AnimationSequencePlayer = new compile_metadata_1.CompileIdentifierMetadata({
        name: 'AnimationSequencePlayer',
        moduleUrl: util_1.assetUrl('core', 'animation/animation_sequence_player'),
        runtime: impAnimationSequencePlayer
    });
    Identifiers.prepareFinalAnimationStyles = new compile_metadata_1.CompileIdentifierMetadata({
        name: 'prepareFinalAnimationStyles',
        moduleUrl: ANIMATION_STYLE_UTIL_ASSET_URL,
        runtime: core_private_1.prepareFinalAnimationStyles
    });
    Identifiers.balanceAnimationKeyframes = new compile_metadata_1.CompileIdentifierMetadata({
        name: 'balanceAnimationKeyframes',
        moduleUrl: ANIMATION_STYLE_UTIL_ASSET_URL,
        runtime: core_private_1.balanceAnimationKeyframes
    });
    Identifiers.clearStyles = new compile_metadata_1.CompileIdentifierMetadata({ name: 'clearStyles', moduleUrl: ANIMATION_STYLE_UTIL_ASSET_URL, runtime: core_private_1.clearStyles });
    Identifiers.renderStyles = new compile_metadata_1.CompileIdentifierMetadata({ name: 'renderStyles', moduleUrl: ANIMATION_STYLE_UTIL_ASSET_URL, runtime: core_private_1.renderStyles });
    Identifiers.collectAndResolveStyles = new compile_metadata_1.CompileIdentifierMetadata({
        name: 'collectAndResolveStyles',
        moduleUrl: ANIMATION_STYLE_UTIL_ASSET_URL,
        runtime: core_private_1.collectAndResolveStyles
    });
    return Identifiers;
}());
exports.Identifiers = Identifiers;
function identifierToken(identifier) {
    return new compile_metadata_1.CompileTokenMetadata({ identifier: identifier });
}
exports.identifierToken = identifierToken;
//# sourceMappingURL=identifiers.js.map