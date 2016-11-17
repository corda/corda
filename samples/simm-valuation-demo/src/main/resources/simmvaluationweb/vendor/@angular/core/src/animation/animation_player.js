/**
 * @license
 * Copyright Google Inc. All Rights Reserved.
 *
 * Use of this source code is governed by an MIT-style license that can be
 * found in the LICENSE file at https://angular.io/license
 */
"use strict";
var exceptions_1 = require('../facade/exceptions');
var lang_1 = require('../facade/lang');
/**
 * @experimental Animation support is experimental.
 */
var AnimationPlayer = (function () {
    function AnimationPlayer() {
    }
    Object.defineProperty(AnimationPlayer.prototype, "parentPlayer", {
        get: function () { throw new exceptions_1.BaseException('NOT IMPLEMENTED: Base Class'); },
        set: function (player) {
            throw new exceptions_1.BaseException('NOT IMPLEMENTED: Base Class');
        },
        enumerable: true,
        configurable: true
    });
    return AnimationPlayer;
}());
exports.AnimationPlayer = AnimationPlayer;
var NoOpAnimationPlayer = (function () {
    function NoOpAnimationPlayer() {
        var _this = this;
        this._subscriptions = [];
        this.parentPlayer = null;
        lang_1.scheduleMicroTask(function () { return _this._onFinish(); });
    }
    /** @internal */
    NoOpAnimationPlayer.prototype._onFinish = function () {
        this._subscriptions.forEach(function (entry) { entry(); });
        this._subscriptions = [];
    };
    NoOpAnimationPlayer.prototype.onDone = function (fn) { this._subscriptions.push(fn); };
    NoOpAnimationPlayer.prototype.play = function () { };
    NoOpAnimationPlayer.prototype.pause = function () { };
    NoOpAnimationPlayer.prototype.restart = function () { };
    NoOpAnimationPlayer.prototype.finish = function () { this._onFinish(); };
    NoOpAnimationPlayer.prototype.destroy = function () { };
    NoOpAnimationPlayer.prototype.reset = function () { };
    NoOpAnimationPlayer.prototype.setPosition = function (p /** TODO #9100 */) { };
    NoOpAnimationPlayer.prototype.getPosition = function () { return 0; };
    return NoOpAnimationPlayer;
}());
exports.NoOpAnimationPlayer = NoOpAnimationPlayer;
//# sourceMappingURL=animation_player.js.map