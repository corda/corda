/**
 * @license
 * Copyright Google Inc. All Rights Reserved.
 *
 * Use of this source code is governed by an MIT-style license that can be
 * found in the LICENSE file at https://angular.io/license
 */
import { Inject, Injectable, OpaqueToken } from '@angular/core';
import { BaseException } from '../../facade/exceptions';
import { isPresent } from '../../facade/lang';
import { HammerGesturesPluginCommon } from './hammer_common';
/**
 * A DI token that you can use to provide{@link HammerGestureConfig} to Angular. Use it to configure
 * Hammer gestures.
 *
 * @experimental
 */
export const HAMMER_GESTURE_CONFIG = new OpaqueToken('HammerGestureConfig');
export class HammerGestureConfig {
    constructor() {
        this.events = [];
        this.overrides = {};
    }
    buildHammer(element) {
        var mc = new Hammer(element);
        mc.get('pinch').set({ enable: true });
        mc.get('rotate').set({ enable: true });
        for (let eventName in this.overrides) {
            mc.get(eventName).set(this.overrides[eventName]);
        }
        return mc;
    }
}
/** @nocollapse */
HammerGestureConfig.decorators = [
    { type: Injectable },
];
export class HammerGesturesPlugin extends HammerGesturesPluginCommon {
    constructor(_config) {
        super();
        this._config = _config;
    }
    supports(eventName) {
        if (!super.supports(eventName) && !this.isCustomEvent(eventName))
            return false;
        if (!isPresent(window['Hammer'])) {
            throw new BaseException(`Hammer.js is not loaded, can not bind ${eventName} event`);
        }
        return true;
    }
    addEventListener(element, eventName, handler) {
        var zone = this.manager.getZone();
        eventName = eventName.toLowerCase();
        return zone.runOutsideAngular(() => {
            // Creating the manager bind events, must be done outside of angular
            var mc = this._config.buildHammer(element);
            var callback = function (eventObj /** TODO #???? */) {
                zone.runGuarded(function () { handler(eventObj); });
            };
            mc.on(eventName, callback);
            return () => { mc.off(eventName, callback); };
        });
    }
    isCustomEvent(eventName) { return this._config.events.indexOf(eventName) > -1; }
}
/** @nocollapse */
HammerGesturesPlugin.decorators = [
    { type: Injectable },
];
/** @nocollapse */
HammerGesturesPlugin.ctorParameters = [
    { type: HammerGestureConfig, decorators: [{ type: Inject, args: [HAMMER_GESTURE_CONFIG,] },] },
];
//# sourceMappingURL=hammer_gestures.js.map