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
var BehaviorSubject_1 = require('rxjs/BehaviorSubject');
var shared_1 = require('./shared');
var url_tree_1 = require('./url_tree');
var collection_1 = require('./utils/collection');
var tree_1 = require('./utils/tree');
/**
 * The state of the router.
 *
 * ### Usage
 *
 * ```
 * class MyComponent {
 *   constructor(router: Router) {
 *     const state = router.routerState;
 *     const id: Observable<string> = state.firstChild(state.root).params.map(p => p.id);
 *     const isDebug: Observable<string> = state.queryParams.map(q => q.debug);
 *   }
 * }
 * ```
 *
 * @stable
 */
var RouterState = (function (_super) {
    __extends(RouterState, _super);
    /**
     * @internal
     */
    function RouterState(root, queryParams, fragment, snapshot) {
        _super.call(this, root);
        this.queryParams = queryParams;
        this.fragment = fragment;
        this.snapshot = snapshot;
    }
    RouterState.prototype.toString = function () { return this.snapshot.toString(); };
    return RouterState;
}(tree_1.Tree));
exports.RouterState = RouterState;
function createEmptyState(urlTree, rootComponent) {
    var snapshot = createEmptyStateSnapshot(urlTree, rootComponent);
    var emptyUrl = new BehaviorSubject_1.BehaviorSubject([new url_tree_1.UrlPathWithParams('', {})]);
    var emptyParams = new BehaviorSubject_1.BehaviorSubject({});
    var emptyData = new BehaviorSubject_1.BehaviorSubject({});
    var emptyQueryParams = new BehaviorSubject_1.BehaviorSubject({});
    var fragment = new BehaviorSubject_1.BehaviorSubject('');
    var activated = new ActivatedRoute(emptyUrl, emptyParams, emptyData, shared_1.PRIMARY_OUTLET, rootComponent, snapshot.root);
    activated.snapshot = snapshot.root;
    return new RouterState(new tree_1.TreeNode(activated, []), emptyQueryParams, fragment, snapshot);
}
exports.createEmptyState = createEmptyState;
function createEmptyStateSnapshot(urlTree, rootComponent) {
    var emptyParams = {};
    var emptyData = {};
    var emptyQueryParams = {};
    var fragment = '';
    var activated = new ActivatedRouteSnapshot([], emptyParams, emptyData, shared_1.PRIMARY_OUTLET, rootComponent, null, urlTree.root, -1, InheritedResolve.empty);
    return new RouterStateSnapshot('', new tree_1.TreeNode(activated, []), emptyQueryParams, fragment);
}
/**
 * Contains the information about a component loaded in an outlet. The information is provided
 * through the params, urlSegments, and data observables.
 *
 * ### Usage
 *
 * ```
 * class MyComponent {
 *   constructor(route: ActivatedRoute) {
 *     const id: Observable<string> = route.params.map(p => p.id);
 *     const data = route.data.map(d => d.user); //includes `data` and `resolve`
 *   }
 * }
 * ```
 *
 * @stable
 */
var ActivatedRoute = (function () {
    /**
     * @internal
     */
    function ActivatedRoute(url, params, data, outlet, component, futureSnapshot) {
        this.url = url;
        this.params = params;
        this.data = data;
        this.outlet = outlet;
        this.component = component;
        this._futureSnapshot = futureSnapshot;
    }
    ActivatedRoute.prototype.toString = function () {
        return this.snapshot ? this.snapshot.toString() : "Future(" + this._futureSnapshot + ")";
    };
    return ActivatedRoute;
}());
exports.ActivatedRoute = ActivatedRoute;
/**
 * @internal
 */
var InheritedResolve = (function () {
    function InheritedResolve(parent, current) {
        this.parent = parent;
        this.current = current;
        /**
         * @internal
         */
        this.resolvedData = {};
    }
    Object.defineProperty(InheritedResolve.prototype, "flattenedResolvedData", {
        /**
         * @internal
         */
        get: function () {
            return this.parent ? collection_1.merge(this.parent.flattenedResolvedData, this.resolvedData) :
                this.resolvedData;
        },
        enumerable: true,
        configurable: true
    });
    Object.defineProperty(InheritedResolve, "empty", {
        get: function () { return new InheritedResolve(null, {}); },
        enumerable: true,
        configurable: true
    });
    return InheritedResolve;
}());
exports.InheritedResolve = InheritedResolve;
/**
 * Contains the information about a component loaded in an outlet at a particular moment in time.
 *
 * ### Usage
 *
 * ```
 * class MyComponent {
 *   constructor(route: ActivatedRoute) {
 *     const id: string = route.snapshot.params.id;
 *     const data = route.snapshot.data;
 *   }
 * }
 * ```
 *
 * @stable
 */
var ActivatedRouteSnapshot = (function () {
    /**
     * @internal
     */
    function ActivatedRouteSnapshot(url, params, data, outlet, component, routeConfig, urlSegment, lastPathIndex, resolve) {
        this.url = url;
        this.params = params;
        this.data = data;
        this.outlet = outlet;
        this.component = component;
        this._routeConfig = routeConfig;
        this._urlSegment = urlSegment;
        this._lastPathIndex = lastPathIndex;
        this._resolve = resolve;
    }
    ActivatedRouteSnapshot.prototype.toString = function () {
        var url = this.url.map(function (s) { return s.toString(); }).join('/');
        var matched = this._routeConfig ? this._routeConfig.path : '';
        return "Route(url:'" + url + "', path:'" + matched + "')";
    };
    return ActivatedRouteSnapshot;
}());
exports.ActivatedRouteSnapshot = ActivatedRouteSnapshot;
/**
 * The state of the router at a particular moment in time.
 *
 * ### Usage
 *
 * ```
 * class MyComponent {
 *   constructor(router: Router) {
 *     const snapshot = router.routerState.snapshot;
 *   }
 * }
 * ```
 *
 * @stable
 */
var RouterStateSnapshot = (function (_super) {
    __extends(RouterStateSnapshot, _super);
    /**
     * @internal
     */
    function RouterStateSnapshot(url, root, queryParams, fragment) {
        _super.call(this, root);
        this.url = url;
        this.queryParams = queryParams;
        this.fragment = fragment;
    }
    RouterStateSnapshot.prototype.toString = function () { return serializeNode(this._root); };
    return RouterStateSnapshot;
}(tree_1.Tree));
exports.RouterStateSnapshot = RouterStateSnapshot;
function serializeNode(node) {
    var c = node.children.length > 0 ? " { " + node.children.map(serializeNode).join(", ") + " } " : '';
    return "" + node.value + c;
}
/**
 * The expectation is that the activate route is created with the right set of parameters.
 * So we push new values into the observables only when they are not the initial values.
 * And we detect that by checking if the snapshot field is set.
 */
function advanceActivatedRoute(route) {
    if (route.snapshot) {
        if (!collection_1.shallowEqual(route.snapshot.params, route._futureSnapshot.params)) {
            route.params.next(route._futureSnapshot.params);
            route.data.next(route._futureSnapshot.data);
        }
        if (!collection_1.shallowEqualArrays(route.snapshot.url, route._futureSnapshot.url)) {
            route.url.next(route._futureSnapshot.url);
        }
        route.snapshot = route._futureSnapshot;
    }
    else {
        route.snapshot = route._futureSnapshot;
        route.data.next(route._futureSnapshot.data);
    }
}
exports.advanceActivatedRoute = advanceActivatedRoute;
//# sourceMappingURL=router_state.js.map