/**
 * @license
 * Copyright Google Inc. All Rights Reserved.
 *
 * Use of this source code is governed by an MIT-style license that can be
 * found in the LICENSE file at https://angular.io/license
 */
import { Injectable, RenderComponentType, ViewEncapsulation } from '@angular/core';
import { VIEW_ENCAPSULATION_VALUES } from '../../../core_private';
import { BaseException } from '../../facade/exceptions';
import { isArray, isPresent, serializeEnum } from '../../facade/lang';
import { RenderStore } from './render_store';
import { LocationType } from './serialized_types';
// PRIMITIVE is any type that does not need to be serialized (string, number, boolean)
// We set it to String so that it is considered a Type.
/**
 * @experimental WebWorker support in Angular is currently experimental.
 */
export const PRIMITIVE = String;
export class Serializer {
    constructor(_renderStore) {
        this._renderStore = _renderStore;
    }
    serialize(obj, type) {
        if (!isPresent(obj)) {
            return null;
        }
        if (isArray(obj)) {
            return obj.map(v => this.serialize(v, type));
        }
        if (type == PRIMITIVE) {
            return obj;
        }
        if (type == RenderStoreObject) {
            return this._renderStore.serialize(obj);
        }
        else if (type === RenderComponentType) {
            return this._serializeRenderComponentType(obj);
        }
        else if (type === ViewEncapsulation) {
            return serializeEnum(obj);
        }
        else if (type === LocationType) {
            return this._serializeLocation(obj);
        }
        else {
            throw new BaseException('No serializer for ' + type.toString());
        }
    }
    deserialize(map, type, data) {
        if (!isPresent(map)) {
            return null;
        }
        if (isArray(map)) {
            var obj = [];
            map.forEach(val => obj.push(this.deserialize(val, type, data)));
            return obj;
        }
        if (type == PRIMITIVE) {
            return map;
        }
        if (type == RenderStoreObject) {
            return this._renderStore.deserialize(map);
        }
        else if (type === RenderComponentType) {
            return this._deserializeRenderComponentType(map);
        }
        else if (type === ViewEncapsulation) {
            return VIEW_ENCAPSULATION_VALUES[map];
        }
        else if (type === LocationType) {
            return this._deserializeLocation(map);
        }
        else {
            throw new BaseException('No deserializer for ' + type.toString());
        }
    }
    _serializeLocation(loc) {
        return {
            'href': loc.href,
            'protocol': loc.protocol,
            'host': loc.host,
            'hostname': loc.hostname,
            'port': loc.port,
            'pathname': loc.pathname,
            'search': loc.search,
            'hash': loc.hash,
            'origin': loc.origin
        };
    }
    _deserializeLocation(loc) {
        return new LocationType(loc['href'], loc['protocol'], loc['host'], loc['hostname'], loc['port'], loc['pathname'], loc['search'], loc['hash'], loc['origin']);
    }
    _serializeRenderComponentType(obj) {
        return {
            'id': obj.id,
            'templateUrl': obj.templateUrl,
            'slotCount': obj.slotCount,
            'encapsulation': this.serialize(obj.encapsulation, ViewEncapsulation),
            'styles': this.serialize(obj.styles, PRIMITIVE)
        };
    }
    _deserializeRenderComponentType(map) {
        return new RenderComponentType(map['id'], map['templateUrl'], map['slotCount'], this.deserialize(map['encapsulation'], ViewEncapsulation), this.deserialize(map['styles'], PRIMITIVE));
    }
}
/** @nocollapse */
Serializer.decorators = [
    { type: Injectable },
];
/** @nocollapse */
Serializer.ctorParameters = [
    { type: RenderStore, },
];
export class RenderStoreObject {
}
//# sourceMappingURL=serializer.js.map