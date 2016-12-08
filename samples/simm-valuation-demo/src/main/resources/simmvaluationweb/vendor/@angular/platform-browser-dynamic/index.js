/**
 * @license
 * Copyright Google Inc. All Rights Reserved.
 *
 * Use of this source code is governed by an MIT-style license that can be
 * found in the LICENSE file at https://angular.io/license
 */
"use strict";
var common_1 = require('@angular/common');
var compiler_1 = require('@angular/compiler');
var core_1 = require('@angular/core');
var platform_browser_1 = require('@angular/platform-browser');
var core_private_1 = require('./core_private');
var async_1 = require('./src/facade/async');
var lang_1 = require('./src/facade/lang');
var xhr_cache_1 = require('./src/xhr/xhr_cache');
var xhr_impl_1 = require('./src/xhr/xhr_impl');
/**
 * @experimental
 */
exports.BROWSER_APP_COMPILER_PROVIDERS = [
    compiler_1.COMPILER_PROVIDERS, {
        provide: compiler_1.CompilerConfig,
        useFactory: function (platformDirectives, platformPipes) {
            return new compiler_1.CompilerConfig({ platformDirectives: platformDirectives, platformPipes: platformPipes });
        },
        deps: [core_1.PLATFORM_DIRECTIVES, core_1.PLATFORM_PIPES]
    },
    { provide: compiler_1.XHR, useClass: xhr_impl_1.XHRImpl },
    { provide: core_1.PLATFORM_DIRECTIVES, useValue: common_1.COMMON_DIRECTIVES, multi: true },
    { provide: core_1.PLATFORM_PIPES, useValue: common_1.COMMON_PIPES, multi: true }
];
/**
 * @experimental
 */
exports.CACHED_TEMPLATE_PROVIDER = [{ provide: compiler_1.XHR, useClass: xhr_cache_1.CachedXHR }];
/**
 * Bootstrapping for Angular applications.
 *
 * You instantiate an Angular application by explicitly specifying a component to use
 * as the root component for your application via the `bootstrap()` method.
 *
 * ## Simple Example
 *
 * Assuming this `index.html`:
 *
 * ```html
 * <html>
 *   <!-- load Angular script tags here. -->
 *   <body>
 *     <my-app>loading...</my-app>
 *   </body>
 * </html>
 * ```
 *
 * An application is bootstrapped inside an existing browser DOM, typically `index.html`.
 * Unlike Angular 1, Angular 2 does not compile/process providers in `index.html`. This is
 * mainly for security reasons, as well as architectural changes in Angular 2. This means
 * that `index.html` can safely be processed using server-side technologies such as
 * providers. Bindings can thus use double-curly `{{ syntax }}` without collision from
 * Angular 2 component double-curly `{{ syntax }}`.
 *
 * We can use this script code:
 *
 * {@example core/ts/bootstrap/bootstrap.ts region='bootstrap'}
 *
 * When the app developer invokes `bootstrap()` with the root component `MyApp` as its
 * argument, Angular performs the following tasks:
 *
 *  1. It uses the component's `selector` property to locate the DOM element which needs
 *     to be upgraded into the angular component.
 *  2. It creates a new child injector (from the platform injector). Optionally, you can
 *     also override the injector configuration for an app by invoking `bootstrap` with the
 *     `componentInjectableBindings` argument.
 *  3. It creates a new `Zone` and connects it to the angular application's change detection
 *     domain instance.
 *  4. It creates an emulated or shadow DOM on the selected component's host element and loads the
 *     template into it.
 *  5. It instantiates the specified component.
 *  6. Finally, Angular performs change detection to apply the initial data providers for the
 *     application.
 *
 *
 * ## Bootstrapping Multiple Applications
 *
 * When working within a browser window, there are many singleton resources: cookies, title,
 * location, and others. Angular services that represent these resources must likewise be
 * shared across all Angular applications that occupy the same browser window. For this
 * reason, Angular creates exactly one global platform object which stores all shared
 * services, and each angular application injector has the platform injector as its parent.
 *
 * Each application has its own private injector as well. When there are multiple
 * applications on a page, Angular treats each application injector's services as private
 * to that application.
 *
 * ## API
 *
 * - `appComponentType`: The root component which should act as the application. This is
 *   a reference to a `Type` which is annotated with `@Component(...)`.
 * - `customProviders`: An additional set of providers that can be added to the
 *   app injector to override default injection behavior.
 *
 * Returns a `Promise` of {@link ComponentRef}.
 *
 * @experimental This api cannot be used with the offline compiler and thus is still subject to
 * change.
 */
function bootstrap(appComponentType, customProviders) {
    core_private_1.reflector.reflectionCapabilities = new core_private_1.ReflectionCapabilities();
    var providers = [
        platform_browser_1.BROWSER_APP_PROVIDERS, exports.BROWSER_APP_COMPILER_PROVIDERS,
        lang_1.isPresent(customProviders) ? customProviders : []
    ];
    var appInjector = core_1.ReflectiveInjector.resolveAndCreate(providers, platform_browser_1.browserPlatform().injector);
    return core_1.coreLoadAndBootstrap(appComponentType, appInjector);
}
exports.bootstrap = bootstrap;
/**
 * @experimental
 */
function bootstrapWorkerUi(workerScriptUri, customProviders) {
    var app = core_1.ReflectiveInjector.resolveAndCreate([
        platform_browser_1.WORKER_UI_APPLICATION_PROVIDERS, exports.BROWSER_APP_COMPILER_PROVIDERS,
        { provide: platform_browser_1.WORKER_SCRIPT, useValue: workerScriptUri },
        lang_1.isPresent(customProviders) ? customProviders : []
    ], platform_browser_1.workerUiPlatform().injector);
    // Return a promise so that we keep the same semantics as Dart,
    // and we might want to wait for the app side to come up
    // in the future...
    return async_1.PromiseWrapper.resolve(app.get(core_1.ApplicationRef));
}
exports.bootstrapWorkerUi = bootstrapWorkerUi;
/**
 * @experimental
 */
var WORKER_APP_COMPILER_PROVIDERS = [
    compiler_1.COMPILER_PROVIDERS, {
        provide: compiler_1.CompilerConfig,
        useFactory: function (platformDirectives, platformPipes) {
            return new compiler_1.CompilerConfig({ platformDirectives: platformDirectives, platformPipes: platformPipes });
        },
        deps: [core_1.PLATFORM_DIRECTIVES, core_1.PLATFORM_PIPES]
    },
    { provide: compiler_1.XHR, useClass: xhr_impl_1.XHRImpl },
    { provide: core_1.PLATFORM_DIRECTIVES, useValue: common_1.COMMON_DIRECTIVES, multi: true },
    { provide: core_1.PLATFORM_PIPES, useValue: common_1.COMMON_PIPES, multi: true }
];
/**
 * @experimental
 */
function bootstrapWorkerApp(appComponentType, customProviders) {
    var appInjector = core_1.ReflectiveInjector.resolveAndCreate([
        platform_browser_1.WORKER_APP_APPLICATION_PROVIDERS, WORKER_APP_COMPILER_PROVIDERS,
        lang_1.isPresent(customProviders) ? customProviders : []
    ], platform_browser_1.workerAppPlatform().injector);
    return core_1.coreLoadAndBootstrap(appComponentType, appInjector);
}
exports.bootstrapWorkerApp = bootstrapWorkerApp;
//# sourceMappingURL=index.js.map