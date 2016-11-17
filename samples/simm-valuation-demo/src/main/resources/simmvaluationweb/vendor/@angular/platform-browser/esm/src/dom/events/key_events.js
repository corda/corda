/**
 * @license
 * Copyright Google Inc. All Rights Reserved.
 *
 * Use of this source code is governed by an MIT-style license that can be
 * found in the LICENSE file at https://angular.io/license
 */
import { Injectable } from '@angular/core';
import { isPresent, StringWrapper } from '../../facade/lang';
import { StringMapWrapper, ListWrapper } from '../../facade/collection';
import { getDOM } from '../dom_adapter';
import { EventManagerPlugin } from './event_manager';
var modifierKeys = ['alt', 'control', 'meta', 'shift'];
var modifierKeyGetters = {
    'alt': (event) => event.altKey,
    'control': (event) => event.ctrlKey,
    'meta': (event) => event.metaKey,
    'shift': (event) => event.shiftKey
};
export class KeyEventsPlugin extends EventManagerPlugin {
    constructor() {
        super();
    }
    supports(eventName) {
        return isPresent(KeyEventsPlugin.parseEventName(eventName));
    }
    addEventListener(element, eventName, handler) {
        var parsedEvent = KeyEventsPlugin.parseEventName(eventName);
        var outsideHandler = KeyEventsPlugin.eventCallback(element, StringMapWrapper.get(parsedEvent, 'fullKey'), handler, this.manager.getZone());
        return this.manager.getZone().runOutsideAngular(() => {
            return getDOM().onAndCancel(element, StringMapWrapper.get(parsedEvent, 'domEventName'), outsideHandler);
        });
    }
    static parseEventName(eventName) {
        var parts = eventName.toLowerCase().split('.');
        var domEventName = parts.shift();
        if ((parts.length === 0) ||
            !(StringWrapper.equals(domEventName, 'keydown') ||
                StringWrapper.equals(domEventName, 'keyup'))) {
            return null;
        }
        var key = KeyEventsPlugin._normalizeKey(parts.pop());
        var fullKey = '';
        modifierKeys.forEach(modifierName => {
            if (ListWrapper.contains(parts, modifierName)) {
                ListWrapper.remove(parts, modifierName);
                fullKey += modifierName + '.';
            }
        });
        fullKey += key;
        if (parts.length != 0 || key.length === 0) {
            // returning null instead of throwing to let another plugin process the event
            return null;
        }
        var result = StringMapWrapper.create();
        StringMapWrapper.set(result, 'domEventName', domEventName);
        StringMapWrapper.set(result, 'fullKey', fullKey);
        return result;
    }
    static getEventFullKey(event) {
        var fullKey = '';
        var key = getDOM().getEventKey(event);
        key = key.toLowerCase();
        if (StringWrapper.equals(key, ' ')) {
            key = 'space'; // for readability
        }
        else if (StringWrapper.equals(key, '.')) {
            key = 'dot'; // because '.' is used as a separator in event names
        }
        modifierKeys.forEach(modifierName => {
            if (modifierName != key) {
                var modifierGetter = StringMapWrapper.get(modifierKeyGetters, modifierName);
                if (modifierGetter(event)) {
                    fullKey += modifierName + '.';
                }
            }
        });
        fullKey += key;
        return fullKey;
    }
    static eventCallback(element, fullKey, handler, zone) {
        return (event /** TODO #9100 */) => {
            if (StringWrapper.equals(KeyEventsPlugin.getEventFullKey(event), fullKey)) {
                zone.runGuarded(() => handler(event));
            }
        };
    }
    /** @internal */
    static _normalizeKey(keyName) {
        // TODO: switch to a StringMap if the mapping grows too much
        switch (keyName) {
            case 'esc':
                return 'escape';
            default:
                return keyName;
        }
    }
}
/** @nocollapse */
KeyEventsPlugin.decorators = [
    { type: Injectable },
];
/** @nocollapse */
KeyEventsPlugin.ctorParameters = [];
//# sourceMappingURL=key_events.js.map