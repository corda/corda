/**
 * @license
 * Copyright Google Inc. All Rights Reserved.
 *
 * Use of this source code is governed by an MIT-style license that can be
 * found in the LICENSE file at https://angular.io/license
 */
import { isPresent } from '../facade/lang';
export class WebAnimationsPlayer {
    constructor(_player, totalTime) {
        this._player = _player;
        this.totalTime = totalTime;
        this._subscriptions = [];
        this._finished = false;
        this.parentPlayer = null;
        // this is required to make the player startable at a later time
        this.reset();
        this._player.onfinish = () => this._onFinish();
    }
    _onFinish() {
        if (!this._finished) {
            this._finished = true;
            if (!isPresent(this.parentPlayer)) {
                this.destroy();
            }
            this._subscriptions.forEach(fn => fn());
            this._subscriptions = [];
        }
    }
    onDone(fn) { this._subscriptions.push(fn); }
    play() { this._player.play(); }
    pause() { this._player.pause(); }
    finish() {
        this._onFinish();
        this._player.finish();
    }
    reset() { this._player.cancel(); }
    restart() {
        this.reset();
        this.play();
    }
    destroy() {
        this.reset();
        this._onFinish();
    }
    setPosition(p /** TODO #9100 */) { this._player.currentTime = p * this.totalTime; }
    getPosition() { return this._player.currentTime / this.totalTime; }
}
//# sourceMappingURL=web_animations_player.js.map