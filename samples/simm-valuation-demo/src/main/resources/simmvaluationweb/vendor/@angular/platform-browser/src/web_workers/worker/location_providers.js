/**
 * @license
 * Copyright Google Inc. All Rights Reserved.
 *
 * Use of this source code is governed by an MIT-style license that can be
 * found in the LICENSE file at https://angular.io/license
 */
"use strict";
var common_1 = require('@angular/common');
var core_1 = require('@angular/core');
var platform_location_1 = require('./platform_location');
/**
 * Those providers should be added when the router is used in a worker context in addition to the
 * {@link ROUTER_PROVIDERS} and after them.
 * @experimental
 */
exports.WORKER_APP_LOCATION_PROVIDERS = [
    { provide: common_1.PlatformLocation, useClass: platform_location_1.WebWorkerPlatformLocation }, {
        provide: core_1.APP_INITIALIZER,
        useFactory: appInitFnFactory,
        multi: true,
        deps: [common_1.PlatformLocation, core_1.NgZone]
    }
];
function appInitFnFactory(platformLocation, zone) {
    return function () { return zone.runGuarded(function () { return platformLocation.init(); }); };
}
//# sourceMappingURL=location_providers.js.map