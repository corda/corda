/**
 * @license
 * Copyright Google Inc. All Rights Reserved.
 *
 * Use of this source code is governed by an MIT-style license that can be
 * found in the LICENSE file at https://angular.io/license
 */
"use strict";
var collection_1 = require('../facade/collection');
var lang_1 = require('../facade/lang');
var animation_constants_1 = require('./animation_constants');
var metadata_1 = require('./metadata');
function prepareFinalAnimationStyles(previousStyles, newStyles, nullValue) {
    if (nullValue === void 0) { nullValue = null; }
    var finalStyles = {};
    collection_1.StringMapWrapper.forEach(newStyles, function (value, prop) {
        finalStyles[prop] = value == metadata_1.AUTO_STYLE ? nullValue : value.toString();
    });
    collection_1.StringMapWrapper.forEach(previousStyles, function (value, prop) {
        if (!lang_1.isPresent(finalStyles[prop])) {
            finalStyles[prop] = nullValue;
        }
    });
    return finalStyles;
}
exports.prepareFinalAnimationStyles = prepareFinalAnimationStyles;
function balanceAnimationKeyframes(collectedStyles, finalStateStyles, keyframes) {
    var limit = keyframes.length - 1;
    var firstKeyframe = keyframes[0];
    // phase 1: copy all the styles from the first keyframe into the lookup map
    var flatenedFirstKeyframeStyles = flattenStyles(firstKeyframe.styles.styles);
    var extraFirstKeyframeStyles = {};
    var hasExtraFirstStyles = false;
    collection_1.StringMapWrapper.forEach(collectedStyles, function (value, prop) {
        // if the style is already defined in the first keyframe then
        // we do not replace it.
        if (!flatenedFirstKeyframeStyles[prop]) {
            flatenedFirstKeyframeStyles[prop] = value;
            extraFirstKeyframeStyles[prop] = value;
            hasExtraFirstStyles = true;
        }
    });
    var keyframeCollectedStyles = collection_1.StringMapWrapper.merge({}, flatenedFirstKeyframeStyles);
    // phase 2: normalize the final keyframe
    var finalKeyframe = keyframes[limit];
    collection_1.ListWrapper.insert(finalKeyframe.styles.styles, 0, finalStateStyles);
    var flatenedFinalKeyframeStyles = flattenStyles(finalKeyframe.styles.styles);
    var extraFinalKeyframeStyles = {};
    var hasExtraFinalStyles = false;
    collection_1.StringMapWrapper.forEach(keyframeCollectedStyles, function (value, prop) {
        if (!lang_1.isPresent(flatenedFinalKeyframeStyles[prop])) {
            extraFinalKeyframeStyles[prop] = metadata_1.AUTO_STYLE;
            hasExtraFinalStyles = true;
        }
    });
    if (hasExtraFinalStyles) {
        finalKeyframe.styles.styles.push(extraFinalKeyframeStyles);
    }
    collection_1.StringMapWrapper.forEach(flatenedFinalKeyframeStyles, function (value, prop) {
        if (!lang_1.isPresent(flatenedFirstKeyframeStyles[prop])) {
            extraFirstKeyframeStyles[prop] = metadata_1.AUTO_STYLE;
            hasExtraFirstStyles = true;
        }
    });
    if (hasExtraFirstStyles) {
        firstKeyframe.styles.styles.push(extraFirstKeyframeStyles);
    }
    return keyframes;
}
exports.balanceAnimationKeyframes = balanceAnimationKeyframes;
function clearStyles(styles) {
    var finalStyles = {};
    collection_1.StringMapWrapper.keys(styles).forEach(function (key) { finalStyles[key] = null; });
    return finalStyles;
}
exports.clearStyles = clearStyles;
function collectAndResolveStyles(collection, styles) {
    return styles.map(function (entry) {
        var stylesObj = {};
        collection_1.StringMapWrapper.forEach(entry, function (value, prop) {
            if (value == animation_constants_1.FILL_STYLE_FLAG) {
                value = collection[prop];
                if (!lang_1.isPresent(value)) {
                    value = metadata_1.AUTO_STYLE;
                }
            }
            collection[prop] = value;
            stylesObj[prop] = value;
        });
        return stylesObj;
    });
}
exports.collectAndResolveStyles = collectAndResolveStyles;
function renderStyles(element, renderer, styles) {
    collection_1.StringMapWrapper.forEach(styles, function (value, prop) { renderer.setElementStyle(element, prop, value); });
}
exports.renderStyles = renderStyles;
function flattenStyles(styles) {
    var finalStyles = {};
    styles.forEach(function (entry) {
        collection_1.StringMapWrapper.forEach(entry, function (value, prop) { finalStyles[prop] = value; });
    });
    return finalStyles;
}
exports.flattenStyles = flattenStyles;
//# sourceMappingURL=animation_style_util.js.map