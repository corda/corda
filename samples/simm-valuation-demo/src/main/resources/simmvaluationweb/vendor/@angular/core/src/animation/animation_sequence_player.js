/**
 * @license
 * Copyright Google Inc. All Rights Reserved.
 *
 * Use of this source code is governed by an MIT-style license that can be
 * found in the LICENSE file at https://angular.io/license
 */
"use strict";
var lang_1 = require('../facade/lang');
var animation_player_1 = require('./animation_player');
var AnimationSequencePlayer = (function () {
    function AnimationSequencePlayer(_players) {
        var _this = this;
        this._players = _players;
        this._currentIndex = 0;
        this._subscriptions = [];
        this._finished = false;
        this.parentPlayer = null;
        this._players.forEach(function (player) { player.parentPlayer = _this; });
        this._onNext(false);
    }
    AnimationSequencePlayer.prototype._onNext = function (start) {
        var _this = this;
        if (this._finished)
            return;
        if (this._players.length == 0) {
            this._activePlayer = new animation_player_1.NoOpAnimationPlayer();
            lang_1.scheduleMicroTask(function () { return _this._onFinish(); });
        }
        else if (this._currentIndex >= this._players.length) {
            this._activePlayer = new animation_player_1.NoOpAnimationPlayer();
            this._onFinish();
        }
        else {
            var player = this._players[this._currentIndex++];
            player.onDone(function () { return _this._onNext(true); });
            this._activePlayer = player;
            if (start) {
                player.play();
            }
        }
    };
    AnimationSequencePlayer.prototype._onFinish = function () {
        if (!this._finished) {
            this._finished = true;
            if (!lang_1.isPresent(this.parentPlayer)) {
                this.destroy();
            }
            this._subscriptions.forEach(function (subscription) { return subscription(); });
            this._subscriptions = [];
        }
    };
    AnimationSequencePlayer.prototype.onDone = function (fn) { this._subscriptions.push(fn); };
    AnimationSequencePlayer.prototype.play = function () { this._activePlayer.play(); };
    AnimationSequencePlayer.prototype.pause = function () { this._activePlayer.pause(); };
    AnimationSequencePlayer.prototype.restart = function () {
        if (this._players.length > 0) {
            this.reset();
            this._players[0].restart();
        }
    };
    AnimationSequencePlayer.prototype.reset = function () { this._players.forEach(function (player) { return player.reset(); }); };
    AnimationSequencePlayer.prototype.finish = function () {
        this._onFinish();
        this._players.forEach(function (player) { return player.finish(); });
    };
    AnimationSequencePlayer.prototype.destroy = function () {
        this._onFinish();
        this._players.forEach(function (player) { return player.destroy(); });
    };
    AnimationSequencePlayer.prototype.setPosition = function (p /** TODO #9100 */) { this._players[0].setPosition(p); };
    AnimationSequencePlayer.prototype.getPosition = function () { return this._players[0].getPosition(); };
    return AnimationSequencePlayer;
}());
exports.AnimationSequencePlayer = AnimationSequencePlayer;
//# sourceMappingURL=animation_sequence_player.js.map