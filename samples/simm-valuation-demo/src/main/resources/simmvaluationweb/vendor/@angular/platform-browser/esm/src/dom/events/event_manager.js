/**
 * @license
 * Copyright Google Inc. All Rights Reserved.
 *
 * Use of this source code is governed by an MIT-style license that can be
 * found in the LICENSE file at https://angular.io/license
 */
import { Inject, Injectable, NgZone, OpaqueToken } from '@angular/core';
import { ListWrapper } from '../../facade/collection';
import { BaseException } from '../../facade/exceptions';
/**
 * @stable
 */
export const EVENT_MANAGER_PLUGINS = new OpaqueToken('EventManagerPlugins');
export class EventManager {
    constructor(plugins, _zone) {
        this._zone = _zone;
        plugins.forEach(p => p.manager = this);
        this._plugins = ListWrapper.reversed(plugins);
    }
    addEventListener(element, eventName, handler) {
        var plugin = this._findPluginFor(eventName);
        return plugin.addEventListener(element, eventName, handler);
    }
    addGlobalEventListener(target, eventName, handler) {
        var plugin = this._findPluginFor(eventName);
        return plugin.addGlobalEventListener(target, eventName, handler);
    }
    getZone() { return this._zone; }
    /** @internal */
    _findPluginFor(eventName) {
        var plugins = this._plugins;
        for (var i = 0; i < plugins.length; i++) {
            var plugin = plugins[i];
            if (plugin.supports(eventName)) {
                return plugin;
            }
        }
        throw new BaseException(`No event manager plugin found for event ${eventName}`);
    }
}
/** @nocollapse */
EventManager.decorators = [
    { type: Injectable },
];
/** @nocollapse */
EventManager.ctorParameters = [
    { type: Array, decorators: [{ type: Inject, args: [EVENT_MANAGER_PLUGINS,] },] },
    { type: NgZone, },
];
export class EventManagerPlugin {
    // That is equivalent to having supporting $event.target
    supports(eventName) { return false; }
    addEventListener(element, eventName, handler) {
        throw 'not implemented';
    }
    addGlobalEventListener(element, eventName, handler) {
        throw 'not implemented';
    }
}
//# sourceMappingURL=event_manager.js.map