/**
 * @license
 * Copyright Google Inc. All Rights Reserved.
 *
 * Use of this source code is governed by an MIT-style license that can be
 * found in the LICENSE file at https://angular.io/license
 */
import { BehaviorSubject } from 'rxjs/BehaviorSubject';
import { PRIMARY_OUTLET } from './shared';
import { UrlPathWithParams } from './url_tree';
import { merge, shallowEqual, shallowEqualArrays } from './utils/collection';
import { Tree, TreeNode } from './utils/tree';
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
export class RouterState extends Tree {
    /**
     * @internal
     */
    constructor(root, queryParams, fragment, snapshot) {
        super(root);
        this.queryParams = queryParams;
        this.fragment = fragment;
        this.snapshot = snapshot;
    }
    toString() { return this.snapshot.toString(); }
}
export function createEmptyState(urlTree, rootComponent) {
    const snapshot = createEmptyStateSnapshot(urlTree, rootComponent);
    const emptyUrl = new BehaviorSubject([new UrlPathWithParams('', {})]);
    const emptyParams = new BehaviorSubject({});
    const emptyData = new BehaviorSubject({});
    const emptyQueryParams = new BehaviorSubject({});
    const fragment = new BehaviorSubject('');
    const activated = new ActivatedRoute(emptyUrl, emptyParams, emptyData, PRIMARY_OUTLET, rootComponent, snapshot.root);
    activated.snapshot = snapshot.root;
    return new RouterState(new TreeNode(activated, []), emptyQueryParams, fragment, snapshot);
}
function createEmptyStateSnapshot(urlTree, rootComponent) {
    const emptyParams = {};
    const emptyData = {};
    const emptyQueryParams = {};
    const fragment = '';
    const activated = new ActivatedRouteSnapshot([], emptyParams, emptyData, PRIMARY_OUTLET, rootComponent, null, urlTree.root, -1, InheritedResolve.empty);
    return new RouterStateSnapshot('', new TreeNode(activated, []), emptyQueryParams, fragment);
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
export class ActivatedRoute {
    /**
     * @internal
     */
    constructor(url, params, data, outlet, component, futureSnapshot) {
        this.url = url;
        this.params = params;
        this.data = data;
        this.outlet = outlet;
        this.component = component;
        this._futureSnapshot = futureSnapshot;
    }
    toString() {
        return this.snapshot ? this.snapshot.toString() : `Future(${this._futureSnapshot})`;
    }
}
/**
 * @internal
 */
export class InheritedResolve {
    constructor(parent, current) {
        this.parent = parent;
        this.current = current;
        /**
         * @internal
         */
        this.resolvedData = {};
    }
    /**
     * @internal
     */
    get flattenedResolvedData() {
        return this.parent ? merge(this.parent.flattenedResolvedData, this.resolvedData) :
            this.resolvedData;
    }
    static get empty() { return new InheritedResolve(null, {}); }
}
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
export class ActivatedRouteSnapshot {
    /**
     * @internal
     */
    constructor(url, params, data, outlet, component, routeConfig, urlSegment, lastPathIndex, resolve) {
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
    toString() {
        const url = this.url.map(s => s.toString()).join('/');
        const matched = this._routeConfig ? this._routeConfig.path : '';
        return `Route(url:'${url}', path:'${matched}')`;
    }
}
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
export class RouterStateSnapshot extends Tree {
    /**
     * @internal
     */
    constructor(url, root, queryParams, fragment) {
        super(root);
        this.url = url;
        this.queryParams = queryParams;
        this.fragment = fragment;
    }
    toString() { return serializeNode(this._root); }
}
function serializeNode(node) {
    const c = node.children.length > 0 ? ` { ${node.children.map(serializeNode).join(", ")} } ` : '';
    return `${node.value}${c}`;
}
/**
 * The expectation is that the activate route is created with the right set of parameters.
 * So we push new values into the observables only when they are not the initial values.
 * And we detect that by checking if the snapshot field is set.
 */
export function advanceActivatedRoute(route) {
    if (route.snapshot) {
        if (!shallowEqual(route.snapshot.params, route._futureSnapshot.params)) {
            route.params.next(route._futureSnapshot.params);
            route.data.next(route._futureSnapshot.data);
        }
        if (!shallowEqualArrays(route.snapshot.url, route._futureSnapshot.url)) {
            route.url.next(route._futureSnapshot.url);
        }
        route.snapshot = route._futureSnapshot;
    }
    else {
        route.snapshot = route._futureSnapshot;
        route.data.next(route._futureSnapshot.data);
    }
}
//# sourceMappingURL=router_state.js.map