/**
 * @license
 * Copyright Google Inc. All Rights Reserved.
 *
 * Use of this source code is governed by an MIT-style license that can be
 * found in the LICENSE file at https://angular.io/license
 */
"use strict";
var Observable_1 = require('rxjs/Observable');
var of_1 = require('rxjs/observable/of');
var shared_1 = require('./shared');
var url_tree_1 = require('./url_tree');
var collection_1 = require('./utils/collection');
var NoMatch = (function () {
    function NoMatch(segment) {
        if (segment === void 0) { segment = null; }
        this.segment = segment;
    }
    return NoMatch;
}());
var GlobalRedirect = (function () {
    function GlobalRedirect(paths) {
        this.paths = paths;
    }
    return GlobalRedirect;
}());
function applyRedirects(urlTree, config) {
    try {
        return createUrlTree(urlTree, expandSegment(config, urlTree.root, shared_1.PRIMARY_OUTLET));
    }
    catch (e) {
        if (e instanceof GlobalRedirect) {
            return createUrlTree(urlTree, new url_tree_1.UrlSegment([], (_a = {}, _a[shared_1.PRIMARY_OUTLET] = new url_tree_1.UrlSegment(e.paths, {}), _a)));
        }
        else if (e instanceof NoMatch) {
            return new Observable_1.Observable(function (obs) {
                return obs.error(new Error("Cannot match any routes: '" + e.segment + "'"));
            });
        }
        else {
            return new Observable_1.Observable(function (obs) { return obs.error(e); });
        }
    }
    var _a;
}
exports.applyRedirects = applyRedirects;
function createUrlTree(urlTree, rootCandidate) {
    var root = rootCandidate.pathsWithParams.length > 0 ?
        new url_tree_1.UrlSegment([], (_a = {}, _a[shared_1.PRIMARY_OUTLET] = rootCandidate, _a)) :
        rootCandidate;
    return of_1.of(new url_tree_1.UrlTree(root, urlTree.queryParams, urlTree.fragment));
    var _a;
}
function expandSegment(routes, segment, outlet) {
    if (segment.pathsWithParams.length === 0 && segment.hasChildren()) {
        return new url_tree_1.UrlSegment([], expandSegmentChildren(routes, segment));
    }
    else {
        return expandPathsWithParams(segment, routes, segment.pathsWithParams, outlet, true);
    }
}
function expandSegmentChildren(routes, segment) {
    return url_tree_1.mapChildren(segment, function (child, childOutlet) { return expandSegment(routes, child, childOutlet); });
}
function expandPathsWithParams(segment, routes, paths, outlet, allowRedirects) {
    for (var _i = 0, routes_1 = routes; _i < routes_1.length; _i++) {
        var r = routes_1[_i];
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
    var newPaths = applyRedirectCommands([], route.redirectTo, {});
    if (route.redirectTo.startsWith('/')) {
        throw new GlobalRedirect(newPaths);
    }
    else {
        return new url_tree_1.UrlSegment(newPaths, {});
    }
}
function expandRegularPathWithParamsAgainstRouteUsingRedirect(segment, routes, route, paths, outlet) {
    var _a = match(segment, route, paths), consumedPaths = _a.consumedPaths, lastChild = _a.lastChild, positionalParamSegments = _a.positionalParamSegments;
    var newPaths = applyRedirectCommands(consumedPaths, route.redirectTo, positionalParamSegments);
    if (route.redirectTo.startsWith('/')) {
        throw new GlobalRedirect(newPaths);
    }
    else {
        return expandPathsWithParams(segment, routes, newPaths.concat(paths.slice(lastChild)), outlet, false);
    }
}
function matchPathsWithParamsAgainstRoute(rawSegment, route, paths) {
    if (route.path === '**') {
        return new url_tree_1.UrlSegment(paths, {});
    }
    else {
        var _a = match(rawSegment, route, paths), consumedPaths = _a.consumedPaths, lastChild = _a.lastChild;
        var childConfig = route.children ? route.children : [];
        var rawSlicedPath = paths.slice(lastChild);
        var _b = split(rawSegment, consumedPaths, rawSlicedPath, childConfig), segment = _b.segment, slicedPath = _b.slicedPath;
        if (slicedPath.length === 0 && segment.hasChildren()) {
            var children = expandSegmentChildren(childConfig, segment);
            return new url_tree_1.UrlSegment(consumedPaths, children);
        }
        else if (childConfig.length === 0 && slicedPath.length === 0) {
            return new url_tree_1.UrlSegment(consumedPaths, {});
        }
        else {
            var cs = expandPathsWithParams(segment, childConfig, slicedPath, shared_1.PRIMARY_OUTLET, true);
            return new url_tree_1.UrlSegment(consumedPaths.concat(cs.pathsWithParams), cs.children);
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
    var path = route.path;
    var parts = path.split('/');
    var positionalParamSegments = {};
    var consumedPaths = [];
    var currentIndex = 0;
    for (var i = 0; i < parts.length; ++i) {
        if (currentIndex >= paths.length)
            throw new NoMatch();
        var current = paths[currentIndex];
        var p = parts[i];
        var isPosParam = p.startsWith(':');
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
    return { consumedPaths: consumedPaths, lastChild: currentIndex, positionalParamSegments: positionalParamSegments };
}
function applyRedirectCommands(paths, redirectTo, posParams) {
    var r = redirectTo.startsWith('/') ? redirectTo.substring(1) : redirectTo;
    if (r === '') {
        return [];
    }
    else {
        return createPaths(redirectTo, r.split('/'), paths, posParams);
    }
}
function createPaths(redirectTo, parts, segments, posParams) {
    return parts.map(function (p) { return p.startsWith(':') ? findPosParam(p, posParams, redirectTo) :
        findOrCreatePath(p, segments); });
}
function findPosParam(part, posParams, redirectTo) {
    var paramName = part.substring(1);
    var pos = posParams[paramName];
    if (!pos)
        throw new Error("Cannot redirect to '" + redirectTo + "'. Cannot find '" + part + "'.");
    return pos;
}
function findOrCreatePath(part, paths) {
    var idx = 0;
    for (var _i = 0, paths_1 = paths; _i < paths_1.length; _i++) {
        var s = paths_1[_i];
        if (s.path === part) {
            paths.splice(idx);
            return s;
        }
        idx++;
    }
    return new url_tree_1.UrlPathWithParams(part, {});
}
function split(segment, consumedPaths, slicedPath, config) {
    if (slicedPath.length > 0 &&
        containsEmptyPathRedirectsWithNamedOutlets(segment, slicedPath, config)) {
        var s = new url_tree_1.UrlSegment(consumedPaths, createChildrenForEmptyPaths(config, new url_tree_1.UrlSegment(slicedPath, segment.children)));
        return { segment: mergeTrivialChildren(s), slicedPath: [] };
    }
    else if (slicedPath.length === 0 && containsEmptyPathRedirects(segment, slicedPath, config)) {
        var s = new url_tree_1.UrlSegment(segment.pathsWithParams, addEmptyPathsToChildrenIfNeeded(segment, slicedPath, config, segment.children));
        return { segment: mergeTrivialChildren(s), slicedPath: slicedPath };
    }
    else {
        return { segment: segment, slicedPath: slicedPath };
    }
}
function mergeTrivialChildren(s) {
    if (s.numberOfChildren === 1 && s.children[shared_1.PRIMARY_OUTLET]) {
        var c = s.children[shared_1.PRIMARY_OUTLET];
        return new url_tree_1.UrlSegment(s.pathsWithParams.concat(c.pathsWithParams), c.children);
    }
    else {
        return s;
    }
}
function addEmptyPathsToChildrenIfNeeded(segment, slicedPath, routes, children) {
    var res = {};
    for (var _i = 0, routes_2 = routes; _i < routes_2.length; _i++) {
        var r = routes_2[_i];
        if (emptyPathRedirect(segment, slicedPath, r) && !children[getOutlet(r)]) {
            res[getOutlet(r)] = new url_tree_1.UrlSegment([], {});
        }
    }
    return collection_1.merge(children, res);
}
function createChildrenForEmptyPaths(routes, primarySegment) {
    var res = {};
    res[shared_1.PRIMARY_OUTLET] = primarySegment;
    for (var _i = 0, routes_3 = routes; _i < routes_3.length; _i++) {
        var r = routes_3[_i];
        if (r.path === '') {
            res[getOutlet(r)] = new url_tree_1.UrlSegment([], {});
        }
    }
    return res;
}
function containsEmptyPathRedirectsWithNamedOutlets(segment, slicedPath, routes) {
    return routes
        .filter(function (r) { return emptyPathRedirect(segment, slicedPath, r) && getOutlet(r) !== shared_1.PRIMARY_OUTLET; })
        .length > 0;
}
function containsEmptyPathRedirects(segment, slicedPath, routes) {
    return routes.filter(function (r) { return emptyPathRedirect(segment, slicedPath, r); }).length > 0;
}
function emptyPathRedirect(segment, slicedPath, r) {
    if ((segment.hasChildren() || slicedPath.length > 0) && (r.terminal || r.pathMatch === 'full'))
        return false;
    return r.path === '' && r.redirectTo !== undefined;
}
function getOutlet(route) {
    return route.outlet ? route.outlet : shared_1.PRIMARY_OUTLET;
}
//# sourceMappingURL=apply_redirects.js.map