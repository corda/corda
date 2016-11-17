/**
 * @license
 * Copyright Google Inc. All Rights Reserved.
 *
 * Use of this source code is governed by an MIT-style license that can be
 * found in the LICENSE file at https://angular.io/license
 */
import { APP_ID } from '../application_tokens';
import { devModeEqual } from '../change_detection/change_detection';
import { uninitialized } from '../change_detection/change_detection_util';
import { Inject, Injectable } from '../di/decorators';
import { ListWrapper, StringMapWrapper } from '../facade/collection';
import { BaseException } from '../facade/exceptions';
import { isBlank, isPresent, looseIdentical } from '../facade/lang';
import { RenderComponentType, RootRenderer } from '../render/api';
import { SanitizationService } from '../security';
import { AppElement } from './element';
import { ExpressionChangedAfterItHasBeenCheckedException } from './exceptions';
export class ViewUtils {
    constructor(_renderer, _appId, sanitizer) {
        this._renderer = _renderer;
        this._appId = _appId;
        this._nextCompTypeId = 0;
        this.sanitizer = sanitizer;
    }
    /**
     * Used by the generated code
     */
    createRenderComponentType(templateUrl, slotCount, encapsulation, styles) {
        return new RenderComponentType(`${this._appId}-${this._nextCompTypeId++}`, templateUrl, slotCount, encapsulation, styles);
    }
    /** @internal */
    renderComponent(renderComponentType) {
        return this._renderer.renderComponent(renderComponentType);
    }
}
/** @nocollapse */
ViewUtils.decorators = [
    { type: Injectable },
];
/** @nocollapse */
ViewUtils.ctorParameters = [
    { type: RootRenderer, },
    { type: undefined, decorators: [{ type: Inject, args: [APP_ID,] },] },
    { type: SanitizationService, },
];
export function flattenNestedViewRenderNodes(nodes) {
    return _flattenNestedViewRenderNodes(nodes, []);
}
function _flattenNestedViewRenderNodes(nodes, renderNodes) {
    for (var i = 0; i < nodes.length; i++) {
        var node = nodes[i];
        if (node instanceof AppElement) {
            var appEl = node;
            renderNodes.push(appEl.nativeElement);
            if (isPresent(appEl.nestedViews)) {
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
const EMPTY_ARR = [];
export function ensureSlotCount(projectableNodes, expectedSlotCount) {
    var res;
    if (isBlank(projectableNodes)) {
        res = EMPTY_ARR;
    }
    else if (projectableNodes.length < expectedSlotCount) {
        var givenSlotCount = projectableNodes.length;
        res = ListWrapper.createFixedSize(expectedSlotCount);
        for (var i = 0; i < expectedSlotCount; i++) {
            res[i] = (i < givenSlotCount) ? projectableNodes[i] : EMPTY_ARR;
        }
    }
    else {
        res = projectableNodes;
    }
    return res;
}
export const MAX_INTERPOLATION_VALUES = 9;
export function interpolate(valueCount, c0, a1, c1, a2, c2, a3, c3, a4, c4, a5, c5, a6, c6, a7, c7, a8, c8, a9, c9) {
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
            throw new BaseException(`Does not support more than 9 expressions`);
    }
}
function _toStringWithNull(v) {
    return v != null ? v.toString() : '';
}
export function checkBinding(throwOnChange, oldValue, newValue) {
    if (throwOnChange) {
        if (!devModeEqual(oldValue, newValue)) {
            throw new ExpressionChangedAfterItHasBeenCheckedException(oldValue, newValue, null);
        }
        return false;
    }
    else {
        return !looseIdentical(oldValue, newValue);
    }
}
export function arrayLooseIdentical(a, b) {
    if (a.length != b.length)
        return false;
    for (var i = 0; i < a.length; ++i) {
        if (!looseIdentical(a[i], b[i]))
            return false;
    }
    return true;
}
export function mapLooseIdentical(m1, m2) {
    var k1 = StringMapWrapper.keys(m1);
    var k2 = StringMapWrapper.keys(m2);
    if (k1.length != k2.length) {
        return false;
    }
    var key;
    for (var i = 0; i < k1.length; i++) {
        key = k1[i];
        if (!looseIdentical(m1[key], m2[key])) {
            return false;
        }
    }
    return true;
}
export function castByValue(input, value) {
    return input;
}
export const EMPTY_ARRAY = [];
export const EMPTY_MAP = {};
export function pureProxy1(fn) {
    var result;
    var v0;
    v0 = uninitialized;
    return (p0) => {
        if (!looseIdentical(v0, p0)) {
            v0 = p0;
            result = fn(p0);
        }
        return result;
    };
}
export function pureProxy2(fn) {
    var result;
    var v0 /** TODO #9100 */, v1;
    v0 = v1 = uninitialized;
    return (p0, p1) => {
        if (!looseIdentical(v0, p0) || !looseIdentical(v1, p1)) {
            v0 = p0;
            v1 = p1;
            result = fn(p0, p1);
        }
        return result;
    };
}
export function pureProxy3(fn) {
    var result;
    var v0 /** TODO #9100 */, v1 /** TODO #9100 */, v2;
    v0 = v1 = v2 = uninitialized;
    return (p0, p1, p2) => {
        if (!looseIdentical(v0, p0) || !looseIdentical(v1, p1) || !looseIdentical(v2, p2)) {
            v0 = p0;
            v1 = p1;
            v2 = p2;
            result = fn(p0, p1, p2);
        }
        return result;
    };
}
export function pureProxy4(fn) {
    var result;
    var v0 /** TODO #9100 */, v1 /** TODO #9100 */, v2 /** TODO #9100 */, v3;
    v0 = v1 = v2 = v3 = uninitialized;
    return (p0, p1, p2, p3) => {
        if (!looseIdentical(v0, p0) || !looseIdentical(v1, p1) || !looseIdentical(v2, p2) ||
            !looseIdentical(v3, p3)) {
            v0 = p0;
            v1 = p1;
            v2 = p2;
            v3 = p3;
            result = fn(p0, p1, p2, p3);
        }
        return result;
    };
}
export function pureProxy5(fn) {
    var result;
    var v0 /** TODO #9100 */, v1 /** TODO #9100 */, v2 /** TODO #9100 */, v3 /** TODO #9100 */, v4;
    v0 = v1 = v2 = v3 = v4 = uninitialized;
    return (p0, p1, p2, p3, p4) => {
        if (!looseIdentical(v0, p0) || !looseIdentical(v1, p1) || !looseIdentical(v2, p2) ||
            !looseIdentical(v3, p3) || !looseIdentical(v4, p4)) {
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
export function pureProxy6(fn) {
    var result;
    var v0 /** TODO #9100 */, v1 /** TODO #9100 */, v2 /** TODO #9100 */, v3 /** TODO #9100 */, v4 /** TODO #9100 */, v5;
    v0 = v1 = v2 = v3 = v4 = v5 = uninitialized;
    return (p0, p1, p2, p3, p4, p5) => {
        if (!looseIdentical(v0, p0) || !looseIdentical(v1, p1) || !looseIdentical(v2, p2) ||
            !looseIdentical(v3, p3) || !looseIdentical(v4, p4) || !looseIdentical(v5, p5)) {
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
export function pureProxy7(fn) {
    var result;
    var v0 /** TODO #9100 */, v1 /** TODO #9100 */, v2 /** TODO #9100 */, v3 /** TODO #9100 */, v4 /** TODO #9100 */, v5 /** TODO #9100 */, v6;
    v0 = v1 = v2 = v3 = v4 = v5 = v6 = uninitialized;
    return (p0, p1, p2, p3, p4, p5, p6) => {
        if (!looseIdentical(v0, p0) || !looseIdentical(v1, p1) || !looseIdentical(v2, p2) ||
            !looseIdentical(v3, p3) || !looseIdentical(v4, p4) || !looseIdentical(v5, p5) ||
            !looseIdentical(v6, p6)) {
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
export function pureProxy8(fn) {
    var result;
    var v0 /** TODO #9100 */, v1 /** TODO #9100 */, v2 /** TODO #9100 */, v3 /** TODO #9100 */, v4 /** TODO #9100 */, v5 /** TODO #9100 */, v6 /** TODO #9100 */, v7;
    v0 = v1 = v2 = v3 = v4 = v5 = v6 = v7 = uninitialized;
    return (p0, p1, p2, p3, p4, p5, p6, p7) => {
        if (!looseIdentical(v0, p0) || !looseIdentical(v1, p1) || !looseIdentical(v2, p2) ||
            !looseIdentical(v3, p3) || !looseIdentical(v4, p4) || !looseIdentical(v5, p5) ||
            !looseIdentical(v6, p6) || !looseIdentical(v7, p7)) {
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
export function pureProxy9(fn) {
    var result;
    var v0 /** TODO #9100 */, v1 /** TODO #9100 */, v2 /** TODO #9100 */, v3 /** TODO #9100 */, v4 /** TODO #9100 */, v5 /** TODO #9100 */, v6 /** TODO #9100 */, v7 /** TODO #9100 */, v8;
    v0 = v1 = v2 = v3 = v4 = v5 = v6 = v7 = v8 = uninitialized;
    return (p0, p1, p2, p3, p4, p5, p6, p7, p8) => {
        if (!looseIdentical(v0, p0) || !looseIdentical(v1, p1) || !looseIdentical(v2, p2) ||
            !looseIdentical(v3, p3) || !looseIdentical(v4, p4) || !looseIdentical(v5, p5) ||
            !looseIdentical(v6, p6) || !looseIdentical(v7, p7) || !looseIdentical(v8, p8)) {
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
export function pureProxy10(fn) {
    var result;
    var v0 /** TODO #9100 */, v1 /** TODO #9100 */, v2 /** TODO #9100 */, v3 /** TODO #9100 */, v4 /** TODO #9100 */, v5 /** TODO #9100 */, v6 /** TODO #9100 */, v7 /** TODO #9100 */, v8 /** TODO #9100 */, v9;
    v0 = v1 = v2 = v3 = v4 = v5 = v6 = v7 = v8 = v9 = uninitialized;
    return (p0, p1, p2, p3, p4, p5, p6, p7, p8, p9) => {
        if (!looseIdentical(v0, p0) || !looseIdentical(v1, p1) || !looseIdentical(v2, p2) ||
            !looseIdentical(v3, p3) || !looseIdentical(v4, p4) || !looseIdentical(v5, p5) ||
            !looseIdentical(v6, p6) || !looseIdentical(v7, p7) || !looseIdentical(v8, p8) ||
            !looseIdentical(v9, p9)) {
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
//# sourceMappingURL=view_utils.js.map