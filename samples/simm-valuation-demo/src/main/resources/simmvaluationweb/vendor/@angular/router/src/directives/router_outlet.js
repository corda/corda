/**
 * @license
 * Copyright Google Inc. All Rights Reserved.
 *
 * Use of this source code is governed by an MIT-style license that can be
 * found in the LICENSE file at https://angular.io/license
 */
"use strict";
var core_1 = require('@angular/core');
var router_outlet_map_1 = require('../router_outlet_map');
var shared_1 = require('../shared');
var RouterOutlet = (function () {
    /**
     * @internal
     */
    function RouterOutlet(parentOutletMap, location, componentFactoryResolver, name) {
        this.location = location;
        this.componentFactoryResolver = componentFactoryResolver;
        parentOutletMap.registerOutlet(name ? name : shared_1.PRIMARY_OUTLET, this);
    }
    Object.defineProperty(RouterOutlet.prototype, "isActivated", {
        get: function () { return !!this.activated; },
        enumerable: true,
        configurable: true
    });
    Object.defineProperty(RouterOutlet.prototype, "component", {
        get: function () {
            if (!this.activated)
                throw new Error('Outlet is not activated');
            return this.activated.instance;
        },
        enumerable: true,
        configurable: true
    });
    Object.defineProperty(RouterOutlet.prototype, "activatedRoute", {
        get: function () {
            if (!this.activated)
                throw new Error('Outlet is not activated');
            return this._activatedRoute;
        },
        enumerable: true,
        configurable: true
    });
    RouterOutlet.prototype.deactivate = function () {
        if (this.activated) {
            this.activated.destroy();
            this.activated = null;
        }
    };
    RouterOutlet.prototype.activate = function (activatedRoute, providers, outletMap) {
        this.outletMap = outletMap;
        this._activatedRoute = activatedRoute;
        var snapshot = activatedRoute._futureSnapshot;
        var component = snapshot._routeConfig.component;
        var factory;
        try {
            factory = typeof component === 'string' ?
                snapshot._resolvedComponentFactory :
                this.componentFactoryResolver.resolveComponentFactory(component);
        }
        catch (e) {
            if (!(e instanceof core_1.NoComponentFactoryError))
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
        var inj = core_1.ReflectiveInjector.fromResolvedProviders(providers, this.location.parentInjector);
        this.activated = this.location.createComponent(factory, this.location.length, inj, []);
    };
    /** @nocollapse */
    RouterOutlet.decorators = [
        { type: core_1.Directive, args: [{ selector: 'router-outlet' },] },
    ];
    /** @nocollapse */
    RouterOutlet.ctorParameters = [
        { type: router_outlet_map_1.RouterOutletMap, },
        { type: core_1.ViewContainerRef, },
        { type: core_1.ComponentFactoryResolver, },
        { type: undefined, decorators: [{ type: core_1.Attribute, args: ['name',] },] },
    ];
    return RouterOutlet;
}());
exports.RouterOutlet = RouterOutlet;
//# sourceMappingURL=router_outlet.js.map