/**
 * @license
 * Copyright Google Inc. All Rights Reserved.
 *
 * Use of this source code is governed by an MIT-style license that can be
 * found in the LICENSE file at https://angular.io/license
 */
import { Injectable } from '@angular/core';
export class RenderStore {
    constructor() {
        this._nextIndex = 0;
        this._lookupById = new Map();
        this._lookupByObject = new Map();
    }
    allocateId() { return this._nextIndex++; }
    store(obj, id) {
        this._lookupById.set(id, obj);
        this._lookupByObject.set(obj, id);
    }
    remove(obj) {
        var index = this._lookupByObject.get(obj);
        this._lookupByObject.delete(obj);
        this._lookupById.delete(index);
    }
    deserialize(id) {
        if (id == null) {
            return null;
        }
        if (!this._lookupById.has(id)) {
            return null;
        }
        return this._lookupById.get(id);
    }
    serialize(obj) {
        if (obj == null) {
            return null;
        }
        return this._lookupByObject.get(obj);
    }
}
/** @nocollapse */
RenderStore.decorators = [
    { type: Injectable },
];
/** @nocollapse */
RenderStore.ctorParameters = [];
//# sourceMappingURL=render_store.js.map