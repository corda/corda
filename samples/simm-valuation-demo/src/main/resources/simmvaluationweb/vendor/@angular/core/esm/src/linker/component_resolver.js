/**
 * @license
 * Copyright Google Inc. All Rights Reserved.
 *
 * Use of this source code is governed by an MIT-style license that can be
 * found in the LICENSE file at https://angular.io/license
 */
import { Injectable } from '../di/decorators';
import { PromiseWrapper } from '../facade/async';
import { BaseException } from '../facade/exceptions';
import { isBlank, isString, stringify } from '../facade/lang';
import { reflector } from '../reflection/reflection';
import { ComponentFactory } from './component_factory';
/**
 * Low-level service for loading {@link ComponentFactory}s, which
 * can later be used to create and render a Component instance.
 * @experimental
 */
export class ComponentResolver {
}
function _isComponentFactory(type) {
    return type instanceof ComponentFactory;
}
export class ReflectorComponentResolver extends ComponentResolver {
    resolveComponent(component) {
        if (isString(component)) {
            return PromiseWrapper.reject(new BaseException(`Cannot resolve component using '${component}'.`), null);
        }
        var metadatas = reflector.annotations(component);
        var componentFactory = metadatas.find(_isComponentFactory);
        if (isBlank(componentFactory)) {
            throw new BaseException(`No precompiled component ${stringify(component)} found`);
        }
        return PromiseWrapper.resolve(componentFactory);
    }
    clearCache() { }
}
/** @nocollapse */
ReflectorComponentResolver.decorators = [
    { type: Injectable },
];
//# sourceMappingURL=component_resolver.js.map