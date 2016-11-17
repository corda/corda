/**
 * @license
 * Copyright Google Inc. All Rights Reserved.
 *
 * Use of this source code is governed by an MIT-style license that can be
 * found in the LICENSE file at https://angular.io/license
 */
"use strict";
function validateConfig(config) {
    config.forEach(validateNode);
}
exports.validateConfig = validateConfig;
function validateNode(route) {
    if (!!route.redirectTo && !!route.children) {
        throw new Error("Invalid configuration of route '" + route.path + "': redirectTo and children cannot be used together");
    }
    if (!!route.redirectTo && !!route.component) {
        throw new Error("Invalid configuration of route '" + route.path + "': redirectTo and component cannot be used together");
    }
    if (route.redirectTo === undefined && !route.component && !route.children) {
        throw new Error("Invalid configuration of route '" + route.path + "': component, redirectTo, children must be provided");
    }
    if (route.path === undefined) {
        throw new Error("Invalid route configuration: routes must have path specified");
    }
    if (route.path.startsWith('/')) {
        throw new Error("Invalid route configuration of route '" + route.path + "': path cannot start with a slash");
    }
    if (route.path === '' && route.redirectTo !== undefined &&
        (route.terminal === undefined && route.pathMatch === undefined)) {
        var exp = "The default value of 'pathMatch' is 'prefix', but often the intent is to use 'full'.";
        throw new Error("Invalid route configuration of route '{path: \"" + route.path + "\", redirectTo: \"" + route.redirectTo + "\"}': please provide 'pathMatch'. " + exp);
    }
}
//# sourceMappingURL=config.js.map