/**
 * @license
 * Copyright Google Inc. All Rights Reserved.
 *
 * Use of this source code is governed by an MIT-style license that can be
 * found in the LICENSE file at https://angular.io/license
 */
"use strict";
var default_iterable_differ_1 = require('./differs/default_iterable_differ');
var default_keyvalue_differ_1 = require('./differs/default_keyvalue_differ');
var iterable_differs_1 = require('./differs/iterable_differs');
var keyvalue_differs_1 = require('./differs/keyvalue_differs');
var change_detection_util_1 = require('./change_detection_util');
exports.SimpleChange = change_detection_util_1.SimpleChange;
exports.ValueUnwrapper = change_detection_util_1.ValueUnwrapper;
exports.WrappedValue = change_detection_util_1.WrappedValue;
exports.devModeEqual = change_detection_util_1.devModeEqual;
exports.looseIdentical = change_detection_util_1.looseIdentical;
exports.uninitialized = change_detection_util_1.uninitialized;
var change_detector_ref_1 = require('./change_detector_ref');
exports.ChangeDetectorRef = change_detector_ref_1.ChangeDetectorRef;
var constants_1 = require('./constants');
exports.CHANGE_DETECTION_STRATEGY_VALUES = constants_1.CHANGE_DETECTION_STRATEGY_VALUES;
exports.ChangeDetectionStrategy = constants_1.ChangeDetectionStrategy;
exports.ChangeDetectorStatus = constants_1.ChangeDetectorStatus;
exports.isDefaultChangeDetectionStrategy = constants_1.isDefaultChangeDetectionStrategy;
var default_iterable_differ_2 = require('./differs/default_iterable_differ');
exports.CollectionChangeRecord = default_iterable_differ_2.CollectionChangeRecord;
exports.DefaultIterableDifferFactory = default_iterable_differ_2.DefaultIterableDifferFactory;
var default_iterable_differ_3 = require('./differs/default_iterable_differ');
exports.DefaultIterableDiffer = default_iterable_differ_3.DefaultIterableDiffer;
var default_keyvalue_differ_2 = require('./differs/default_keyvalue_differ');
exports.DefaultKeyValueDifferFactory = default_keyvalue_differ_2.DefaultKeyValueDifferFactory;
exports.KeyValueChangeRecord = default_keyvalue_differ_2.KeyValueChangeRecord;
var iterable_differs_2 = require('./differs/iterable_differs');
exports.IterableDiffers = iterable_differs_2.IterableDiffers;
var keyvalue_differs_2 = require('./differs/keyvalue_differs');
exports.KeyValueDiffers = keyvalue_differs_2.KeyValueDiffers;
/**
 * Structural diffing for `Object`s and `Map`s.
 */
exports.keyValDiff = 
/*@ts2dart_const*/ [new default_keyvalue_differ_1.DefaultKeyValueDifferFactory()];
/**
 * Structural diffing for `Iterable` types such as `Array`s.
 */
exports.iterableDiff = 
/*@ts2dart_const*/ [new default_iterable_differ_1.DefaultIterableDifferFactory()];
exports.defaultIterableDiffers = new iterable_differs_1.IterableDiffers(exports.iterableDiff);
exports.defaultKeyValueDiffers = new keyvalue_differs_1.KeyValueDiffers(exports.keyValDiff);
//# sourceMappingURL=change_detection.js.map