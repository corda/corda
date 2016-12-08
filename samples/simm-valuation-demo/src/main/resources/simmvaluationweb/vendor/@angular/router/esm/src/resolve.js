/**
 * @license
 * Copyright Google Inc. All Rights Reserved.
 *
 * Use of this source code is governed by an MIT-style license that can be
 * found in the LICENSE file at https://angular.io/license
 */
import 'rxjs/add/operator/map';
import 'rxjs/add/operator/toPromise';
import { forkJoin } from 'rxjs/observable/forkJoin';
import { fromPromise } from 'rxjs/observable/fromPromise';
export function resolve(resolver, state) {
    return resolveNode(resolver, state._root).map(_ => state);
}
function resolveNode(resolver, node) {
    if (node.children.length === 0) {
        return fromPromise(resolveComponent(resolver, node.value).then(factory => {
            node.value._resolvedComponentFactory = factory;
            return node.value;
        }));
    }
    else {
        const c = node.children.map(c => resolveNode(resolver, c).toPromise());
        return forkJoin(c).map(_ => resolveComponent(resolver, node.value).then(factory => {
            node.value._resolvedComponentFactory = factory;
            return node.value;
        }));
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