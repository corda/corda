/**
 * @license
 * Copyright Google Inc. All Rights Reserved.
 *
 * Use of this source code is governed by an MIT-style license that can be
 * found in the LICENSE file at https://angular.io/license
 */
import { global, isString } from '../facade/lang';
const _SEPARATOR = '#';
/**
 * Component resolver that can load components lazily
 * @experimental
 */
export class SystemJsComponentResolver {
    constructor(_resolver) {
        this._resolver = _resolver;
    }
    resolveComponent(componentType) {
        if (isString(componentType)) {
            let [module, component] = componentType.split(_SEPARATOR);
            if (component === void (0)) {
                // Use the default export when no component is specified
                component = 'default';
            }
            return global
                .System.import(module)
                .then((module) => this._resolver.resolveComponent(module[component]));
        }
        return this._resolver.resolveComponent(componentType);
    }
    clearCache() { }
}
const FACTORY_MODULE_SUFFIX = '.ngfactory';
const FACTORY_CLASS_SUFFIX = 'NgFactory';
/**
 * Component resolver that can load component factories lazily
 * @experimental
 */
export class SystemJsCmpFactoryResolver {
    resolveComponent(componentType) {
        if (isString(componentType)) {
            let [module, factory] = componentType.split(_SEPARATOR);
            return global
                .System.import(module + FACTORY_MODULE_SUFFIX)
                .then((module) => module[factory + FACTORY_CLASS_SUFFIX]);
        }
        return Promise.resolve(null);
    }
    clearCache() { }
}
//# sourceMappingURL=systemjs_component_resolver.js.map