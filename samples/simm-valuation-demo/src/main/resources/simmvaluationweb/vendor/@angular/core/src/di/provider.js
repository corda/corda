/**
 * @license
 * Copyright Google Inc. All Rights Reserved.
 *
 * Use of this source code is governed by an MIT-style license that can be
 * found in the LICENSE file at https://angular.io/license
 */
"use strict";
var __extends = (this && this.__extends) || function (d, b) {
    for (var p in b) if (b.hasOwnProperty(p)) d[p] = b[p];
    function __() { this.constructor = d; }
    d.prototype = b === null ? Object.create(b) : (__.prototype = b.prototype, new __());
};
var exceptions_1 = require('../facade/exceptions');
var lang_1 = require('../facade/lang');
/**
 * Describes how the {@link Injector} should instantiate a given token.
 *
 * See {@link provide}.
 *
 * ### Example ([live demo](http://plnkr.co/edit/GNAyj6K6PfYg2NBzgwZ5?p%3Dpreview&p=preview))
 *
 * ```javascript
 * var injector = Injector.resolveAndCreate([
 *   new Provider("message", { useValue: 'Hello' })
 * ]);
 *
 * expect(injector.get("message")).toEqual('Hello');
 * ```
 * @ts2dart_const
 * @deprecated
 */
var Provider = (function () {
    function Provider(token, _a) {
        var useClass = _a.useClass, useValue = _a.useValue, useExisting = _a.useExisting, useFactory = _a.useFactory, deps = _a.deps, multi = _a.multi;
        this.token = token;
        this.useClass = useClass;
        this.useValue = useValue;
        this.useExisting = useExisting;
        this.useFactory = useFactory;
        this.dependencies = deps;
        this._multi = multi;
    }
    Object.defineProperty(Provider.prototype, "multi", {
        // TODO: Provide a full working example after alpha38 is released.
        /**
         * Creates multiple providers matching the same token (a multi-provider).
         *
         * Multi-providers are used for creating pluggable service, where the system comes
         * with some default providers, and the user can register additional providers.
         * The combination of the default providers and the additional providers will be
         * used to drive the behavior of the system.
         *
         * ### Example
         *
         * ```typescript
         * var injector = Injector.resolveAndCreate([
         *   new Provider("Strings", { useValue: "String1", multi: true}),
         *   new Provider("Strings", { useValue: "String2", multi: true})
         * ]);
         *
         * expect(injector.get("Strings")).toEqual(["String1", "String2"]);
         * ```
         *
         * Multi-providers and regular providers cannot be mixed. The following
         * will throw an exception:
         *
         * ```typescript
         * var injector = Injector.resolveAndCreate([
         *   new Provider("Strings", { useValue: "String1", multi: true }),
         *   new Provider("Strings", { useValue: "String2"})
         * ]);
         * ```
         */
        get: function () { return lang_1.normalizeBool(this._multi); },
        enumerable: true,
        configurable: true
    });
    return Provider;
}());
exports.Provider = Provider;
/**
 * See {@link Provider} instead.
 *
 * @deprecated
 * @ts2dart_const
 */
var Binding = (function (_super) {
    __extends(Binding, _super);
    function Binding(token, _a) {
        var toClass = _a.toClass, toValue = _a.toValue, toAlias = _a.toAlias, toFactory = _a.toFactory, deps = _a.deps, multi = _a.multi;
        _super.call(this, token, {
            useClass: toClass,
            useValue: toValue,
            useExisting: toAlias,
            useFactory: toFactory,
            deps: deps,
            multi: multi
        });
    }
    Object.defineProperty(Binding.prototype, "toClass", {
        /**
         * @deprecated
         */
        get: function () { return this.useClass; },
        enumerable: true,
        configurable: true
    });
    Object.defineProperty(Binding.prototype, "toAlias", {
        /**
         * @deprecated
         */
        get: function () { return this.useExisting; },
        enumerable: true,
        configurable: true
    });
    Object.defineProperty(Binding.prototype, "toFactory", {
        /**
         * @deprecated
         */
        get: function () { return this.useFactory; },
        enumerable: true,
        configurable: true
    });
    Object.defineProperty(Binding.prototype, "toValue", {
        /**
         * @deprecated
         */
        get: function () { return this.useValue; },
        enumerable: true,
        configurable: true
    });
    return Binding;
}(Provider));
exports.Binding = Binding;
/**
 * Creates a {@link Provider}.
 *
 * To construct a {@link Provider}, bind a `token` to either a class, a value, a factory function,
 * or
 * to an existing `token`.
 * See {@link ProviderBuilder} for more details.
 *
 * The `token` is most commonly a class or {@link OpaqueToken-class.html}.
 *
 * @deprecated
 */
function bind(token) {
    return new ProviderBuilder(token);
}
exports.bind = bind;
/**
 * Helper class for the {@link bind} function.
 * @deprecated
 */
var ProviderBuilder = (function () {
    function ProviderBuilder(token) {
        this.token = token;
    }
    /**
     * Binds a DI token to a class.
     *
     * ### Example ([live demo](http://plnkr.co/edit/ZpBCSYqv6e2ud5KXLdxQ?p=preview))
     *
     * Because `toAlias` and `toClass` are often confused, the example contains
     * both use cases for easy comparison.
     *
     * ```typescript
     * class Vehicle {}
     *
     * class Car extends Vehicle {}
     *
     * var injectorClass = Injector.resolveAndCreate([
     *   Car,
     *   {provide: Vehicle, useClass: Car}
     * ]);
     * var injectorAlias = Injector.resolveAndCreate([
     *   Car,
     *   {provide: Vehicle, useExisting: Car}
     * ]);
     *
     * expect(injectorClass.get(Vehicle)).not.toBe(injectorClass.get(Car));
     * expect(injectorClass.get(Vehicle) instanceof Car).toBe(true);
     *
     * expect(injectorAlias.get(Vehicle)).toBe(injectorAlias.get(Car));
     * expect(injectorAlias.get(Vehicle) instanceof Car).toBe(true);
     * ```
     */
    ProviderBuilder.prototype.toClass = function (type) {
        if (!lang_1.isType(type)) {
            throw new exceptions_1.BaseException("Trying to create a class provider but \"" + lang_1.stringify(type) + "\" is not a class!");
        }
        return new Provider(this.token, { useClass: type });
    };
    /**
     * Binds a DI token to a value.
     *
     * ### Example ([live demo](http://plnkr.co/edit/G024PFHmDL0cJFgfZK8O?p=preview))
     *
     * ```typescript
     * var injector = Injector.resolveAndCreate([
     *   {provide: 'message', useValue: 'Hello'}
     * ]);
     *
     * expect(injector.get('message')).toEqual('Hello');
     * ```
     */
    ProviderBuilder.prototype.toValue = function (value) { return new Provider(this.token, { useValue: value }); };
    /**
     * Binds a DI token to an existing token.
     *
     * Angular will return the same instance as if the provided token was used. (This is
     * in contrast to `useClass` where a separate instance of `useClass` will be returned.)
     *
     * ### Example ([live demo](http://plnkr.co/edit/uBaoF2pN5cfc5AfZapNw?p=preview))
     *
     * Because `toAlias` and `toClass` are often confused, the example contains
     * both use cases for easy comparison.
     *
     * ```typescript
     * class Vehicle {}
     *
     * class Car extends Vehicle {}
     *
     * var injectorAlias = Injector.resolveAndCreate([
     *   Car,
     *   {provide: Vehicle, useExisting: Car}
     * ]);
     * var injectorClass = Injector.resolveAndCreate([
     *   Car,
     *   {provide: Vehicle, useClass: Car})
     * ]);
     *
     * expect(injectorAlias.get(Vehicle)).toBe(injectorAlias.get(Car));
     * expect(injectorAlias.get(Vehicle) instanceof Car).toBe(true);
     *
     * expect(injectorClass.get(Vehicle)).not.toBe(injectorClass.get(Car));
     * expect(injectorClass.get(Vehicle) instanceof Car).toBe(true);
     * ```
     */
    ProviderBuilder.prototype.toAlias = function (aliasToken) {
        if (lang_1.isBlank(aliasToken)) {
            throw new exceptions_1.BaseException("Can not alias " + lang_1.stringify(this.token) + " to a blank value!");
        }
        return new Provider(this.token, { useExisting: aliasToken });
    };
    /**
     * Binds a DI token to a function which computes the value.
     *
     * ### Example ([live demo](http://plnkr.co/edit/OejNIfTT3zb1iBxaIYOb?p=preview))
     *
     * ```typescript
     * var injector = Injector.resolveAndCreate([
     *   {provide: Number, useFactory: () => { return 1+2; }},
     *   {provide: String, useFactory: (v) => { return "Value: " + v; }, deps: [Number]}
     * ]);
     *
     * expect(injector.get(Number)).toEqual(3);
     * expect(injector.get(String)).toEqual('Value: 3');
     * ```
     */
    ProviderBuilder.prototype.toFactory = function (factory, dependencies) {
        if (!lang_1.isFunction(factory)) {
            throw new exceptions_1.BaseException("Trying to create a factory provider but \"" + lang_1.stringify(factory) + "\" is not a function!");
        }
        return new Provider(this.token, { useFactory: factory, deps: dependencies });
    };
    return ProviderBuilder;
}());
exports.ProviderBuilder = ProviderBuilder;
/**
 * Creates a {@link Provider}.
 *
 * See {@link Provider} for more details.
 *
 * <!-- TODO: improve the docs -->
 * @deprecated
 */
function provide(token, _a) {
    var useClass = _a.useClass, useValue = _a.useValue, useExisting = _a.useExisting, useFactory = _a.useFactory, deps = _a.deps, multi = _a.multi;
    return new Provider(token, {
        useClass: useClass,
        useValue: useValue,
        useExisting: useExisting,
        useFactory: useFactory,
        deps: deps,
        multi: multi
    });
}
exports.provide = provide;
//# sourceMappingURL=provider.js.map