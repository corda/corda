/**
 * @license
 * Copyright Google Inc. All Rights Reserved.
 *
 * Use of this source code is governed by an MIT-style license that can be
 * found in the LICENSE file at https://angular.io/license
 */
import { BaseException } from '../facade/exceptions';
import { stringify } from '../facade/lang';
/**
 * @stable
 */
export class NoComponentFactoryError extends BaseException {
    constructor(component) {
        super(`No component factory found for ${stringify(component)}`);
        this.component = component;
    }
}
class _NullComponentFactoryResolver {
    resolveComponentFactory(component) {
        throw new NoComponentFactoryError(component);
    }
}
/**
 * @stable
 */
export class ComponentFactoryResolver {
}
ComponentFactoryResolver.NULL = new _NullComponentFactoryResolver();
export class CodegenComponentFactoryResolver {
    constructor(factories, _parent) {
        this._parent = _parent;
        this._factories = new Map();
        for (let i = 0; i < factories.length; i++) {
            let factory = factories[i];
            this._factories.set(factory.componentType, factory);
        }
    }
    resolveComponentFactory(component) {
        let result = this._factories.get(component);
        if (!result) {
            result = this._parent.resolveComponentFactory(component);
        }
        return result;
    }
}
//# sourceMappingURL=component_factory_resolver.js.map