/**
 * @license
 * Copyright Google Inc. All Rights Reserved.
 *
 * Use of this source code is governed by an MIT-style license that can be
 * found in the LICENSE file at https://angular.io/license
 */
import { Attribute, ComponentFactoryResolver, Directive, NoComponentFactoryError, ReflectiveInjector, ViewContainerRef } from '@angular/core';
import { RouterOutletMap } from '../router_outlet_map';
import { PRIMARY_OUTLET } from '../shared';
export class RouterOutlet {
    /**
     * @internal
     */
    constructor(parentOutletMap, location, componentFactoryResolver, name) {
        this.location = location;
        this.componentFactoryResolver = componentFactoryResolver;
        parentOutletMap.registerOutlet(name ? name : PRIMARY_OUTLET, this);
    }
    get isActivated() { return !!this.activated; }
    get component() {
        if (!this.activated)
            throw new Error('Outlet is not activated');
        return this.activated.instance;
    }
    get activatedRoute() {
        if (!this.activated)
            throw new Error('Outlet is not activated');
        return this._activatedRoute;
    }
    deactivate() {
        if (this.activated) {
            this.activated.destroy();
            this.activated = null;
        }
    }
    activate(activatedRoute, providers, outletMap) {
        this.outletMap = outletMap;
        this._activatedRoute = activatedRoute;
        const snapshot = activatedRoute._futureSnapshot;
        const component = snapshot._routeConfig.component;
        let factory;
        try {
            factory = typeof component === 'string' ?
                snapshot._resolvedComponentFactory :
                this.componentFactoryResolver.resolveComponentFactory(component);
        }
        catch (e) {
            if (!(e instanceof NoComponentFactoryError))
                throw e;
            // TODO: vsavkin uncomment this once CompoentResolver is deprecated
            // const componentName = component ? component.name : null;
            // console.warn(
            //     `'${componentName}' not found in precompile array.  To ensure all components referred
            //     to by the RouterConfig are compiled, you must add '${componentName}' to the
            //     'precompile' array of your application component. This will be required in a future
            //     release of the router.`);
            factory = snapshot._resolvedComponentFactory;
        }
        const inj = ReflectiveInjector.fromResolvedProviders(providers, this.location.parentInjector);
        this.activated = this.location.createComponent(factory, this.location.length, inj, []);
    }
}
/** @nocollapse */
RouterOutlet.decorators = [
    { type: Directive, args: [{ selector: 'router-outlet' },] },
];
/** @nocollapse */
RouterOutlet.ctorParameters = [
    { type: RouterOutletMap, },
    { type: ViewContainerRef, },
    { type: ComponentFactoryResolver, },
    { type: undefined, decorators: [{ type: Attribute, args: ['name',] },] },
];
//# sourceMappingURL=router_outlet.js.map