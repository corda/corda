"use strict";
var __decorate = (this && this.__decorate) || function (decorators, target, key, desc) {
    var c = arguments.length, r = c < 3 ? target : desc === null ? desc = Object.getOwnPropertyDescriptor(target, key) : desc, d;
    if (typeof Reflect === "object" && typeof Reflect.decorate === "function") r = Reflect.decorate(decorators, target, key, desc);
    else for (var i = decorators.length - 1; i >= 0; i--) if (d = decorators[i]) r = (c < 3 ? d(r) : c > 3 ? d(target, key, r) : d(target, key)) || r;
    return c > 3 && r && Object.defineProperty(target, key, r), r;
};
var __metadata = (this && this.__metadata) || function (k, v) {
    if (typeof Reflect === "object" && typeof Reflect.metadata === "function") return Reflect.metadata(k, v);
};
var core_1 = require('@angular/core');
var platform_browser_1 = require('@angular/platform-browser');
/**
 * Components helper class to easily work with
 * allows to:
 * - get application root view container ref
 */
var ComponentsHelper = (function () {
    function ComponentsHelper(applicationRef, componentResolver, injector) {
        this.applicationRef = applicationRef;
        this.componentResolver = componentResolver;
        this.injector = injector;
    }
    ComponentsHelper.prototype.getDocument = function () {
        return this.injector.get(platform_browser_1.DOCUMENT);
    };
    /**
     * This is a name conventional class to get application root view component ref
     * to made this method working you need to add:
     * ```typescript
     *  @Component({
     *   selector: 'my-app',
     *   ...
     *   })
     *  export class MyApp {
     *    constructor(viewContainerRef: ViewContainerRef) {
     *        // A Default view container ref, usually the app root container ref.
     *        // Has to be set manually until we can find a way to get it automatically.
     *        this.viewContainerRef = viewContainerRef;
     *      }
     *  }
     * ```
     * @returns {ViewContainerRef} - application root view component ref
     */
    ComponentsHelper.prototype.getRootViewContainerRef = function () {
        // The only way for now (by @mhevery)
        // https://github.com/angular/angular/issues/6446#issuecomment-173459525
        // this is a class of application bootstrap component (like my-app)
        var classOfRootComponent = this.applicationRef.componentTypes[0];
        // this is an instance of application bootstrap component
        var appInstance = this.injector.get(classOfRootComponent);
        return appInstance.viewContainerRef;
    };
    /**
     * Helper methods to add ComponentClass(like modal backdrop) with options
     * of type ComponentOptionsClass to element next to application root
     * or next to provided instance of view container
     * @param ComponentClass - @Component class
     * @param ComponentOptionsClass - options class
     * @param options - instance of options
     * @param _viewContainerRef - optional instance of ViewContainerRef
     * @returns {Promise<ComponentRef<T>>} - returns a promise with ComponentRef<T>
     */
    ComponentsHelper.prototype.appendNextToRoot = function (ComponentClass, ComponentOptionsClass, options, _viewContainerRef) {
        var _this = this;
        return this.componentResolver
            .resolveComponent(ComponentClass)
            .then(function (componentFactory) {
            var viewContainerRef = _viewContainerRef || _this.getRootViewContainerRef();
            var bindings = core_1.ReflectiveInjector.resolve([
                new core_1.Provider(ComponentOptionsClass, { useValue: options })
            ]);
            var ctxInjector = viewContainerRef.parentInjector;
            var childInjector = Array.isArray(bindings) && bindings.length > 0 ?
                core_1.ReflectiveInjector.fromResolvedProviders(bindings, ctxInjector) : ctxInjector;
            return viewContainerRef.createComponent(componentFactory, viewContainerRef.length, childInjector);
        });
    };
    ComponentsHelper = __decorate([
        core_1.Injectable(), 
        __metadata('design:paramtypes', [core_1.ApplicationRef, core_1.ComponentResolver, core_1.Injector])
    ], ComponentsHelper);
    return ComponentsHelper;
}());
exports.ComponentsHelper = ComponentsHelper;
