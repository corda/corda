/**
 * @license
 * Copyright Google Inc. All Rights Reserved.
 *
 * Use of this source code is governed by an MIT-style license that can be
 * found in the LICENSE file at https://angular.io/license
 */
import { Directive, ElementRef, IterableDiffers, KeyValueDiffers, Renderer } from '@angular/core';
import { StringMapWrapper, isListLikeIterable } from '../facade/collection';
import { isArray, isPresent, isString } from '../facade/lang';
export class NgClass {
    constructor(_iterableDiffers, _keyValueDiffers, _ngEl, _renderer) {
        this._iterableDiffers = _iterableDiffers;
        this._keyValueDiffers = _keyValueDiffers;
        this._ngEl = _ngEl;
        this._renderer = _renderer;
        this._initialClasses = [];
    }
    set initialClasses(v) {
        this._applyInitialClasses(true);
        this._initialClasses = isPresent(v) && isString(v) ? v.split(' ') : [];
        this._applyInitialClasses(false);
        this._applyClasses(this._rawClass, false);
    }
    set rawClass(v) {
        this._cleanupClasses(this._rawClass);
        if (isString(v)) {
            v = v.split(' ');
        }
        this._rawClass = v;
        this._iterableDiffer = null;
        this._keyValueDiffer = null;
        if (isPresent(v)) {
            if (isListLikeIterable(v)) {
                this._iterableDiffer = this._iterableDiffers.find(v).create(null);
            }
            else {
                this._keyValueDiffer = this._keyValueDiffers.find(v).create(null);
            }
        }
    }
    ngDoCheck() {
        if (isPresent(this._iterableDiffer)) {
            var changes = this._iterableDiffer.diff(this._rawClass);
            if (isPresent(changes)) {
                this._applyIterableChanges(changes);
            }
        }
        if (isPresent(this._keyValueDiffer)) {
            var changes = this._keyValueDiffer.diff(this._rawClass);
            if (isPresent(changes)) {
                this._applyKeyValueChanges(changes);
            }
        }
    }
    ngOnDestroy() { this._cleanupClasses(this._rawClass); }
    _cleanupClasses(rawClassVal) {
        this._applyClasses(rawClassVal, true);
        this._applyInitialClasses(false);
    }
    _applyKeyValueChanges(changes) {
        changes.forEachAddedItem((record) => { this._toggleClass(record.key, record.currentValue); });
        changes.forEachChangedItem((record) => { this._toggleClass(record.key, record.currentValue); });
        changes.forEachRemovedItem((record) => {
            if (record.previousValue) {
                this._toggleClass(record.key, false);
            }
        });
    }
    _applyIterableChanges(changes) {
        changes.forEachAddedItem((record) => { this._toggleClass(record.item, true); });
        changes.forEachRemovedItem((record) => { this._toggleClass(record.item, false); });
    }
    _applyInitialClasses(isCleanup) {
        this._initialClasses.forEach(className => this._toggleClass(className, !isCleanup));
    }
    _applyClasses(rawClassVal, isCleanup) {
        if (isPresent(rawClassVal)) {
            if (isArray(rawClassVal)) {
                rawClassVal.forEach(className => this._toggleClass(className, !isCleanup));
            }
            else if (rawClassVal instanceof Set) {
                rawClassVal.forEach(className => this._toggleClass(className, !isCleanup));
            }
            else {
                StringMapWrapper.forEach(rawClassVal, (expVal, className) => {
                    if (isPresent(expVal))
                        this._toggleClass(className, !isCleanup);
                });
            }
        }
    }
    _toggleClass(className, enabled) {
        className = className.trim();
        if (className.length > 0) {
            if (className.indexOf(' ') > -1) {
                var classes = className.split(/\s+/g);
                for (var i = 0, len = classes.length; i < len; i++) {
                    this._renderer.setElementClass(this._ngEl.nativeElement, classes[i], enabled);
                }
            }
            else {
                this._renderer.setElementClass(this._ngEl.nativeElement, className, enabled);
            }
        }
    }
}
/** @nocollapse */
NgClass.decorators = [
    { type: Directive, args: [{ selector: '[ngClass]', inputs: ['rawClass: ngClass', 'initialClasses: class'] },] },
];
/** @nocollapse */
NgClass.ctorParameters = [
    { type: IterableDiffers, },
    { type: KeyValueDiffers, },
    { type: ElementRef, },
    { type: Renderer, },
];
//# sourceMappingURL=ng_class.js.map