/**
 * @license
 * Copyright Google Inc. All Rights Reserved.
 *
 * Use of this source code is governed by an MIT-style license that can be
 * found in the LICENSE file at https://angular.io/license
 */
"use strict";
function shallowEqualArrays(a, b) {
    if (a.length !== b.length)
        return false;
    for (var i = 0; i < a.length; ++i) {
        if (!shallowEqual(a[i], b[i]))
            return false;
    }
    return true;
}
exports.shallowEqualArrays = shallowEqualArrays;
function shallowEqual(a, b) {
    var k1 = Object.keys(a);
    var k2 = Object.keys(b);
    if (k1.length != k2.length) {
        return false;
    }
    var key;
    for (var i = 0; i < k1.length; i++) {
        key = k1[i];
        if (a[key] !== b[key]) {
            return false;
        }
    }
    return true;
}
exports.shallowEqual = shallowEqual;
function flatten(a) {
    var target = [];
    for (var i = 0; i < a.length; ++i) {
        for (var j = 0; j < a[i].length; ++j) {
            target.push(a[i][j]);
        }
    }
    return target;
}
exports.flatten = flatten;
function first(a) {
    return a.length > 0 ? a[0] : null;
}
exports.first = first;
function last(a) {
    return a.length > 0 ? a[a.length - 1] : null;
}
exports.last = last;
function and(bools) {
    return bools.reduce(function (a, b) { return a && b; }, true);
}
exports.and = and;
function merge(m1, m2) {
    var m = {};
    for (var attr in m1) {
        if (m1.hasOwnProperty(attr)) {
            m[attr] = m1[attr];
        }
    }
    for (var attr in m2) {
        if (m2.hasOwnProperty(attr)) {
            m[attr] = m2[attr];
        }
    }
    return m;
}
exports.merge = merge;
function forEach(map, callback) {
    for (var prop in map) {
        if (map.hasOwnProperty(prop)) {
            callback(map[prop], prop);
        }
    }
}
exports.forEach = forEach;
//# sourceMappingURL=collection.js.map