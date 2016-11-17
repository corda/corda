/**
 * @license
 * Copyright Google Inc. All Rights Reserved.
 *
 * Use of this source code is governed by an MIT-style license that can be
 * found in the LICENSE file at https://angular.io/license
 */
import { Injectable } from '@angular/core';
import { getDOM } from '../dom_adapter';
import { EventManagerPlugin } from './event_manager';
export class DomEventsPlugin extends EventManagerPlugin {
    // This plugin should come last in the list of plugins, because it accepts all
    // events.
    supports(eventName) { return true; }
    addEventListener(element, eventName, handler) {
        var zone = this.manager.getZone();
        var outsideHandler = (event /** TODO #9100 */) => zone.runGuarded(() => handler(event));
        return this.manager.getZone().runOutsideAngular(() => getDOM().onAndCancel(element, eventName, outsideHandler));
    }
    addGlobalEventListener(target, eventName, handler) {
        var element = getDOM().getGlobalEventTarget(target);
        var zone = this.manager.getZone();
        var outsideHandler = (event /** TODO #9100 */) => zone.runGuarded(() => handler(event));
        return this.manager.getZone().runOutsideAngular(() => getDOM().onAndCancel(element, eventName, outsideHandler));
    }
}
/** @nocollapse */
DomEventsPlugin.decorators = [
    { type: Injectable },
];
//# sourceMappingURL=dom_events.js.map