/**
 * @license
 * Copyright Google Inc. All Rights Reserved.
 *
 * Use of this source code is governed by an MIT-style license that can be
 * found in the LICENSE file at https://angular.io/license
 */
import { makeDecorator, makeParamDecorator } from '../util/decorators';
import { HostMetadata, InjectMetadata, InjectableMetadata, OptionalMetadata, SelfMetadata, SkipSelfMetadata } from './metadata';
/**
 * Factory for creating {@link InjectMetadata}.
 * @stable
 * @Annotation
 */
export var Inject = makeParamDecorator(InjectMetadata);
/**
 * Factory for creating {@link OptionalMetadata}.
 * @stable
 * @Annotation
 */
export var Optional = makeParamDecorator(OptionalMetadata);
/**
 * Factory for creating {@link InjectableMetadata}.
 * @stable
 * @Annotation
 */
export var Injectable = makeDecorator(InjectableMetadata);
/**
 * Factory for creating {@link SelfMetadata}.
 * @stable
 * @Annotation
 */
export var Self = makeParamDecorator(SelfMetadata);
/**
 * Factory for creating {@link HostMetadata}.
 * @stable
 * @Annotation
 */
export var Host = makeParamDecorator(HostMetadata);
/**
 * Factory for creating {@link SkipSelfMetadata}.
 * @stable
 * @Annotation
 */
export var SkipSelf = makeParamDecorator(SkipSelfMetadata);
//# sourceMappingURL=decorators.js.map