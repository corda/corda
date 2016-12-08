/**
 * @license
 * Copyright Google Inc. All Rights Reserved.
 *
 * Use of this source code is governed by an MIT-style license that can be
 * found in the LICENSE file at https://angular.io/license
 */
"use strict";
var BehaviorSubject_1 = require('rxjs/BehaviorSubject');
var router_state_1 = require('./router_state');
var tree_1 = require('./utils/tree');
function createRouterState(curr, prevState) {
    var root = createNode(curr._root, prevState ? prevState._root : undefined);
    var queryParams = prevState ? prevState.queryParams : new BehaviorSubject_1.BehaviorSubject(curr.queryParams);
    var fragment = prevState ? prevState.fragment : new BehaviorSubject_1.BehaviorSubject(curr.fragment);
    return new router_state_1.RouterState(root, queryParams, fragment, curr);
}
exports.createRouterState = createRouterState;
function createNode(curr, prevState) {
    if (prevState && equalRouteSnapshots(prevState.value.snapshot, curr.value)) {
        var value = prevState.value;
        value._futureSnapshot = curr.value;
        var children = createOrReuseChildren(curr, prevState);
        return new tree_1.TreeNode(value, children);
    }
    else {
        var value = createActivatedRoute(curr.value);
        var children = curr.children.map(function (c) { return createNode(c); });
        return new tree_1.TreeNode(value, children);
    }
}
function createOrReuseChildren(curr, prevState) {
    return curr.children.map(function (child) {
        for (var _i = 0, _a = prevState.children; _i < _a.length; _i++) {
            var p = _a[_i];
            if (equalRouteSnapshots(p.value.snapshot, child.value)) {
                return createNode(child, p);
            }
        }
        return createNode(child);
    });
}
function createActivatedRoute(c) {
    return new router_state_1.ActivatedRoute(new BehaviorSubject_1.BehaviorSubject(c.url), new BehaviorSubject_1.BehaviorSubject(c.params), new BehaviorSubject_1.BehaviorSubject(c.data), c.outlet, c.component, c);
}
function equalRouteSnapshots(a, b) {
    return a._routeConfig === b._routeConfig;
}
//# sourceMappingURL=create_router_state.js.map