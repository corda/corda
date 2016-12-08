/**
 * @license
 * Copyright Google Inc. All Rights Reserved.
 *
 * Use of this source code is governed by an MIT-style license that can be
 * found in the LICENSE file at https://angular.io/license
 */
import { ChangeDetectorRef, Directive, IterableDiffers, TemplateRef, ViewContainerRef } from '@angular/core';
import { BaseException } from '../facade/exceptions';
import { getTypeNameForDebugging, isBlank, isPresent } from '../facade/lang';
export class NgForRow {
    constructor($implicit, index, count) {
        this.$implicit = $implicit;
        this.index = index;
        this.count = count;
    }
    get first() { return this.index === 0; }
    get last() { return this.index === this.count - 1; }
    get even() { return this.index % 2 === 0; }
    get odd() { return !this.even; }
}
export class NgFor {
    constructor(_viewContainer, _templateRef, _iterableDiffers, _cdr) {
        this._viewContainer = _viewContainer;
        this._templateRef = _templateRef;
        this._iterableDiffers = _iterableDiffers;
        this._cdr = _cdr;
    }
    set ngForOf(value) {
        this._ngForOf = value;
        if (isBlank(this._differ) && isPresent(value)) {
            try {
                this._differ = this._iterableDiffers.find(value).create(this._cdr, this._ngForTrackBy);
            }
            catch (e) {
                throw new BaseException(`Cannot find a differ supporting object '${value}' of type '${getTypeNameForDebugging(value)}'. NgFor only supports binding to Iterables such as Arrays.`);
            }
        }
    }
    set ngForTemplate(value) {
        if (isPresent(value)) {
            this._templateRef = value;
        }
    }
    set ngForTrackBy(value) { this._ngForTrackBy = value; }
    ngDoCheck() {
        if (isPresent(this._differ)) {
            var changes = this._differ.diff(this._ngForOf);
            if (isPresent(changes))
                this._applyChanges(changes);
        }
    }
    _applyChanges(changes) {
        // TODO(rado): check if change detection can produce a change record that is
        // easier to consume than current.
        var recordViewTuples = [];
        changes.forEachRemovedItem((removedRecord) => recordViewTuples.push(new RecordViewTuple(removedRecord, null)));
        changes.forEachMovedItem((movedRecord) => recordViewTuples.push(new RecordViewTuple(movedRecord, null)));
        var insertTuples = this._bulkRemove(recordViewTuples);
        changes.forEachAddedItem((addedRecord) => insertTuples.push(new RecordViewTuple(addedRecord, null)));
        this._bulkInsert(insertTuples);
        for (var i = 0; i < insertTuples.length; i++) {
            this._perViewChange(insertTuples[i].view, insertTuples[i].record);
        }
        for (var i = 0, ilen = this._viewContainer.length; i < ilen; i++) {
            var viewRef = this._viewContainer.get(i);
            viewRef.context.index = i;
            viewRef.context.count = ilen;
        }
        changes.forEachIdentityChange((record /** TODO #9100 */) => {
            var viewRef = this._viewContainer.get(record.currentIndex);
            viewRef.context.$implicit = record.item;
        });
    }
    _perViewChange(view, record) {
        view.context.$implicit = record.item;
    }
    _bulkRemove(tuples) {
        tuples.sort((a, b) => a.record.previousIndex - b.record.previousIndex);
        var movedTuples = [];
        for (var i = tuples.length - 1; i >= 0; i--) {
            var tuple = tuples[i];
            // separate moved views from removed views.
            if (isPresent(tuple.record.currentIndex)) {
                tuple.view =
                    this._viewContainer.detach(tuple.record.previousIndex);
                movedTuples.push(tuple);
            }
            else {
                this._viewContainer.remove(tuple.record.previousIndex);
            }
        }
        return movedTuples;
    }
    _bulkInsert(tuples) {
        tuples.sort((a, b) => a.record.currentIndex - b.record.currentIndex);
        for (var i = 0; i < tuples.length; i++) {
            var tuple = tuples[i];
            if (isPresent(tuple.view)) {
                this._viewContainer.insert(tuple.view, tuple.record.currentIndex);
            }
            else {
                tuple.view = this._viewContainer.createEmbeddedView(this._templateRef, new NgForRow(null, null, null), tuple.record.currentIndex);
            }
        }
        return tuples;
    }
}
/** @nocollapse */
NgFor.decorators = [
    { type: Directive, args: [{ selector: '[ngFor][ngForOf]', inputs: ['ngForTrackBy', 'ngForOf', 'ngForTemplate'] },] },
];
/** @nocollapse */
NgFor.ctorParameters = [
    { type: ViewContainerRef, },
    { type: TemplateRef, },
    { type: IterableDiffers, },
    { type: ChangeDetectorRef, },
];
class RecordViewTuple {
    constructor(record, view) {
        this.record = record;
        this.view = view;
    }
}
//# sourceMappingURL=ng_for.js.map