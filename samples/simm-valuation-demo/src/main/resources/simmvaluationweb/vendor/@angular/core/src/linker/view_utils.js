/**
 * @license
 * Copyright Google Inc. All Rights Reserved.
 *
 * Use of this source code is governed by an MIT-style license that can be
 * found in the LICENSE file at https://angular.io/license
 */
"use strict";
var application_tokens_1 = require('../application_tokens');
var change_detection_1 = require('../change_detection/change_detection');
var change_detection_util_1 = require('../change_detection/change_detection_util');
var decorators_1 = require('../di/decorators');
var collection_1 = require('../facade/collection');
var exceptions_1 = require('../facade/exceptions');
var lang_1 = require('../facade/lang');
var api_1 = require('../render/api');
var security_1 = require('../security');
var element_1 = require('./element');
var exceptions_2 = require('./exceptions');
var ViewUtils = (function () {
    function ViewUtils(_renderer, _appId, sanitizer) {
        this._renderer = _renderer;
        this._appId = _appId;
        this._nextCompTypeId = 0;
        this.sanitizer = sanitizer;
    }
    /**
     * Used by the generated code
     */
    ViewUtils.prototype.createRenderComponentType = function (templateUrl, slotCount, encapsulation, styles) {
        return new api_1.RenderComponentType(this._appId + "-" + this._nextCompTypeId++, templateUrl, slotCount, encapsulation, styles);
    };
    /** @internal */
    ViewUtils.prototype.renderComponent = function (renderComponentType) {
        return this._renderer.renderComponent(renderComponentType);
    };
    /** @nocollapse */
    ViewUtils.decorators = [
        { type: decorators_1.Injectable },
    ];
    /** @nocollapse */
    ViewUtils.ctorParameters = [
        { type: api_1.RootRenderer, },
        { type: undefined, decorators: [{ type: decorators_1.Inject, args: [application_tokens_1.APP_ID,] },] },
        { type: security_1.SanitizationService, },
    ];
    return ViewUtils;
}());
exports.ViewUtils = ViewUtils;
function flattenNestedViewRenderNodes(nodes) {
    return _flattenNestedViewRenderNodes(nodes, []);
}
exports.flattenNestedViewRenderNodes = flattenNestedViewRenderNodes;
function _flattenNestedViewRenderNodes(nodes, renderNodes) {
    for (var i = 0; i < nodes.length; i++) {
        var node = nodes[i];
        if (node instanceof element_1.AppElement) {
            var appEl = node;
            renderNodes.push(appEl.nativeElement);
            if (lang_1.isPresent(appEl.nestedViews)) {
                for (var k = 0; k < appEl.nestedViews.length; k++) {
                    _flattenNestedViewRenderNodes(appEl.nestedViews[k].rootNodesOrAppElements, renderNodes);
                }
            }
        }
        else {
            renderNodes.push(node);
        }
    }
    return renderNodes;
}
var EMPTY_ARR = [];
function ensureSlotCount(projectableNodes, expectedSlotCount) {
    var res;
    if (lang_1.isBlank(projectableNodes)) {
        res = EMPTY_ARR;
    }
    else if (projectableNodes.length < expectedSlotCount) {
        var givenSlotCount = projectableNodes.length;
        res = collection_1.ListWrapper.createFixedSize(expectedSlotCount);
        for (var i = 0; i < expectedSlotCount; i++) {
            res[i] = (i < givenSlotCount) ? projectableNodes[i] : EMPTY_ARR;
        }
    }
    else {
        res = projectableNodes;
    }
    return res;
}
exports.ensureSlotCount = ensureSlotCount;
exports.MAX_INTERPOLATION_VALUES = 9;
function interpolate(valueCount, c0, a1, c1, a2, c2, a3, c3, a4, c4, a5, c5, a6, c6, a7, c7, a8, c8, a9, c9) {
    switch (valueCount) {
        case 1:
            return c0 + _toStringWithNull(a1) + c1;
        case 2:
            return c0 + _toStringWithNull(a1) + c1 + _toStringWithNull(a2) + c2;
        case 3:
            return c0 + _toStringWithNull(a1) + c1 + _toStringWithNull(a2) + c2 + _toStringWithNull(a3) +
                c3;
        case 4:
            return c0 + _toStringWithNull(a1) + c1 + _toStringWithNull(a2) + c2 + _toStringWithNull(a3) +
                c3 + _toStringWithNull(a4) + c4;
        case 5:
            return c0 + _toStringWithNull(a1) + c1 + _toStringWithNull(a2) + c2 + _toStringWithNull(a3) +
                c3 + _toStringWithNull(a4) + c4 + _toStringWithNull(a5) + c5;
        case 6:
            return c0 + _toStringWithNull(a1) + c1 + _toStringWithNull(a2) + c2 + _toStringWithNull(a3) +
                c3 + _toStringWithNull(a4) + c4 + _toStringWithNull(a5) + c5 + _toStringWithNull(a6) + c6;
        case 7:
            return c0 + _toStringWithNull(a1) + c1 + _toStringWithNull(a2) + c2 + _toStringWithNull(a3) +
                c3 + _toStringWithNull(a4) + c4 + _toStringWithNull(a5) + c5 + _toStringWithNull(a6) +
                c6 + _toStringWithNull(a7) + c7;
        case 8:
            return c0 + _toStringWithNull(a1) + c1 + _toStringWithNull(a2) + c2 + _toStringWithNull(a3) +
                c3 + _toStringWithNull(a4) + c4 + _toStringWithNull(a5) + c5 + _toStringWithNull(a6) +
                c6 + _toStringWithNull(a7) + c7 + _toStringWithNull(a8) + c8;
        case 9:
            return c0 + _toStringWithNull(a1) + c1 + _toStringWithNull(a2) + c2 + _toStringWithNull(a3) +
                c3 + _toStringWithNull(a4) + c4 + _toStringWithNull(a5) + c5 + _toStringWithNull(a6) +
                c6 + _toStringWithNull(a7) + c7 + _toStringWithNull(a8) + c8 + _toStringWithNull(a9) + c9;
        default:
            throw new exceptions_1.BaseException("Does not support more than 9 expressions");
    }
}
exports.interpolate = interpolate;
function _toStringWithNull(v) {
    return v != null ? v.toString() : '';
}
function checkBinding(throwOnChange, oldValue, newValue) {
    if (throwOnChange) {
        if (!change_detection_1.devModeEqual(oldValue, newValue)) {
            throw new exceptions_2.ExpressionChangedAfterItHasBeenCheckedException(oldValue, newValue, null);
        }
        return false;
    }
    else {
        return !lang_1.looseIdentical(oldValue, newValue);
    }
}
exports.checkBinding = checkBinding;
function arrayLooseIdentical(a, b) {
    if (a.length != b.length)
        return false;
    for (var i = 0; i < a.length; ++i) {
        if (!lang_1.looseIdentical(a[i], b[i]))
            return false;
    }
    return true;
}
exports.arrayLooseIdentical = arrayLooseIdentical;
function mapLooseIdentical(m1, m2) {
    var k1 = collection_1.StringMapWrapper.keys(m1);
    var k2 = collection_1.StringMapWrapper.keys(m2);
    if (k1.length != k2.length) {
        return false;
    }
    var key;
    for (var i = 0; i < k1.length; i++) {
        key = k1[i];
        if (!lang_1.looseIdentical(m1[key], m2[key])) {
            return false;
        }
    }
    return true;
}
exports.mapLooseIdentical = mapLooseIdentical;
function castByValue(input, value) {
    return input;
}
exports.castByValue = castByValue;
exports.EMPTY_ARRAY = [];
exports.EMPTY_MAP = {};
function pureProxy1(fn) {
    var result;
    var v0;
    v0 = change_detection_util_1.uninitialized;
    return function (p0) {
        if (!lang_1.looseIdentical(v0, p0)) {
            v0 = p0;
            result = fn(p0);
        }
        return result;
    };
}
exports.pureProxy1 = pureProxy1;
function pureProxy2(fn) {
    var result;
    var v0 /** TODO #9100 */, v1;
    v0 = v1 = change_detection_util_1.uninitialized;
    return function (p0, p1) {
        if (!lang_1.looseIdentical(v0, p0) || !lang_1.looseIdentical(v1, p1)) {
            v0 = p0;
            v1 = p1;
            result = fn(p0, p1);
        }
        return result;
    };
}
exports.pureProxy2 = pureProxy2;
function pureProxy3(fn) {
    var result;
    var v0 /** TODO #9100 */, v1 /** TODO #9100 */, v2;
    v0 = v1 = v2 = change_detection_util_1.uninitialized;
    return function (p0, p1, p2) {
        if (!lang_1.looseIdentical(v0, p0) || !lang_1.looseIdentical(v1, p1) || !lang_1.looseIdentical(v2, p2)) {
            v0 = p0;
            v1 = p1;
            v2 = p2;
            result = fn(p0, p1, p2);
        }
        return result;
    };
}
exports.pureProxy3 = pureProxy3;
function pureProxy4(fn) {
    var result;
    var v0 /** TODO #9100 */, v1 /** TODO #9100 */, v2 /** TODO #9100 */, v3;
    v0 = v1 = v2 = v3 = change_detection_util_1.uninitialized;
    return function (p0, p1, p2, p3) {
        if (!lang_1.looseIdentical(v0, p0) || !lang_1.looseIdentical(v1, p1) || !lang_1.looseIdentical(v2, p2) ||
            !lang_1.looseIdentical(v3, p3)) {
            v0 = p0;
            v1 = p1;
            v2 = p2;
            v3 = p3;
            result = fn(p0, p1, p2, p3);
        }
        return result;
    };
}
exports.pureProxy4 = pureProxy4;
function pureProxy5(fn) {
    var result;
    var v0 /** TODO #9100 */, v1 /** TODO #9100 */, v2 /** TODO #9100 */, v3 /** TODO #9100 */, v4;
    v0 = v1 = v2 = v3 = v4 = change_detection_util_1.uninitialized;
    return function (p0, p1, p2, p3, p4) {
        if (!lang_1.looseIdentical(v0, p0) || !lang_1.looseIdentical(v1, p1) || !lang_1.looseIdentical(v2, p2) ||
            !lang_1.looseIdentical(v3, p3) || !lang_1.looseIdentical(v4, p4)) {
            v0 = p0;
            v1 = p1;
            v2 = p2;
            v3 = p3;
            v4 = p4;
            result = fn(p0, p1, p2, p3, p4);
        }
        return result;
    };
}
exports.pureProxy5 = pureProxy5;
function pureProxy6(fn) {
    var result;
    var v0 /** TODO #9100 */, v1 /** TODO #9100 */, v2 /** TODO #9100 */, v3 /** TODO #9100 */, v4 /** TODO #9100 */, v5;
    v0 = v1 = v2 = v3 = v4 = v5 = change_detection_util_1.uninitialized;
    return function (p0, p1, p2, p3, p4, p5) {
        if (!lang_1.looseIdentical(v0, p0) || !lang_1.looseIdentical(v1, p1) || !lang_1.looseIdentical(v2, p2) ||
            !lang_1.looseIdentical(v3, p3) || !lang_1.looseIdentical(v4, p4) || !lang_1.looseIdentical(v5, p5)) {
            v0 = p0;
            v1 = p1;
            v2 = p2;
            v3 = p3;
            v4 = p4;
            v5 = p5;
            result = fn(p0, p1, p2, p3, p4, p5);
        }
        return result;
    };
}
exports.pureProxy6 = pureProxy6;
function pureProxy7(fn) {
    var result;
    var v0 /** TODO #9100 */, v1 /** TODO #9100 */, v2 /** TODO #9100 */, v3 /** TODO #9100 */, v4 /** TODO #9100 */, v5 /** TODO #9100 */, v6;
    v0 = v1 = v2 = v3 = v4 = v5 = v6 = change_detection_util_1.uninitialized;
    return function (p0, p1, p2, p3, p4, p5, p6) {
        if (!lang_1.looseIdentical(v0, p0) || !lang_1.looseIdentical(v1, p1) || !lang_1.looseIdentical(v2, p2) ||
            !lang_1.looseIdentical(v3, p3) || !lang_1.looseIdentical(v4, p4) || !lang_1.looseIdentical(v5, p5) ||
            !lang_1.looseIdentical(v6, p6)) {
            v0 = p0;
            v1 = p1;
            v2 = p2;
            v3 = p3;
            v4 = p4;
            v5 = p5;
            v6 = p6;
            result = fn(p0, p1, p2, p3, p4, p5, p6);
        }
        return result;
    };
}
exports.pureProxy7 = pureProxy7;
function pureProxy8(fn) {
    var result;
    var v0 /** TODO #9100 */, v1 /** TODO #9100 */, v2 /** TODO #9100 */, v3 /** TODO #9100 */, v4 /** TODO #9100 */, v5 /** TODO #9100 */, v6 /** TODO #9100 */, v7;
    v0 = v1 = v2 = v3 = v4 = v5 = v6 = v7 = change_detection_util_1.uninitialized;
    return function (p0, p1, p2, p3, p4, p5, p6, p7) {
        if (!lang_1.looseIdentical(v0, p0) || !lang_1.looseIdentical(v1, p1) || !lang_1.looseIdentical(v2, p2) ||
            !lang_1.looseIdentical(v3, p3) || !lang_1.looseIdentical(v4, p4) || !lang_1.looseIdentical(v5, p5) ||
            !lang_1.looseIdentical(v6, p6) || !lang_1.looseIdentical(v7, p7)) {
            v0 = p0;
            v1 = p1;
            v2 = p2;
            v3 = p3;
            v4 = p4;
            v5 = p5;
            v6 = p6;
            v7 = p7;
            result = fn(p0, p1, p2, p3, p4, p5, p6, p7);
        }
        return result;
    };
}
exports.pureProxy8 = pureProxy8;
function pureProxy9(fn) {
    var result;
    var v0 /** TODO #9100 */, v1 /** TODO #9100 */, v2 /** TODO #9100 */, v3 /** TODO #9100 */, v4 /** TODO #9100 */, v5 /** TODO #9100 */, v6 /** TODO #9100 */, v7 /** TODO #9100 */, v8;
    v0 = v1 = v2 = v3 = v4 = v5 = v6 = v7 = v8 = change_detection_util_1.uninitialized;
    return function (p0, p1, p2, p3, p4, p5, p6, p7, p8) {
        if (!lang_1.looseIdentical(v0, p0) || !lang_1.looseIdentical(v1, p1) || !lang_1.looseIdentical(v2, p2) ||
            !lang_1.looseIdentical(v3, p3) || !lang_1.looseIdentical(v4, p4) || !lang_1.looseIdentical(v5, p5) ||
            !lang_1.looseIdentical(v6, p6) || !lang_1.looseIdentical(v7, p7) || !lang_1.looseIdentical(v8, p8)) {
            v0 = p0;
            v1 = p1;
            v2 = p2;
            v3 = p3;
            v4 = p4;
            v5 = p5;
            v6 = p6;
            v7 = p7;
            v8 = p8;
            result = fn(p0, p1, p2, p3, p4, p5, p6, p7, p8);
        }
        return result;
    };
}
exports.pureProxy9 = pureProxy9;
function pureProxy10(fn) {
    var result;
    var v0 /** TODO #9100 */, v1 /** TODO #9100 */, v2 /** TODO #9100 */, v3 /** TODO #9100 */, v4 /** TODO #9100 */, v5 /** TODO #9100 */, v6 /** TODO #9100 */, v7 /** TODO #9100 */, v8 /** TODO #9100 */, v9;
    v0 = v1 = v2 = v3 = v4 = v5 = v6 = v7 = v8 = v9 = change_detection_util_1.uninitialized;
    return function (p0, p1, p2, p3, p4, p5, p6, p7, p8, p9) {
        if (!lang_1.looseIdentical(v0, p0) || !lang_1.looseIdentical(v1, p1) || !lang_1.looseIdentical(v2, p2) ||
            !lang_1.looseIdentical(v3, p3) || !lang_1.looseIdentical(v4, p4) || !lang_1.looseIdentical(v5, p5) ||
            !lang_1.looseIdentical(v6, p6) || !lang_1.looseIdentical(v7, p7) || !lang_1.looseIdentical(v8, p8) ||
            !lang_1.looseIdentical(v9, p9)) {
            v0 = p0;
            v1 = p1;
            v2 = p2;
            v3 = p3;
            v4 = p4;
            v5 = p5;
            v6 = p6;
            v7 = p7;
            v8 = p8;
            v9 = p9;
            result = fn(p0, p1, p2, p3, p4, p5, p6, p7, p8, p9);
        }
        return result;
    };
}
exports.pureProxy10 = pureProxy10;
//# sourceMappingURL=view_utils.js.map