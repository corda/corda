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
var ActiveAnimationPlayersMap = (function () {
    function ActiveAnimationPlayersMap() {
        this._map = new collection_1.Map();
        this._allPlayers = [];
    }
    Object.defineProperty(ActiveAnimationPlayersMap.prototype, "length", {
        get: function () { return this.getAllPlayers().length; },
        enumerable: true,
        configurable: true
    });
    ActiveAnimationPlayersMap.prototype.find = function (element, animationName) {
        var playersByAnimation = this._map.get(element);
        if (lang_1.isPresent(playersByAnimation)) {
            return playersByAnimation[animationName];
        }
    };
    ActiveAnimationPlayersMap.prototype.findAllPlayersByElement = function (element) {
        var players = [];
        collection_1.StringMapWrapper.forEach(this._map.get(element), function (player /** TODO #9100 */) { return players.push(player); });
        return players;
    };
    ActiveAnimationPlayersMap.prototype.set = function (element, animationName, player) {
        var playersByAnimation = this._map.get(element);
        if (!lang_1.isPresent(playersByAnimation)) {
            playersByAnimation = {};
        }
        var existingEntry = playersByAnimation[animationName];
        if (lang_1.isPresent(existingEntry)) {
            this.remove(element, animationName);
        }
        playersByAnimation[animationName] = player;
        this._allPlayers.push(player);
        this._map.set(element, playersByAnimation);
    };
    ActiveAnimationPlayersMap.prototype.getAllPlayers = function () { return this._allPlayers; };
    ActiveAnimationPlayersMap.prototype.remove = function (element, animationName) {
        var playersByAnimation = this._map.get(element);
        if (lang_1.isPresent(playersByAnimation)) {
            var player = playersByAnimation[animationName];
            delete playersByAnimation[animationName];
            var index = this._allPlayers.indexOf(player);
            collection_1.ListWrapper.removeAt(this._allPlayers, index);
            if (collection_1.StringMapWrapper.isEmpty(playersByAnimation)) {
                this._map.delete(element);
            }
        }
    };
    return ActiveAnimationPlayersMap;
}());
exports.ActiveAnimationPlayersMap = ActiveAnimationPlayersMap;
//# sourceMappingURL=active_animation_players_map.js.map