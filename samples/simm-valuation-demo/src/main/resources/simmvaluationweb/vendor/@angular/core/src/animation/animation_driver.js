/**
 * @license
 * Copyright Google Inc. All Rights Reserved.
 *
 * Use of this source code is governed by an MIT-style license that can be
 * found in the LICENSE file at https://angular.io/license
 */
"use strict";
var __extends = (this && this.__extends) || function (d, b) {
    for (var p in b) if (b.hasOwnProperty(p)) d[p] = b[p];
    function __() { this.constructor = d; }
    d.prototype = b === null ? Object.create(b) : (__.prototype = b.prototype, new __());
};
var animation_player_1 = require('./animation_player');
var AnimationDriver = (function () {
    function AnimationDriver() {
    }
    return AnimationDriver;
}());
exports.AnimationDriver = AnimationDriver;
var NoOpAnimationDriver = (function (_super) {
    __extends(NoOpAnimationDriver, _super);
    function NoOpAnimationDriver() {
        _super.apply(this, arguments);
    }
    NoOpAnimationDriver.prototype.animate = function (element, startingStyles, keyframes, duration, delay, easing) {
        return new animation_player_1.NoOpAnimationPlayer();
    };
    return NoOpAnimationDriver;
}(AnimationDriver));
exports.NoOpAnimationDriver = NoOpAnimationDriver;
//# sourceMappingURL=animation_driver.js.map