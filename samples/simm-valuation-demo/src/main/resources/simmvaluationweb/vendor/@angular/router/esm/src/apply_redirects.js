/**
 * @license
 * Copyright Google Inc. All Rights Reserved.
 *
 * Use of this source code is governed by an MIT-style license that can be
 * found in the LICENSE file at https://angular.io/license
 */
import { Observable } from 'rxjs/Observable';
import { of } from 'rxjs/observable/of';
import { PRIMARY_OUTLET } from './shared';
import { UrlPathWithParams, UrlSegment, UrlTree, mapChildren } from './url_tree';
import { merge } from './utils/collection';
class NoMatch {
    constructor(segment = null) {
        this.segment = segment;
    }
}
class GlobalRedirect {
    constructor(paths) {
        this.paths = paths;
    }
}
export function applyRedirects(urlTree, config) {
    try {
        return createUrlTree(urlTree, expandSegment(config, urlTree.root, PRIMARY_OUTLET));
    }
    catch (e) {
        if (e instanceof GlobalRedirect) {
            return createUrlTree(urlTree, new UrlSegment([], { [PRIMARY_OUTLET]: new UrlSegment(e.paths, {}) }));
        }
        else if (e instanceof NoMatch) {
            return new Observable((obs) => obs.error(new Error(`Cannot match any routes: '${e.segment}'`)));
        }
        else {
            return new Observable((obs) => obs.error(e));
        }
    }
}
function createUrlTree(urlTree, rootCandidate) {
    const root = rootCandidate.pathsWithParams.length > 0 ?
        new UrlSegment([], { [PRIMARY_OUTLET]: rootCandidate }) :
        rootCandidate;
    return of(new UrlTree(root, urlTree.queryParams, urlTree.fragment));
}
function expandSegment(routes, segment, outlet) {
    if (segment.pathsWithParams.length === 0 && segment.hasChildren()) {
        return new UrlSegment([], expandSegmentChildren(routes, segment));
    }
    else {
        return expandPathsWithParams(segment, routes, segment.pathsWithParams, outlet, true);
    }
}
function expandSegmentChildren(routes, segment) {
    return mapChildren(segment, (child, childOutlet) => expandSegment(routes, child, childOutlet));
}
function expandPathsWithParams(segment, routes, paths, outlet, allowRedirects) {
    for (let r of routes) {
        try {
            return expandPathsWithParamsAgainstRoute(segment, routes, r, paths, outlet, allowRedirects);
        }
        catch (e) {
            if (!(e instanceof NoMatch))
                throw e;
        }
    }
    throw new NoMatch(segment);
}
function expandPathsWithParamsAgainstRoute(segment, routes, route, paths, outlet, allowRedirects) {
    if (getOutlet(route) !== outlet)
        throw new NoMatch();
    if (route.redirectTo !== undefined && !allowRedirects)
        throw new NoMatch();
    if (route.redirectTo !== undefined) {
        return expandPathsWithParamsAgainstRouteUsingRedirect(segment, routes, route, paths, outlet);
    }
    else {
        return matchPathsWithParamsAgainstRoute(segment, route, paths);
    }
}
function expandPathsWithParamsAgainstRouteUsingRedirect(segment, routes, route, paths, outlet) {
    if (route.path === '**') {
        return expandWildCardWithParamsAgainstRouteUsingRedirect(route);
    }
    else {
        return expandRegularPathWithParamsAgainstRouteUsingRedirect(segment, routes, route, paths, outlet);
    }
}
function expandWildCardWithParamsAgainstRouteUsingRedirect(route) {
    const newPaths = applyRedirectCommands([], route.redirectTo, {});
    if (route.redirectTo.startsWith('/')) {
        throw new GlobalRedirect(newPaths);
    }
    else {
        return new UrlSegment(newPaths, {});
    }
}
function expandRegularPathWithParamsAgainstRouteUsingRedirect(segment, routes, route, paths, outlet) {
    const { consumedPaths, lastChild, positionalParamSegments } = match(segment, route, paths);
    const newPaths = applyRedirectCommands(consumedPaths, route.redirectTo, positionalParamSegments);
    if (route.redirectTo.startsWith('/')) {
        throw new GlobalRedirect(newPaths);
    }
    else {
        return expandPathsWithParams(segment, routes, newPaths.concat(paths.slice(lastChild)), outlet, false);
    }
}
function matchPathsWithParamsAgainstRoute(rawSegment, route, paths) {
    if (route.path === '**') {
        return new UrlSegment(paths, {});
    }
    else {
        const { consumedPaths, lastChild } = match(rawSegment, route, paths);
        const childConfig = route.children ? route.children : [];
        const rawSlicedPath = paths.slice(lastChild);
        const { segment, slicedPath } = split(rawSegment, consumedPaths, rawSlicedPath, childConfig);
        if (slicedPath.length === 0 && segment.hasChildren()) {
            const children = expandSegmentChildren(childConfig, segment);
            return new UrlSegment(consumedPaths, children);
        }
        else if (childConfig.length === 0 && slicedPath.length === 0) {
            return new UrlSegment(consumedPaths, {});
        }
        else {
            const cs = expandPathsWithParams(segment, childConfig, slicedPath, PRIMARY_OUTLET, true);
            return new UrlSegment(consumedPaths.concat(cs.pathsWithParams), cs.children);
        }
    }
}
function match(segment, route, paths) {
    if (route.path === '') {
        if ((route.terminal || route.pathMatch === 'full') &&
            (segment.hasChildren() || paths.length > 0)) {
            throw new NoMatch();
        }
        else {
            return { consumedPaths: [], lastChild: 0, positionalParamSegments: {} };
        }
    }
    const path = route.path;
    const parts = path.split('/');
    const positionalParamSegments = {};
    const consumedPaths = [];
    let currentIndex = 0;
    for (let i = 0; i < parts.length; ++i) {
        if (currentIndex >= paths.length)
            throw new NoMatch();
        const current = paths[currentIndex];
        const p = parts[i];
        const isPosParam = p.startsWith(':');
        if (!isPosParam && p !== current.path)
            throw new NoMatch();
        if (isPosParam) {
            positionalParamSegments[p.substring(1)] = current;
        }
        consumedPaths.push(current);
        currentIndex++;
    }
    if (route.terminal && (segment.hasChildren() || currentIndex < paths.length)) {
        throw new NoMatch();
    }
    return { consumedPaths, lastChild: currentIndex, positionalParamSegments };
}
function applyRedirectCommands(paths, redirectTo, posParams) {
    const r = redirectTo.startsWith('/') ? redirectTo.substring(1) : redirectTo;
    if (r === '') {
        return [];
    }
    else {
        return createPaths(redirectTo, r.split('/'), paths, posParams);
    }
}
function createPaths(redirectTo, parts, segments, posParams) {
    return parts.map(p => p.startsWith(':') ? findPosParam(p, posParams, redirectTo) :
        findOrCreatePath(p, segments));
}
function findPosParam(part, posParams, redirectTo) {
    const paramName = part.substring(1);
    const pos = posParams[paramName];
    if (!pos)
        throw new Error(`Cannot redirect to '${redirectTo}'. Cannot find '${part}'.`);
    return pos;
}
function findOrCreatePath(part, paths) {
    let idx = 0;
    for (const s of paths) {
        if (s.path === part) {
            paths.splice(idx);
            return s;
        }
        idx++;
    }
    return new UrlPathWithParams(part, {});
}
function split(segment, consumedPaths, slicedPath, config) {
    if (slicedPath.length > 0 &&
        containsEmptyPathRedirectsWithNamedOutlets(segment, slicedPath, config)) {
        const s = new UrlSegment(consumedPaths, createChildrenForEmptyPaths(config, new UrlSegment(slicedPath, segment.children)));
        return { segment: mergeTrivialChildren(s), slicedPath: [] };
    }
    else if (slicedPath.length === 0 && containsEmptyPathRedirects(segment, slicedPath, config)) {
        const s = new UrlSegment(segment.pathsWithParams, addEmptyPathsToChildrenIfNeeded(segment, slicedPath, config, segment.children));
        return { segment: mergeTrivialChildren(s), slicedPath };
    }
    else {
        return { segment, slicedPath };
    }
}
function mergeTrivialChildren(s) {
    if (s.numberOfChildren === 1 && s.children[PRIMARY_OUTLET]) {
        const c = s.children[PRIMARY_OUTLET];
        return new UrlSegment(s.pathsWithParams.concat(c.pathsWithParams), c.children);
    }
    else {
        return s;
    }
}
function addEmptyPathsToChildrenIfNeeded(segment, slicedPath, routes, children) {
    const res = {};
    for (let r of routes) {
        if (emptyPathRedirect(segment, slicedPath, r) && !children[getOutlet(r)]) {
            res[getOutlet(r)] = new UrlSegment([], {});
        }
    }
    return merge(children, res);
}
function createChildrenForEmptyPaths(routes, primarySegment) {
    const res = {};
    res[PRIMARY_OUTLET] = primarySegment;
    for (let r of routes) {
        if (r.path === '') {
            res[getOutlet(r)] = new UrlSegment([], {});
        }
    }
    return res;
}
function containsEmptyPathRedirectsWithNamedOutlets(segment, slicedPath, routes) {
    return routes
        .filter(r => emptyPathRedirect(segment, slicedPath, r) && getOutlet(r) !== PRIMARY_OUTLET)
        .length > 0;
}
function containsEmptyPathRedirects(segment, slicedPath, routes) {
    return routes.filter(r => emptyPathRedirect(segment, slicedPath, r)).length > 0;
}
function emptyPathRedirect(segment, slicedPath, r) {
    if ((segment.hasChildren() || slicedPath.length > 0) && (r.terminal || r.pathMatch === 'full'))
        return false;
    return r.path === '' && r.redirectTo !== undefined;
}
function getOutlet(route) {
    return route.outlet ? route.outlet : PRIMARY_OUTLET;
}
//# sourceMappingURL=apply_redirects.js.map