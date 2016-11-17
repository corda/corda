/**
 * @license
 * Copyright Google Inc. All Rights Reserved.
 *
 * Use of this source code is governed by an MIT-style license that can be
 * found in the LICENSE file at https://angular.io/license
 */
"use strict";
require('rxjs/add/operator/map');
require('rxjs/add/operator/toPromise');
var forkJoin_1 = require('rxjs/observable/forkJoin');
var fromPromise_1 = require('rxjs/observable/fromPromise');
function resolve(resolver, state) {
    return resolveNode(resolver, state._root).map(function (_) { return state; });
}
exports.resolve = resolve;
function resolveNode(resolver, node) {
    if (node.children.length === 0) {
        return fromPromise_1.fromPromise(resolveComponent(resolver, node.value).then(function (factory) {
            node.value._resolvedComponentFactory = factory;
            return node.value;
        }));
    }
    else {
        var c = node.children.map(function (c) { return resolveNode(resolver, c).toPromise(); });
        return forkJoin_1.forkJoin(c).map(function (_) { return resolveComponent(resolver, node.value).then(function (factory) {
            node.value._resolvedComponentFactory = factory;
            return node.value;
        }); });
    }
}
function resolveComponent(resolver, snapshot) {
    // TODO: vsavkin change to typeof snapshot.component === 'string' in beta2
    if (snapshot.component && snapshot._routeConfig) {
        return resolver.resolveComponent(snapshot.component);
    }
    else {
        return Promise.resolve(null);
    }
}
//# sourceMappingURL=resolve.js.map