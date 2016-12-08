/**
 * @license
 * Copyright Google Inc. All Rights Reserved.
 *
 * Use of this source code is governed by an MIT-style license that can be
 * found in the LICENSE file at https://angular.io/license
 */
import { BaseException } from '../facade/exceptions';
import { scheduleMicroTask } from '../facade/lang';
/**
 * @experimental Animation support is experimental.
 */
export class AnimationPlayer {
    get parentPlayer() { throw new BaseException('NOT IMPLEMENTED: Base Class'); }
    set parentPlayer(player) {
        throw new BaseException('NOT IMPLEMENTED: Base Class');
    }
}
export class NoOpAnimationPlayer {
    constructor() {
        this._subscriptions = [];
        this.parentPlayer = null;
        scheduleMicroTask(() => this._onFinish());
    }
    /** @internal */
    _onFinish() {
        this._subscriptions.forEach(entry => { entry(); });
        this._subscriptions = [];
    }
    onDone(fn) { this._subscriptions.push(fn); }
    play() { }
    pause() { }
    restart() { }
    finish() { this._onFinish(); }
    destroy() { }
    reset() { }
    setPosition(p /** TODO #9100 */) { }
    getPosition() { return 0; }
}
//# sourceMappingURL=animation_player.js.map