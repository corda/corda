/**
 * @license
 * Copyright Google Inc. All Rights Reserved.
 *
 * Use of this source code is governed by an MIT-style license that can be
 * found in the LICENSE file at https://angular.io/license
 */
import { ANY_STATE as ANY_STATE_, DEFAULT_STATE as DEFAULT_STATE_, EMPTY_STATE as EMPTY_STATE_, FILL_STYLE_FLAG as FILL_STYLE_FLAG_ } from './src/animation/animation_constants';
import { AnimationDriver as AnimationDriver_, NoOpAnimationDriver as NoOpAnimationDriver_ } from './src/animation/animation_driver';
import { AnimationGroupPlayer as AnimationGroupPlayer_ } from './src/animation/animation_group_player';
import { AnimationKeyframe as AnimationKeyframe_ } from './src/animation/animation_keyframe';
import { AnimationPlayer as AnimationPlayer_, NoOpAnimationPlayer as NoOpAnimationPlayer_ } from './src/animation/animation_player';
import { AnimationSequencePlayer as AnimationSequencePlayer_ } from './src/animation/animation_sequence_player';
import * as animationUtils from './src/animation/animation_style_util';
import { AnimationStyles as AnimationStyles_ } from './src/animation/animation_styles';
import * as change_detection_util from './src/change_detection/change_detection_util';
import * as constants from './src/change_detection/constants';
import * as console from './src/console';
import * as debug from './src/debug/debug_renderer';
import * as provider_util from './src/di/provider_util';
import * as reflective_provider from './src/di/reflective_provider';
import * as component_factory_resolver from './src/linker/component_factory_resolver';
import * as component_resolver from './src/linker/component_resolver';
import * as debug_context from './src/linker/debug_context';
import * as element from './src/linker/element';
import * as template_ref from './src/linker/template_ref';
import * as view from './src/linker/view';
import * as view_type from './src/linker/view_type';
import * as view_utils from './src/linker/view_utils';
import * as lifecycle_hooks from './src/metadata/lifecycle_hooks';
import * as metadata_view from './src/metadata/view';
import * as wtf_init from './src/profile/wtf_init';
import * as reflection from './src/reflection/reflection';
import * as reflection_capabilities from './src/reflection/reflection_capabilities';
import * as reflector_reader from './src/reflection/reflector_reader';
import * as api from './src/render/api';
import * as security from './src/security';
import * as decorators from './src/util/decorators';
export var __core_private__ = {
    isDefaultChangeDetectionStrategy: constants.isDefaultChangeDetectionStrategy,
    ChangeDetectorStatus: constants.ChangeDetectorStatus,
    CHANGE_DETECTION_STRATEGY_VALUES: constants.CHANGE_DETECTION_STRATEGY_VALUES,
    constructDependencies: reflective_provider.constructDependencies,
    LifecycleHooks: lifecycle_hooks.LifecycleHooks,
    LIFECYCLE_HOOKS_VALUES: lifecycle_hooks.LIFECYCLE_HOOKS_VALUES,
    ReflectorReader: reflector_reader.ReflectorReader,
    ReflectorComponentResolver: component_resolver.ReflectorComponentResolver,
    CodegenComponentFactoryResolver: component_factory_resolver.CodegenComponentFactoryResolver,
    AppElement: element.AppElement,
    AppView: view.AppView,
    DebugAppView: view.DebugAppView,
    ViewType: view_type.ViewType,
    MAX_INTERPOLATION_VALUES: view_utils.MAX_INTERPOLATION_VALUES,
    checkBinding: view_utils.checkBinding,
    flattenNestedViewRenderNodes: view_utils.flattenNestedViewRenderNodes,
    interpolate: view_utils.interpolate,
    ViewUtils: view_utils.ViewUtils,
    VIEW_ENCAPSULATION_VALUES: metadata_view.VIEW_ENCAPSULATION_VALUES,
    DebugContext: debug_context.DebugContext,
    StaticNodeDebugInfo: debug_context.StaticNodeDebugInfo,
    devModeEqual: change_detection_util.devModeEqual,
    uninitialized: change_detection_util.uninitialized,
    ValueUnwrapper: change_detection_util.ValueUnwrapper,
    RenderDebugInfo: api.RenderDebugInfo,
    SecurityContext: security.SecurityContext,
    SanitizationService: security.SanitizationService,
    TemplateRef_: template_ref.TemplateRef_,
    wtfInit: wtf_init.wtfInit,
    ReflectionCapabilities: reflection_capabilities.ReflectionCapabilities,
    makeDecorator: decorators.makeDecorator,
    DebugDomRootRenderer: debug.DebugDomRootRenderer,
    createProvider: provider_util.createProvider,
    isProviderLiteral: provider_util.isProviderLiteral,
    EMPTY_ARRAY: view_utils.EMPTY_ARRAY,
    EMPTY_MAP: view_utils.EMPTY_MAP,
    pureProxy1: view_utils.pureProxy1,
    pureProxy2: view_utils.pureProxy2,
    pureProxy3: view_utils.pureProxy3,
    pureProxy4: view_utils.pureProxy4,
    pureProxy5: view_utils.pureProxy5,
    pureProxy6: view_utils.pureProxy6,
    pureProxy7: view_utils.pureProxy7,
    pureProxy8: view_utils.pureProxy8,
    pureProxy9: view_utils.pureProxy9,
    pureProxy10: view_utils.pureProxy10,
    castByValue: view_utils.castByValue,
    Console: console.Console,
    reflector: reflection.reflector,
    Reflector: reflection.Reflector,
    NoOpAnimationPlayer: NoOpAnimationPlayer_,
    AnimationPlayer: AnimationPlayer_,
    NoOpAnimationDriver: NoOpAnimationDriver_,
    AnimationDriver: AnimationDriver_,
    AnimationSequencePlayer: AnimationSequencePlayer_,
    AnimationGroupPlayer: AnimationGroupPlayer_,
    AnimationKeyframe: AnimationKeyframe_,
    prepareFinalAnimationStyles: animationUtils.prepareFinalAnimationStyles,
    balanceAnimationKeyframes: animationUtils.balanceAnimationKeyframes,
    flattenStyles: animationUtils.flattenStyles,
    clearStyles: animationUtils.clearStyles,
    renderStyles: animationUtils.renderStyles,
    collectAndResolveStyles: animationUtils.collectAndResolveStyles,
    AnimationStyles: AnimationStyles_,
    ANY_STATE: ANY_STATE_,
    DEFAULT_STATE: DEFAULT_STATE_,
    EMPTY_STATE: EMPTY_STATE_,
    FILL_STYLE_FLAG: FILL_STYLE_FLAG_
};
//# sourceMappingURL=private_export.js.map