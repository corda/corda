/**
 * @license
 * Copyright Google Inc. All Rights Reserved.
 *
 * Use of this source code is governed by an MIT-style license that can be
 * found in the LICENSE file at https://angular.io/license
 */
import { isPresent, scheduleMicroTask } from '../facade/lang';
import { Math } from '../facade/math';
export class AnimationGroupPlayer {
    constructor(_players) {
        this._players = _players;
        this._subscriptions = [];
        this._finished = false;
        this.parentPlayer = null;
        var count = 0;
        var total = this._players.length;
        if (total == 0) {
            scheduleMicroTask(() => this._onFinish());
        }
        else {
            this._players.forEach(player => {
                player.parentPlayer = this;
                player.onDone(() => {
                    if (++count >= total) {
                        this._onFinish();
                    }
                });
            });
        }
    }
    _onFinish() {
        if (!this._finished) {
            this._finished = true;
            if (!isPresent(this.parentPlayer)) {
                this.destroy();
            }
            this._subscriptions.forEach(subscription => subscription());
            this._subscriptions = [];
        }
    }
    onDone(fn) { this._subscriptions.push(fn); }
    play() { this._players.forEach(player => player.play()); }
    pause() { this._players.forEach(player => player.pause()); }
    restart() { this._players.forEach(player => player.restart()); }
    finish() {
        this._onFinish();
        this._players.forEach(player => player.finish());
    }
    destroy() {
        this._onFinish();
        this._players.forEach(player => player.destroy());
    }
    reset() { this._players.forEach(player => player.reset()); }
    setPosition(p /** TODO #9100 */) {
        this._players.forEach(player => { player.setPosition(p); });
    }
    getPosition() {
        var min = 0;
        this._players.forEach(player => {
            var p = player.getPosition();
            min = Math.min(p, min);
        });
        return min;
    }
}
//# sourceMappingURL=animation_group_player.js.map