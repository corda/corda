/**
 * @license
 * Copyright Google Inc. All Rights Reserved.
 *
 * Use of this source code is governed by an MIT-style license that can be
 * found in the LICENSE file at https://angular.io/license
 */
"use strict";
var lang_1 = require('../facade/lang');
var WebAnimationsPlayer = (function () {
    function WebAnimationsPlayer(_player, totalTime) {
        var _this = this;
        this._player = _player;
        this.totalTime = totalTime;
        this._subscriptions = [];
        this._finished = false;
        this.parentPlayer = null;
        // this is required to make the player startable at a later time
        this.reset();
        this._player.onfinish = function () { return _this._onFinish(); };
    }
    WebAnimationsPlayer.prototype._onFinish = function () {
        if (!this._finished) {
            this._finished = true;
            if (!lang_1.isPresent(this.parentPlayer)) {
                this.destroy();
            }
            this._subscriptions.forEach(function (fn) { return fn(); });
            this._subscriptions = [];
        }
    };
    WebAnimationsPlayer.prototype.onDone = function (fn) { this._subscriptions.push(fn); };
    WebAnimationsPlayer.prototype.play = function () { this._player.play(); };
    WebAnimationsPlayer.prototype.pause = function () { this._player.pause(); };
    WebAnimationsPlayer.prototype.finish = function () {
        this._onFinish();
        this._player.finish();
    };
    WebAnimationsPlayer.prototype.reset = function () { this._player.cancel(); };
    WebAnimationsPlayer.prototype.restart = function () {
        this.reset();
        this.play();
    };
    WebAnimationsPlayer.prototype.destroy = function () {
        this.reset();
        this._onFinish();
    };
    WebAnimationsPlayer.prototype.setPosition = function (p /** TODO #9100 */) { this._player.currentTime = p * this.totalTime; };
    WebAnimationsPlayer.prototype.getPosition = function () { return this._player.currentTime / this.totalTime; };
    return WebAnimationsPlayer;
}());
exports.WebAnimationsPlayer = WebAnimationsPlayer;
//# sourceMappingURL=web_animations_player.js.map