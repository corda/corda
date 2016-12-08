/**
 * @license
 * Copyright Google Inc. All Rights Reserved.
 *
 * Use of this source code is governed by an MIT-style license that can be
 * found in the LICENSE file at https://angular.io/license
 */
import { ChangeDetectionStrategy, ChangeDetectorRef, ComponentFactoryResolver, ElementRef, Injector, QueryList, RenderComponentType, Renderer, SimpleChange, TemplateRef, ViewContainerRef, ViewEncapsulation } from '@angular/core';
import { AnimationGroupPlayer as AnimationGroupPlayer_, AnimationKeyframe as AnimationKeyframe_, AnimationSequencePlayer as AnimationSequencePlayer_, AnimationStyles as AnimationStyles_, AppElement, AppView, ChangeDetectorStatus, CodegenComponentFactoryResolver, DebugAppView, DebugContext, EMPTY_ARRAY, EMPTY_MAP, NoOpAnimationPlayer as NoOpAnimationPlayer_, SecurityContext, StaticNodeDebugInfo, TemplateRef_, ValueUnwrapper, ViewType, ViewUtils, balanceAnimationKeyframes as impBalanceAnimationKeyframes, castByValue, checkBinding, clearStyles as impClearStyles, collectAndResolveStyles as impCollectAndResolveStyles, devModeEqual, flattenNestedViewRenderNodes, interpolate, prepareFinalAnimationStyles as impBalanceAnimationStyles, pureProxy1, pureProxy10, pureProxy2, pureProxy3, pureProxy4, pureProxy5, pureProxy6, pureProxy7, pureProxy8, pureProxy9, renderStyles as impRenderStyles, uninitialized } from '../core_private';
import { CompileIdentifierMetadata, CompileTokenMetadata } from './compile_metadata';
import { assetUrl } from './util';
var APP_VIEW_MODULE_URL = assetUrl('core', 'linker/view');
var VIEW_UTILS_MODULE_URL = assetUrl('core', 'linker/view_utils');
var CD_MODULE_URL = assetUrl('core', 'change_detection/change_detection');
// Reassign the imports to different variables so we can
// define static variables with the name of the import.
// (only needed for Dart).
var impViewUtils = ViewUtils;
var impAppView = AppView;
var impDebugAppView = DebugAppView;
var impDebugContext = DebugContext;
var impAppElement = AppElement;
var impElementRef = ElementRef;
var impViewContainerRef = ViewContainerRef;
var impChangeDetectorRef = ChangeDetectorRef;
var impRenderComponentType = RenderComponentType;
var impQueryList = QueryList;
var impTemplateRef = TemplateRef;
var impTemplateRef_ = TemplateRef_;
var impValueUnwrapper = ValueUnwrapper;
var impInjector = Injector;
var impViewEncapsulation = ViewEncapsulation;
var impViewType = ViewType;
var impChangeDetectionStrategy = ChangeDetectionStrategy;
var impStaticNodeDebugInfo = StaticNodeDebugInfo;
var impRenderer = Renderer;
var impSimpleChange = SimpleChange;
var impUninitialized = uninitialized;
var impChangeDetectorStatus = ChangeDetectorStatus;
var impFlattenNestedViewRenderNodes = flattenNestedViewRenderNodes;
var impDevModeEqual = devModeEqual;
var impInterpolate = interpolate;
var impCheckBinding = checkBinding;
var impCastByValue = castByValue;
var impEMPTY_ARRAY = EMPTY_ARRAY;
var impEMPTY_MAP = EMPTY_MAP;
var impAnimationGroupPlayer = AnimationGroupPlayer_;
var impAnimationSequencePlayer = AnimationSequencePlayer_;
var impAnimationKeyframe = AnimationKeyframe_;
var impAnimationStyles = AnimationStyles_;
var impNoOpAnimationPlayer = NoOpAnimationPlayer_;
var ANIMATION_STYLE_UTIL_ASSET_URL = assetUrl('core', 'animation/animation_style_util');
export class Identifiers {
}
Identifiers.ViewUtils = new CompileIdentifierMetadata({ name: 'ViewUtils', moduleUrl: assetUrl('core', 'linker/view_utils'), runtime: impViewUtils });
Identifiers.AppView = new CompileIdentifierMetadata({ name: 'AppView', moduleUrl: APP_VIEW_MODULE_URL, runtime: impAppView });
Identifiers.DebugAppView = new CompileIdentifierMetadata({ name: 'DebugAppView', moduleUrl: APP_VIEW_MODULE_URL, runtime: impDebugAppView });
Identifiers.AppElement = new CompileIdentifierMetadata({ name: 'AppElement', moduleUrl: assetUrl('core', 'linker/element'), runtime: impAppElement });
Identifiers.ElementRef = new CompileIdentifierMetadata({
    name: 'ElementRef',
    moduleUrl: assetUrl('core', 'linker/element_ref'),
    runtime: impElementRef
});
Identifiers.ViewContainerRef = new CompileIdentifierMetadata({
    name: 'ViewContainerRef',
    moduleUrl: assetUrl('core', 'linker/view_container_ref'),
    runtime: impViewContainerRef
});
Identifiers.ChangeDetectorRef = new CompileIdentifierMetadata({
    name: 'ChangeDetectorRef',
    moduleUrl: assetUrl('core', 'change_detection/change_detector_ref'),
    runtime: impChangeDetectorRef
});
Identifiers.RenderComponentType = new CompileIdentifierMetadata({
    name: 'RenderComponentType',
    moduleUrl: assetUrl('core', 'render/api'),
    runtime: impRenderComponentType
});
Identifiers.QueryList = new CompileIdentifierMetadata({ name: 'QueryList', moduleUrl: assetUrl('core', 'linker/query_list'), runtime: impQueryList });
Identifiers.TemplateRef = new CompileIdentifierMetadata({
    name: 'TemplateRef',
    moduleUrl: assetUrl('core', 'linker/template_ref'),
    runtime: impTemplateRef
});
Identifiers.TemplateRef_ = new CompileIdentifierMetadata({
    name: 'TemplateRef_',
    moduleUrl: assetUrl('core', 'linker/template_ref'),
    runtime: impTemplateRef_
});
Identifiers.CodegenComponentFactoryResolver = new CompileIdentifierMetadata({
    name: 'CodegenComponentFactoryResolver',
    moduleUrl: assetUrl('core', 'linker/component_factory_resolver'),
    runtime: CodegenComponentFactoryResolver
});
Identifiers.ComponentFactoryResolver = new CompileIdentifierMetadata({
    name: 'ComponentFactoryResolver',
    moduleUrl: assetUrl('core', 'linker/component_factory_resolver'),
    runtime: ComponentFactoryResolver
});
Identifiers.ValueUnwrapper = new CompileIdentifierMetadata({ name: 'ValueUnwrapper', moduleUrl: CD_MODULE_URL, runtime: impValueUnwrapper });
Identifiers.Injector = new CompileIdentifierMetadata({ name: 'Injector', moduleUrl: assetUrl('core', 'di/injector'), runtime: impInjector });
Identifiers.ViewEncapsulation = new CompileIdentifierMetadata({
    name: 'ViewEncapsulation',
    moduleUrl: assetUrl('core', 'metadata/view'),
    runtime: impViewEncapsulation
});
Identifiers.ViewType = new CompileIdentifierMetadata({ name: 'ViewType', moduleUrl: assetUrl('core', 'linker/view_type'), runtime: impViewType });
Identifiers.ChangeDetectionStrategy = new CompileIdentifierMetadata({
    name: 'ChangeDetectionStrategy',
    moduleUrl: CD_MODULE_URL,
    runtime: impChangeDetectionStrategy
});
Identifiers.StaticNodeDebugInfo = new CompileIdentifierMetadata({
    name: 'StaticNodeDebugInfo',
    moduleUrl: assetUrl('core', 'linker/debug_context'),
    runtime: impStaticNodeDebugInfo
});
Identifiers.DebugContext = new CompileIdentifierMetadata({
    name: 'DebugContext',
    moduleUrl: assetUrl('core', 'linker/debug_context'),
    runtime: impDebugContext
});
Identifiers.Renderer = new CompileIdentifierMetadata({ name: 'Renderer', moduleUrl: assetUrl('core', 'render/api'), runtime: impRenderer });
Identifiers.SimpleChange = new CompileIdentifierMetadata({ name: 'SimpleChange', moduleUrl: CD_MODULE_URL, runtime: impSimpleChange });
Identifiers.uninitialized = new CompileIdentifierMetadata({ name: 'uninitialized', moduleUrl: CD_MODULE_URL, runtime: impUninitialized });
Identifiers.ChangeDetectorStatus = new CompileIdentifierMetadata({ name: 'ChangeDetectorStatus', moduleUrl: CD_MODULE_URL, runtime: impChangeDetectorStatus });
Identifiers.checkBinding = new CompileIdentifierMetadata({ name: 'checkBinding', moduleUrl: VIEW_UTILS_MODULE_URL, runtime: impCheckBinding });
Identifiers.flattenNestedViewRenderNodes = new CompileIdentifierMetadata({
    name: 'flattenNestedViewRenderNodes',
    moduleUrl: VIEW_UTILS_MODULE_URL,
    runtime: impFlattenNestedViewRenderNodes
});
Identifiers.devModeEqual = new CompileIdentifierMetadata({ name: 'devModeEqual', moduleUrl: CD_MODULE_URL, runtime: impDevModeEqual });
Identifiers.interpolate = new CompileIdentifierMetadata({ name: 'interpolate', moduleUrl: VIEW_UTILS_MODULE_URL, runtime: impInterpolate });
Identifiers.castByValue = new CompileIdentifierMetadata({ name: 'castByValue', moduleUrl: VIEW_UTILS_MODULE_URL, runtime: impCastByValue });
Identifiers.EMPTY_ARRAY = new CompileIdentifierMetadata({ name: 'EMPTY_ARRAY', moduleUrl: VIEW_UTILS_MODULE_URL, runtime: impEMPTY_ARRAY });
Identifiers.EMPTY_MAP = new CompileIdentifierMetadata({ name: 'EMPTY_MAP', moduleUrl: VIEW_UTILS_MODULE_URL, runtime: impEMPTY_MAP });
Identifiers.pureProxies = [
    null,
    new CompileIdentifierMetadata({ name: 'pureProxy1', moduleUrl: VIEW_UTILS_MODULE_URL, runtime: pureProxy1 }),
    new CompileIdentifierMetadata({ name: 'pureProxy2', moduleUrl: VIEW_UTILS_MODULE_URL, runtime: pureProxy2 }),
    new CompileIdentifierMetadata({ name: 'pureProxy3', moduleUrl: VIEW_UTILS_MODULE_URL, runtime: pureProxy3 }),
    new CompileIdentifierMetadata({ name: 'pureProxy4', moduleUrl: VIEW_UTILS_MODULE_URL, runtime: pureProxy4 }),
    new CompileIdentifierMetadata({ name: 'pureProxy5', moduleUrl: VIEW_UTILS_MODULE_URL, runtime: pureProxy5 }),
    new CompileIdentifierMetadata({ name: 'pureProxy6', moduleUrl: VIEW_UTILS_MODULE_URL, runtime: pureProxy6 }),
    new CompileIdentifierMetadata({ name: 'pureProxy7', moduleUrl: VIEW_UTILS_MODULE_URL, runtime: pureProxy7 }),
    new CompileIdentifierMetadata({ name: 'pureProxy8', moduleUrl: VIEW_UTILS_MODULE_URL, runtime: pureProxy8 }),
    new CompileIdentifierMetadata({ name: 'pureProxy9', moduleUrl: VIEW_UTILS_MODULE_URL, runtime: pureProxy9 }),
    new CompileIdentifierMetadata({ name: 'pureProxy10', moduleUrl: VIEW_UTILS_MODULE_URL, runtime: pureProxy10 }),
];
Identifiers.SecurityContext = new CompileIdentifierMetadata({
    name: 'SecurityContext',
    moduleUrl: assetUrl('core', 'security'),
    runtime: SecurityContext,
});
Identifiers.AnimationKeyframe = new CompileIdentifierMetadata({
    name: 'AnimationKeyframe',
    moduleUrl: assetUrl('core', 'animation/animation_keyframe'),
    runtime: impAnimationKeyframe
});
Identifiers.AnimationStyles = new CompileIdentifierMetadata({
    name: 'AnimationStyles',
    moduleUrl: assetUrl('core', 'animation/animation_styles'),
    runtime: impAnimationStyles
});
Identifiers.NoOpAnimationPlayer = new CompileIdentifierMetadata({
    name: 'NoOpAnimationPlayer',
    moduleUrl: assetUrl('core', 'animation/animation_player'),
    runtime: impNoOpAnimationPlayer
});
Identifiers.AnimationGroupPlayer = new CompileIdentifierMetadata({
    name: 'AnimationGroupPlayer',
    moduleUrl: assetUrl('core', 'animation/animation_group_player'),
    runtime: impAnimationGroupPlayer
});
Identifiers.AnimationSequencePlayer = new CompileIdentifierMetadata({
    name: 'AnimationSequencePlayer',
    moduleUrl: assetUrl('core', 'animation/animation_sequence_player'),
    runtime: impAnimationSequencePlayer
});
Identifiers.prepareFinalAnimationStyles = new CompileIdentifierMetadata({
    name: 'prepareFinalAnimationStyles',
    moduleUrl: ANIMATION_STYLE_UTIL_ASSET_URL,
    runtime: impBalanceAnimationStyles
});
Identifiers.balanceAnimationKeyframes = new CompileIdentifierMetadata({
    name: 'balanceAnimationKeyframes',
    moduleUrl: ANIMATION_STYLE_UTIL_ASSET_URL,
    runtime: impBalanceAnimationKeyframes
});
Identifiers.clearStyles = new CompileIdentifierMetadata({ name: 'clearStyles', moduleUrl: ANIMATION_STYLE_UTIL_ASSET_URL, runtime: impClearStyles });
Identifiers.renderStyles = new CompileIdentifierMetadata({ name: 'renderStyles', moduleUrl: ANIMATION_STYLE_UTIL_ASSET_URL, runtime: impRenderStyles });
Identifiers.collectAndResolveStyles = new CompileIdentifierMetadata({
    name: 'collectAndResolveStyles',
    moduleUrl: ANIMATION_STYLE_UTIL_ASSET_URL,
    runtime: impCollectAndResolveStyles
});
export function identifierToken(identifier) {
    return new CompileTokenMetadata({ identifier: identifier });
}
//# sourceMappingURL=identifiers.js.map