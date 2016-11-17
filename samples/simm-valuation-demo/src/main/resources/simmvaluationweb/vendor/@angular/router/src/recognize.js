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
var router_state_1 = require('./router_state');
var shared_1 = require('./shared');
var url_tree_1 = require('./url_tree');
var collection_1 = require('./utils/collection');
var tree_1 = require('./utils/tree');
var NoMatch = (function () {
    function NoMatch(segment) {
        if (segment === void 0) { segment = null; }
        this.segment = segment;
    }
    return NoMatch;
}());
var InheritedFromParent = (function () {
    function InheritedFromParent(parent, params, data, resolve) {
        this.parent = parent;
        this.params = params;
        this.data = data;
        this.resolve = resolve;
    }
    Object.defineProperty(InheritedFromParent.prototype, "allParams", {
        get: function () {
            return this.parent ? collection_1.merge(this.parent.allParams, this.params) : this.params;
        },
        enumerable: true,
        configurable: true
    });
    Object.defineProperty(InheritedFromParent.prototype, "allData", {
        get: function () { return this.parent ? collection_1.merge(this.parent.allData, this.data) : this.data; },
        enumerable: true,
        configurable: true
    });
    Object.defineProperty(InheritedFromParent, "empty", {
        get: function () {
            return new InheritedFromParent(null, {}, {}, new router_state_1.InheritedResolve(null, {}));
        },
        enumerable: true,
        configurable: true
    });
    return InheritedFromParent;
}());
function recognize(rootComponentType, config, urlTree, url) {
    try {
        var children = processSegment(config, urlTree.root, InheritedFromParent.empty, shared_1.PRIMARY_OUTLET);
        var root = new router_state_1.ActivatedRouteSnapshot([], {}, {}, shared_1.PRIMARY_OUTLET, rootComponentType, null, urlTree.root, -1, router_state_1.InheritedResolve.empty);
        var rootNode = new tree_1.TreeNode(root, children);
        return of_1.of(new router_state_1.RouterStateSnapshot(url, rootNode, urlTree.queryParams, urlTree.fragment));
    }
    catch (e) {
        if (e instanceof NoMatch) {
            return new Observable_1.Observable(function (obs) {
                return obs.error(new Error("Cannot match any routes: '" + e.segment + "'"));
            });
        }
        else {
            return new Observable_1.Observable(function (obs) { return obs.error(e); });
        }
    }
}
exports.recognize = recognize;
function processSegment(config, segment, inherited, outlet) {
    if (segment.pathsWithParams.length === 0 && segment.hasChildren()) {
        return processSegmentChildren(config, segment, inherited);
    }
    else {
        return processPathsWithParams(config, segment, 0, segment.pathsWithParams, inherited, outlet);
    }
}
function processSegmentChildren(config, segment, inherited) {
    var children = url_tree_1.mapChildrenIntoArray(segment, function (child, childOutlet) { return processSegment(config, child, inherited, childOutlet); });
    checkOutletNameUniqueness(children);
    sortActivatedRouteSnapshots(children);
    return children;
}
function sortActivatedRouteSnapshots(nodes) {
    nodes.sort(function (a, b) {
        if (a.value.outlet === shared_1.PRIMARY_OUTLET)
            return -1;
        if (b.value.outlet === shared_1.PRIMARY_OUTLET)
            return 1;
        return a.value.outlet.localeCompare(b.value.outlet);
    });
}
function processPathsWithParams(config, segment, pathIndex, paths, inherited, outlet) {
    for (var _i = 0, config_1 = config; _i < config_1.length; _i++) {
        var r = config_1[_i];
        try {
            return processPathsWithParamsAgainstRoute(r, segment, pathIndex, paths, inherited, outlet);
        }
        catch (e) {
            if (!(e instanceof NoMatch))
                throw e;
        }
    }
    throw new NoMatch(segment);
}
function processPathsWithParamsAgainstRoute(route, rawSegment, pathIndex, paths, inherited, outlet) {
    if (route.redirectTo)
        throw new NoMatch();
    if ((route.outlet ? route.outlet : shared_1.PRIMARY_OUTLET) !== outlet)
        throw new NoMatch();
    var newInheritedResolve = new router_state_1.InheritedResolve(inherited.resolve, getResolve(route));
    if (route.path === '**') {
        var params = paths.length > 0 ? collection_1.last(paths).parameters : {};
        var snapshot_1 = new router_state_1.ActivatedRouteSnapshot(paths, collection_1.merge(inherited.allParams, params), collection_1.merge(inherited.allData, getData(route)), outlet, route.component, route, getSourceSegment(rawSegment), getPathIndexShift(rawSegment) - 1, newInheritedResolve);
        return [new tree_1.TreeNode(snapshot_1, [])];
    }
    var _a = match(rawSegment, route, paths), consumedPaths = _a.consumedPaths, parameters = _a.parameters, lastChild = _a.lastChild;
    var rawSlicedPath = paths.slice(lastChild);
    var childConfig = route.children ? route.children : [];
    var newInherited = route.component ?
        InheritedFromParent.empty :
        new InheritedFromParent(inherited, parameters, getData(route), newInheritedResolve);
    var _b = split(rawSegment, consumedPaths, rawSlicedPath, childConfig), segment = _b.segment, slicedPath = _b.slicedPath;
    var snapshot = new router_state_1.ActivatedRouteSnapshot(consumedPaths, collection_1.merge(inherited.allParams, parameters), collection_1.merge(inherited.allData, getData(route)), outlet, route.component, route, getSourceSegment(rawSegment), getPathIndexShift(rawSegment) + pathIndex + lastChild - 1, newInheritedResolve);
    if (slicedPath.length === 0 && segment.hasChildren()) {
        var children = processSegmentChildren(childConfig, segment, newInherited);
        return [new tree_1.TreeNode(snapshot, children)];
    }
    else if (childConfig.length === 0 && slicedPath.length === 0) {
        return [new tree_1.TreeNode(snapshot, [])];
    }
    else {
        var children = processPathsWithParams(childConfig, segment, pathIndex + lastChild, slicedPath, newInherited, shared_1.PRIMARY_OUTLET);
        return [new tree_1.TreeNode(snapshot, children)];
    }
}
function match(segment, route, paths) {
    if (route.path === '') {
        if ((route.terminal || route.pathMatch === 'full') &&
            (segment.hasChildren() || paths.length > 0)) {
            throw new NoMatch();
        }
        else {
            return { consumedPaths: [], lastChild: 0, parameters: {} };
        }
    }
    var path = route.path;
    var parts = path.split('/');
    var posParameters = {};
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
            posParameters[p.substring(1)] = current.path;
        }
        consumedPaths.push(current);
        currentIndex++;
    }
    if ((route.terminal || route.pathMatch === 'full') &&
        (segment.hasChildren() || currentIndex < paths.length)) {
        throw new NoMatch();
    }
    var parameters = collection_1.merge(posParameters, consumedPaths[consumedPaths.length - 1].parameters);
    return { consumedPaths: consumedPaths, lastChild: currentIndex, parameters: parameters };
}
function checkOutletNameUniqueness(nodes) {
    var names = {};
    nodes.forEach(function (n) {
        var routeWithSameOutletName = names[n.value.outlet];
        if (routeWithSameOutletName) {
            var p = routeWithSameOutletName.url.map(function (s) { return s.toString(); }).join('/');
            var c = n.value.url.map(function (s) { return s.toString(); }).join('/');
            throw new Error("Two segments cannot have the same outlet name: '" + p + "' and '" + c + "'.");
        }
        names[n.value.outlet] = n.value;
    });
}
function getSourceSegment(segment) {
    var s = segment;
    while (s._sourceSegment) {
        s = s._sourceSegment;
    }
    return s;
}
function getPathIndexShift(segment) {
    var s = segment;
    var res = 0;
    while (s._sourceSegment) {
        s = s._sourceSegment;
        res += segment._pathIndexShift;
    }
    return res;
}
function split(segment, consumedPaths, slicedPath, config) {
    if (slicedPath.length > 0 &&
        containsEmptyPathMatchesWithNamedOutlets(segment, slicedPath, config)) {
        var s = new url_tree_1.UrlSegment(consumedPaths, createChildrenForEmptyPaths(segment, consumedPaths, config, new url_tree_1.UrlSegment(slicedPath, segment.children)));
        s._sourceSegment = segment;
        s._pathIndexShift = 0;
        return { segment: s, slicedPath: [] };
    }
    else if (slicedPath.length === 0 && containsEmptyPathMatches(segment, slicedPath, config)) {
        var s = new url_tree_1.UrlSegment(segment.pathsWithParams, addEmptyPathsToChildrenIfNeeded(segment, slicedPath, config, segment.children));
        s._sourceSegment = segment;
        s._pathIndexShift = 0;
        return { segment: s, slicedPath: slicedPath };
    }
    else {
        return { segment: segment, slicedPath: slicedPath };
    }
}
function addEmptyPathsToChildrenIfNeeded(segment, slicedPath, routes, children) {
    var res = {};
    for (var _i = 0, routes_1 = routes; _i < routes_1.length; _i++) {
        var r = routes_1[_i];
        if (emptyPathMatch(segment, slicedPath, r) && !children[getOutlet(r)]) {
            var s = new url_tree_1.UrlSegment([], {});
            s._sourceSegment = segment;
            s._pathIndexShift = segment.pathsWithParams.length;
            res[getOutlet(r)] = s;
        }
    }
    return collection_1.merge(children, res);
}
function createChildrenForEmptyPaths(segment, consumedPaths, routes, primarySegment) {
    var res = {};
    res[shared_1.PRIMARY_OUTLET] = primarySegment;
    primarySegment._sourceSegment = segment;
    primarySegment._pathIndexShift = consumedPaths.length;
    for (var _i = 0, routes_2 = routes; _i < routes_2.length; _i++) {
        var r = routes_2[_i];
        if (r.path === '') {
            var s = new url_tree_1.UrlSegment([], {});
            s._sourceSegment = segment;
            s._pathIndexShift = consumedPaths.length;
            res[getOutlet(r)] = s;
        }
    }
    return res;
}
function containsEmptyPathMatchesWithNamedOutlets(segment, slicedPath, routes) {
    return routes
        .filter(function (r) { return emptyPathMatch(segment, slicedPath, r) && getOutlet(r) !== shared_1.PRIMARY_OUTLET; })
        .length > 0;
}
function containsEmptyPathMatches(segment, slicedPath, routes) {
    return routes.filter(function (r) { return emptyPathMatch(segment, slicedPath, r); }).length > 0;
}
function emptyPathMatch(segment, slicedPath, r) {
    if ((segment.hasChildren() || slicedPath.length > 0) && (r.terminal || r.pathMatch === 'full'))
        return false;
    return r.path === '' && r.redirectTo === undefined;
}
function getOutlet(route) {
    return route.outlet ? route.outlet : shared_1.PRIMARY_OUTLET;
}
function getData(route) {
    return route.data ? route.data : {};
}
function getResolve(route) {
    return route.resolve ? route.resolve : {};
}
//# sourceMappingURL=recognize.js.map