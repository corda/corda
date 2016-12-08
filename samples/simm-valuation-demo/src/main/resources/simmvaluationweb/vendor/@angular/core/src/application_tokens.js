/**
 * @license
 * Copyright Google Inc. All Rights Reserved.
 *
 * Use of this source code is governed by an MIT-style license that can be
 * found in the LICENSE file at https://angular.io/license
 */
"use strict";
var lang_1 = require('../src/facade/lang');
var di_1 = require('./di');
/**
 * A DI Token representing a unique string id assigned to the application by Angular and used
 * primarily for prefixing application attributes and CSS styles when
 * {@link ViewEncapsulation#Emulated} is being used.
 *
 * If you need to avoid randomly generated value to be used as an application id, you can provide
 * a custom value via a DI provider <!-- TODO: provider --> configuring the root {@link Injector}
 * using this token.
 * @experimental
 */
exports.APP_ID = new di_1.OpaqueToken('AppId');
function _appIdRandomProviderFactory() {
    return "" + _randomChar() + _randomChar() + _randomChar();
}
/**
 * Providers that will generate a random APP_ID_TOKEN.
 * @experimental
 */
exports.APP_ID_RANDOM_PROVIDER = 
/*@ts2dart_const*/ /* @ts2dart_Provider */ {
    provide: exports.APP_ID,
    useFactory: _appIdRandomProviderFactory,
    deps: []
};
function _randomChar() {
    return lang_1.StringWrapper.fromCharCode(97 + lang_1.Math.floor(lang_1.Math.random() * 25));
}
/**
 * A function that will be executed when a platform is initialized.
 * @experimental
 */
exports.PLATFORM_INITIALIZER = 
/*@ts2dart_const*/ new di_1.OpaqueToken('Platform Initializer');
/**
 * A function that will be executed when an application is initialized.
 * @experimental
 */
exports.APP_INITIALIZER = 
/*@ts2dart_const*/ new di_1.OpaqueToken('Application Initializer');
/**
 * A token which indicates the root directory of the application
 * @experimental
 */
exports.PACKAGE_ROOT_URL = 
/*@ts2dart_const*/ new di_1.OpaqueToken('Application Packages Root URL');
//# sourceMappingURL=application_tokens.js.map