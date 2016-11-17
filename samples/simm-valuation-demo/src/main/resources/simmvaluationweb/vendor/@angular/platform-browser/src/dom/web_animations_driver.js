/**
 * @license
 * Copyright Google Inc. All Rights Reserved.
 *
 * Use of this source code is governed by an MIT-style license that can be
 * found in the LICENSE file at https://angular.io/license
 */
"use strict";
var core_1 = require('@angular/core');
var collection_1 = require('../facade/collection');
var lang_1 = require('../facade/lang');
var dom_adapter_1 = require('./dom_adapter');
var util_1 = require('./util');
var web_animations_player_1 = require('./web_animations_player');
var WebAnimationsDriver = (function () {
    function WebAnimationsDriver() {
    }
    WebAnimationsDriver.prototype.animate = function (element, startingStyles, keyframes, duration, delay, easing) {
        var anyElm = element;
        var formattedSteps = [];
        var startingStyleLookup = {};
        if (lang_1.isPresent(startingStyles) && startingStyles.styles.length > 0) {
            startingStyleLookup = _populateStyles(anyElm, startingStyles, {});
            startingStyleLookup['offset'] = 0;
            formattedSteps.push(startingStyleLookup);
        }
        keyframes.forEach(function (keyframe) {
            var data = _populateStyles(anyElm, keyframe.styles, startingStyleLookup);
            data['offset'] = keyframe.offset;
            formattedSteps.push(data);
        });
        // this is a special case when only styles are applied as an
        // animation. When this occurs we want to animate from start to
        // end with the same values. Removing the offset and having only
        // start/end values is suitable enough for the web-animations API
        if (formattedSteps.length == 1) {
            var start = formattedSteps[0];
            start['offset'] = null;
            formattedSteps = [start, start];
        }
        var playerOptions = {
            'duration': duration,
            'delay': delay,
            'easing': easing,
            'fill': 'both' // we use `both` because it allows for styling at 0% to work with `delay`
        };
        var player = this._triggerWebAnimation(anyElm, formattedSteps, playerOptions);
        return new web_animations_player_1.WebAnimationsPlayer(player, duration);
    };
    /** @internal */
    WebAnimationsDriver.prototype._triggerWebAnimation = function (elm, keyframes, options) {
        return elm.animate(keyframes, options);
    };
    return WebAnimationsDriver;
}());
exports.WebAnimationsDriver = WebAnimationsDriver;
function _populateStyles(element, styles, defaultStyles) {
    var data = {};
    styles.styles.forEach(function (entry) {
        collection_1.StringMapWrapper.forEach(entry, function (val, prop) {
            var formattedProp = util_1.dashCaseToCamelCase(prop);
            data[formattedProp] = val == core_1.AUTO_STYLE ?
                _computeStyle(element, formattedProp) :
                val.toString() + _resolveStyleUnit(val, prop, formattedProp);
        });
    });
    collection_1.StringMapWrapper.forEach(defaultStyles, function (value, prop) {
        if (!lang_1.isPresent(data[prop])) {
            data[prop] = value;
        }
    });
    return data;
}
function _resolveStyleUnit(val, userProvidedProp, formattedProp) {
    var unit = '';
    if (_isPixelDimensionStyle(formattedProp) && val != 0 && val != '0') {
        if (lang_1.isNumber(val)) {
            unit = 'px';
        }
        else if (_findDimensionalSuffix(val.toString()).length == 0) {
            throw new core_1.BaseException('Please provide a CSS unit value for ' + userProvidedProp + ':' + val);
        }
    }
    return unit;
}
var _$0 = 48;
var _$9 = 57;
var _$PERIOD = 46;
function _findDimensionalSuffix(value) {
    for (var i = 0; i < value.length; i++) {
        var c = lang_1.StringWrapper.charCodeAt(value, i);
        if ((c >= _$0 && c <= _$9) || c == _$PERIOD)
            continue;
        return value.substring(i, value.length);
    }
    return '';
}
function _isPixelDimensionStyle(prop) {
    switch (prop) {
        case 'width':
        case 'height':
        case 'minWidth':
        case 'minHeight':
        case 'maxWidth':
        case 'maxHeight':
        case 'left':
        case 'top':
        case 'bottom':
        case 'right':
        case 'fontSize':
        case 'outlineWidth':
        case 'outlineOffset':
        case 'paddingTop':
        case 'paddingLeft':
        case 'paddingBottom':
        case 'paddingRight':
        case 'marginTop':
        case 'marginLeft':
        case 'marginBottom':
        case 'marginRight':
        case 'borderRadius':
        case 'borderWidth':
        case 'borderTopWidth':
        case 'borderLeftWidth':
        case 'borderRightWidth':
        case 'borderBottomWidth':
        case 'textIndent':
            return true;
        default:
            return false;
    }
}
function _computeStyle(element, prop) {
    return dom_adapter_1.getDOM().getComputedStyle(element)[prop];
}
//# sourceMappingURL=web_animations_driver.js.map