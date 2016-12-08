/**
 * @license
 * Copyright Google Inc. All Rights Reserved.
 *
 * Use of this source code is governed by an MIT-style license that can be
 * found in the LICENSE file at https://angular.io/license
 */
import { ListWrapper } from '../facade/collection';
import { isPresent } from '../facade/lang';
export class StylesCollectionEntry {
    constructor(time, value) {
        this.time = time;
        this.value = value;
    }
    matches(time, value) {
        return time == this.time && value == this.value;
    }
}
export class StylesCollection {
    constructor() {
        this.styles = {};
    }
    insertAtTime(property, time, value) {
        var tuple = new StylesCollectionEntry(time, value);
        var entries = this.styles[property];
        if (!isPresent(entries)) {
            entries = this.styles[property] = [];
        }
        // insert this at the right stop in the array
        // this way we can keep it sorted
        var insertionIndex = 0;
        for (var i = entries.length - 1; i >= 0; i--) {
            if (entries[i].time <= time) {
                insertionIndex = i + 1;
                break;
            }
        }
        ListWrapper.insert(entries, insertionIndex, tuple);
    }
    getByIndex(property, index) {
        var items = this.styles[property];
        if (isPresent(items)) {
            return index >= items.length ? null : items[index];
        }
        return null;
    }
    indexOfAtOrBeforeTime(property, time) {
        var entries = this.styles[property];
        if (isPresent(entries)) {
            for (var i = entries.length - 1; i >= 0; i--) {
                if (entries[i].time <= time)
                    return i;
            }
        }
        return null;
    }
}
//# sourceMappingURL=styles_collection.js.map