/**
 * @license
 * Copyright Google Inc. All Rights Reserved.
 *
 * Use of this source code is governed by an MIT-style license that can be
 * found in the LICENSE file at https://angular.io/license
 */
"use strict";
var lang_1 = require('../facade/lang');
var math_1 = require('../facade/math');
var AnimationGroupPlayer = (function () {
    function AnimationGroupPlayer(_players) {
        var _this = this;
        this._players = _players;
        this._subscriptions = [];
        this._finished = false;
        this.parentPlayer = null;
        var count = 0;
        var total = this._players.length;
        if (total == 0) {
            lang_1.scheduleMicroTask(function () { return _this._onFinish(); });
        }
        else {
            this._players.forEach(function (player) {
                player.parentPlayer = _this;
                player.onDone(function () {
                    if (++count >= total) {
                        _this._onFinish();
                    }
                });
            });
        }
    }
    AnimationGroupPlayer.prototype._onFinish = function () {
        if (!this._finished) {
            this._finished = true;
            if (!lang_1.isPresent(this.parentPlayer)) {
                this.destroy();
            }
            this._subscriptions.forEach(function (subscription) { return subscription(); });
            this._subscriptions = [];
        }
    };
    AnimationGroupPlayer.prototype.onDone = function (fn) { this._subscriptions.push(fn); };
    AnimationGroupPlayer.prototype.play = function () { this._players.forEach(function (player) { return player.play(); }); };
    AnimationGroupPlayer.prototype.pause = function () { this._players.forEach(function (player) { return player.pause(); }); };
    AnimationGroupPlayer.prototype.restart = function () { this._players.forEach(function (player) { return player.restart(); }); };
    AnimationGroupPlayer.prototype.finish = function () {
        this._onFinish();
        this._players.forEach(function (player) { return player.finish(); });
    };
    AnimationGroupPlayer.prototype.destroy = function () {
        this._onFinish();
        this._players.forEach(function (player) { return player.destroy(); });
    };
    AnimationGroupPlayer.prototype.reset = function () { this._players.forEach(function (player) { return player.reset(); }); };
    AnimationGroupPlayer.prototype.setPosition = function (p /** TODO #9100 */) {
        this._players.forEach(function (player) { player.setPosition(p); });
    };
    AnimationGroupPlayer.prototype.getPosition = function () {
        var min = 0;
        this._players.forEach(function (player) {
            var p = player.getPosition();
            min = math_1.Math.min(p, min);
        });
        return min;
    };
    return AnimationGroupPlayer;
}());
exports.AnimationGroupPlayer = AnimationGroupPlayer;
//# sourceMappingURL=animation_group_player.js.map