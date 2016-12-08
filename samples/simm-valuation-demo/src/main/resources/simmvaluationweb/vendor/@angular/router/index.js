/**
 * @license
 * Copyright Google Inc. All Rights Reserved.
 *
 * Use of this source code is governed by an MIT-style license that can be
 * found in the LICENSE file at https://angular.io/license
 */
"use strict";
var router_link_1 = require('./src/directives/router_link');
var router_link_active_1 = require('./src/directives/router_link_active');
var router_outlet_1 = require('./src/directives/router_outlet');
var router_link_2 = require('./src/directives/router_link');
exports.RouterLink = router_link_2.RouterLink;
exports.RouterLinkWithHref = router_link_2.RouterLinkWithHref;
var router_link_active_2 = require('./src/directives/router_link_active');
exports.RouterLinkActive = router_link_active_2.RouterLinkActive;
var router_outlet_2 = require('./src/directives/router_outlet');
exports.RouterOutlet = router_outlet_2.RouterOutlet;
var router_1 = require('./src/router');
exports.NavigationCancel = router_1.NavigationCancel;
exports.NavigationEnd = router_1.NavigationEnd;
exports.NavigationError = router_1.NavigationError;
exports.NavigationStart = router_1.NavigationStart;
exports.Router = router_1.Router;
exports.RoutesRecognized = router_1.RoutesRecognized;
var router_outlet_map_1 = require('./src/router_outlet_map');
exports.RouterOutletMap = router_outlet_map_1.RouterOutletMap;
var router_providers_1 = require('./src/router_providers');
exports.provideRouter = router_providers_1.provideRouter;
var router_state_1 = require('./src/router_state');
exports.ActivatedRoute = router_state_1.ActivatedRoute;
exports.ActivatedRouteSnapshot = router_state_1.ActivatedRouteSnapshot;
exports.RouterState = router_state_1.RouterState;
exports.RouterStateSnapshot = router_state_1.RouterStateSnapshot;
var shared_1 = require('./src/shared');
exports.PRIMARY_OUTLET = shared_1.PRIMARY_OUTLET;
var url_tree_1 = require('./src/url_tree');
exports.DefaultUrlSerializer = url_tree_1.DefaultUrlSerializer;
exports.UrlPathWithParams = url_tree_1.UrlPathWithParams;
exports.UrlSerializer = url_tree_1.UrlSerializer;
exports.UrlTree = url_tree_1.UrlTree;
/**
 * @stable
 */
exports.ROUTER_DIRECTIVES = [router_outlet_1.RouterOutlet, router_link_1.RouterLink, router_link_1.RouterLinkWithHref, router_link_active_1.RouterLinkActive];
//# sourceMappingURL=index.js.map