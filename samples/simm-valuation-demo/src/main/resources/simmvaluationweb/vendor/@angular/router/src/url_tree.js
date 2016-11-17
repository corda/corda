/**
 * @license
 * Copyright Google Inc. All Rights Reserved.
 *
 * Use of this source code is governed by an MIT-style license that can be
 * found in the LICENSE file at https://angular.io/license
 */
"use strict";
var shared_1 = require('./shared');
var collection_1 = require('./utils/collection');
function createEmptyUrlTree() {
    return new UrlTree(new UrlSegment([], {}), {}, null);
}
exports.createEmptyUrlTree = createEmptyUrlTree;
function containsTree(container, containee, exact) {
    if (exact) {
        return equalSegments(container.root, containee.root);
    }
    else {
        return containsSegment(container.root, containee.root);
    }
}
exports.containsTree = containsTree;
function equalSegments(container, containee) {
    if (!equalPath(container.pathsWithParams, containee.pathsWithParams))
        return false;
    if (container.numberOfChildren !== containee.numberOfChildren)
        return false;
    for (var c in containee.children) {
        if (!container.children[c])
            return false;
        if (!equalSegments(container.children[c], containee.children[c]))
            return false;
    }
    return true;
}
function containsSegment(container, containee) {
    return containsSegmentHelper(container, containee, containee.pathsWithParams);
}
function containsSegmentHelper(container, containee, containeePaths) {
    if (container.pathsWithParams.length > containeePaths.length) {
        var current = container.pathsWithParams.slice(0, containeePaths.length);
        if (!equalPath(current, containeePaths))
            return false;
        if (containee.hasChildren())
            return false;
        return true;
    }
    else if (container.pathsWithParams.length === containeePaths.length) {
        if (!equalPath(container.pathsWithParams, containeePaths))
            return false;
        for (var c in containee.children) {
            if (!container.children[c])
                return false;
            if (!containsSegment(container.children[c], containee.children[c]))
                return false;
        }
        return true;
    }
    else {
        var current = containeePaths.slice(0, container.pathsWithParams.length);
        var next = containeePaths.slice(container.pathsWithParams.length);
        if (!equalPath(container.pathsWithParams, current))
            return false;
        if (!container.children[shared_1.PRIMARY_OUTLET])
            return false;
        return containsSegmentHelper(container.children[shared_1.PRIMARY_OUTLET], containee, next);
    }
}
/**
 * A URL in the tree form.
 *
 * @stable
 */
var UrlTree = (function () {
    /**
     * @internal
     */
    function UrlTree(root, queryParams, fragment) {
        this.root = root;
        this.queryParams = queryParams;
        this.fragment = fragment;
    }
    UrlTree.prototype.toString = function () { return new DefaultUrlSerializer().serialize(this); };
    return UrlTree;
}());
exports.UrlTree = UrlTree;
/**
 * @stable
 */
var UrlSegment = (function () {
    function UrlSegment(pathsWithParams, children) {
        var _this = this;
        this.pathsWithParams = pathsWithParams;
        this.children = children;
        this.parent = null;
        collection_1.forEach(children, function (v, k) { return v.parent = _this; });
    }
    /**
     * Return true if the segment has child segments
     */
    UrlSegment.prototype.hasChildren = function () { return this.numberOfChildren > 0; };
    Object.defineProperty(UrlSegment.prototype, "numberOfChildren", {
        /**
         * Returns the number of child sements.
         */
        get: function () { return Object.keys(this.children).length; },
        enumerable: true,
        configurable: true
    });
    UrlSegment.prototype.toString = function () { return serializePaths(this); };
    return UrlSegment;
}());
exports.UrlSegment = UrlSegment;
/**
 * @stable
 */
var UrlPathWithParams = (function () {
    function UrlPathWithParams(path, parameters) {
        this.path = path;
        this.parameters = parameters;
    }
    UrlPathWithParams.prototype.toString = function () { return serializePath(this); };
    return UrlPathWithParams;
}());
exports.UrlPathWithParams = UrlPathWithParams;
function equalPathsWithParams(a, b) {
    if (a.length !== b.length)
        return false;
    for (var i = 0; i < a.length; ++i) {
        if (a[i].path !== b[i].path)
            return false;
        if (!collection_1.shallowEqual(a[i].parameters, b[i].parameters))
            return false;
    }
    return true;
}
exports.equalPathsWithParams = equalPathsWithParams;
function equalPath(a, b) {
    if (a.length !== b.length)
        return false;
    for (var i = 0; i < a.length; ++i) {
        if (a[i].path !== b[i].path)
            return false;
    }
    return true;
}
exports.equalPath = equalPath;
function mapChildren(segment, fn) {
    var newChildren = {};
    collection_1.forEach(segment.children, function (child, childOutlet) {
        if (childOutlet === shared_1.PRIMARY_OUTLET) {
            newChildren[childOutlet] = fn(child, childOutlet);
        }
    });
    collection_1.forEach(segment.children, function (child, childOutlet) {
        if (childOutlet !== shared_1.PRIMARY_OUTLET) {
            newChildren[childOutlet] = fn(child, childOutlet);
        }
    });
    return newChildren;
}
exports.mapChildren = mapChildren;
function mapChildrenIntoArray(segment, fn) {
    var res = [];
    collection_1.forEach(segment.children, function (child, childOutlet) {
        if (childOutlet === shared_1.PRIMARY_OUTLET) {
            res = res.concat(fn(child, childOutlet));
        }
    });
    collection_1.forEach(segment.children, function (child, childOutlet) {
        if (childOutlet !== shared_1.PRIMARY_OUTLET) {
            res = res.concat(fn(child, childOutlet));
        }
    });
    return res;
}
exports.mapChildrenIntoArray = mapChildrenIntoArray;
/**
 * Defines a way to serialize/deserialize a url tree.
 *
 * @experimental
 */
var UrlSerializer = (function () {
    function UrlSerializer() {
    }
    return UrlSerializer;
}());
exports.UrlSerializer = UrlSerializer;
/**
 * A default implementation of the serialization.
 *
 * @experimental
 */
var DefaultUrlSerializer = (function () {
    function DefaultUrlSerializer() {
    }
    DefaultUrlSerializer.prototype.parse = function (url) {
        var p = new UrlParser(url);
        return new UrlTree(p.parseRootSegment(), p.parseQueryParams(), p.parseFragment());
    };
    DefaultUrlSerializer.prototype.serialize = function (tree) {
        var segment = "/" + serializeSegment(tree.root, true);
        var query = serializeQueryParams(tree.queryParams);
        var fragment = tree.fragment !== null ? "#" + tree.fragment : '';
        return "" + segment + query + fragment;
    };
    return DefaultUrlSerializer;
}());
exports.DefaultUrlSerializer = DefaultUrlSerializer;
function serializePaths(segment) {
    return segment.pathsWithParams.map(function (p) { return serializePath(p); }).join('/');
}
exports.serializePaths = serializePaths;
function serializeSegment(segment, root) {
    if (segment.children[shared_1.PRIMARY_OUTLET] && root) {
        var primary = serializeSegment(segment.children[shared_1.PRIMARY_OUTLET], false);
        var children_1 = [];
        collection_1.forEach(segment.children, function (v, k) {
            if (k !== shared_1.PRIMARY_OUTLET) {
                children_1.push(k + ":" + serializeSegment(v, false));
            }
        });
        if (children_1.length > 0) {
            return primary + "(" + children_1.join('//') + ")";
        }
        else {
            return "" + primary;
        }
    }
    else if (segment.hasChildren() && !root) {
        var children = mapChildrenIntoArray(segment, function (v, k) {
            if (k === shared_1.PRIMARY_OUTLET) {
                return [serializeSegment(segment.children[shared_1.PRIMARY_OUTLET], false)];
            }
            else {
                return [(k + ":" + serializeSegment(v, false))];
            }
        });
        return serializePaths(segment) + "/(" + children.join('//') + ")";
    }
    else {
        return serializePaths(segment);
    }
}
function serializePath(path) {
    return "" + path.path + serializeParams(path.parameters);
}
exports.serializePath = serializePath;
function serializeParams(params) {
    return pairs(params).map(function (p) { return (";" + p.first + "=" + p.second); }).join('');
}
function serializeQueryParams(params) {
    var strs = pairs(params).map(function (p) { return (p.first + "=" + p.second); });
    return strs.length > 0 ? "?" + strs.join("&") : '';
}
var Pair = (function () {
    function Pair(first, second) {
        this.first = first;
        this.second = second;
    }
    return Pair;
}());
function pairs(obj) {
    var res = [];
    for (var prop in obj) {
        if (obj.hasOwnProperty(prop)) {
            res.push(new Pair(prop, obj[prop]));
        }
    }
    return res;
}
var SEGMENT_RE = /^[^\/\(\)\?;=&#]+/;
function matchPathWithParams(str) {
    SEGMENT_RE.lastIndex = 0;
    var match = SEGMENT_RE.exec(str);
    return match ? match[0] : '';
}
var QUERY_PARAM_RE = /^[^=\?&#]+/;
function matchQueryParams(str) {
    QUERY_PARAM_RE.lastIndex = 0;
    var match = SEGMENT_RE.exec(str);
    return match ? match[0] : '';
}
var QUERY_PARAM_VALUE_RE = /^[^\?&#]+/;
function matchUrlQueryParamValue(str) {
    QUERY_PARAM_VALUE_RE.lastIndex = 0;
    var match = QUERY_PARAM_VALUE_RE.exec(str);
    return match ? match[0] : '';
}
var UrlParser = (function () {
    function UrlParser(remaining) {
        this.remaining = remaining;
    }
    UrlParser.prototype.peekStartsWith = function (str) { return this.remaining.startsWith(str); };
    UrlParser.prototype.capture = function (str) {
        if (!this.remaining.startsWith(str)) {
            throw new Error("Expected \"" + str + "\".");
        }
        this.remaining = this.remaining.substring(str.length);
    };
    UrlParser.prototype.parseRootSegment = function () {
        if (this.remaining.startsWith('/')) {
            this.capture('/');
        }
        if (this.remaining === '' || this.remaining.startsWith('?')) {
            return new UrlSegment([], {});
        }
        else {
            return new UrlSegment([], this.parseSegmentChildren());
        }
    };
    UrlParser.prototype.parseSegmentChildren = function () {
        if (this.remaining.length == 0) {
            return {};
        }
        if (this.peekStartsWith('/')) {
            this.capture('/');
        }
        var paths = [this.parsePathWithParams()];
        while (this.peekStartsWith('/') && !this.peekStartsWith('//') && !this.peekStartsWith('/(')) {
            this.capture('/');
            paths.push(this.parsePathWithParams());
        }
        var children = {};
        if (this.peekStartsWith('/(')) {
            this.capture('/');
            children = this.parseParens(true);
        }
        var res = {};
        if (this.peekStartsWith('(')) {
            res = this.parseParens(false);
        }
        res[shared_1.PRIMARY_OUTLET] = new UrlSegment(paths, children);
        return res;
    };
    UrlParser.prototype.parsePathWithParams = function () {
        var path = matchPathWithParams(this.remaining);
        if (path === '' && this.peekStartsWith(';')) {
            throw new Error("Empty path url segment cannot have parameters: '" + this.remaining + "'.");
        }
        this.capture(path);
        var matrixParams = {};
        if (this.peekStartsWith(';')) {
            matrixParams = this.parseMatrixParams();
        }
        return new UrlPathWithParams(path, matrixParams);
    };
    UrlParser.prototype.parseQueryParams = function () {
        var params = {};
        if (this.peekStartsWith('?')) {
            this.capture('?');
            this.parseQueryParam(params);
            while (this.remaining.length > 0 && this.peekStartsWith('&')) {
                this.capture('&');
                this.parseQueryParam(params);
            }
        }
        return params;
    };
    UrlParser.prototype.parseFragment = function () {
        if (this.peekStartsWith('#')) {
            return this.remaining.substring(1);
        }
        else {
            return null;
        }
    };
    UrlParser.prototype.parseMatrixParams = function () {
        var params = {};
        while (this.remaining.length > 0 && this.peekStartsWith(';')) {
            this.capture(';');
            this.parseParam(params);
        }
        return params;
    };
    UrlParser.prototype.parseParam = function (params) {
        var key = matchPathWithParams(this.remaining);
        if (!key) {
            return;
        }
        this.capture(key);
        var value = 'true';
        if (this.peekStartsWith('=')) {
            this.capture('=');
            var valueMatch = matchPathWithParams(this.remaining);
            if (valueMatch) {
                value = valueMatch;
                this.capture(value);
            }
        }
        params[key] = value;
    };
    UrlParser.prototype.parseQueryParam = function (params) {
        var key = matchQueryParams(this.remaining);
        if (!key) {
            return;
        }
        this.capture(key);
        var value = 'true';
        if (this.peekStartsWith('=')) {
            this.capture('=');
            var valueMatch = matchUrlQueryParamValue(this.remaining);
            if (valueMatch) {
                value = valueMatch;
                this.capture(value);
            }
        }
        params[key] = value;
    };
    UrlParser.prototype.parseParens = function (allowPrimary) {
        var segments = {};
        this.capture('(');
        while (!this.peekStartsWith(')') && this.remaining.length > 0) {
            var path = matchPathWithParams(this.remaining);
            var outletName = void 0;
            if (path.indexOf(':') > -1) {
                outletName = path.substr(0, path.indexOf(':'));
                this.capture(outletName);
                this.capture(':');
            }
            else if (allowPrimary) {
                outletName = shared_1.PRIMARY_OUTLET;
            }
            var children = this.parseSegmentChildren();
            segments[outletName] = Object.keys(children).length === 1 ? children[shared_1.PRIMARY_OUTLET] :
                new UrlSegment([], children);
            if (this.peekStartsWith('//')) {
                this.capture('//');
            }
        }
        this.capture(')');
        return segments;
    };
    return UrlParser;
}());
//# sourceMappingURL=url_tree.js.map