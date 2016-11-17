/**
 * @license
 * Copyright Google Inc. All Rights Reserved.
 *
 * Use of this source code is governed by an MIT-style license that can be
 * found in the LICENSE file at https://angular.io/license
 */
import { EventEmitter, Injectable } from '@angular/core';
import { ObservableWrapper } from '../src/facade/async';
export class SpyLocation {
    constructor() {
        this.urlChanges = [];
        /** @internal */
        this._history = [new LocationState('', '')];
        /** @internal */
        this._historyIndex = 0;
        /** @internal */
        this._subject = new EventEmitter();
        /** @internal */
        this._baseHref = '';
        /** @internal */
        this._platformStrategy = null;
    }
    setInitialPath(url) { this._history[this._historyIndex].path = url; }
    setBaseHref(url) { this._baseHref = url; }
    path() { return this._history[this._historyIndex].path; }
    isCurrentPathEqualTo(path, query = '') {
        var givenPath = path.endsWith('/') ? path.substring(0, path.length - 1) : path;
        var currPath = this.path().endsWith('/') ? this.path().substring(0, this.path().length - 1) : this.path();
        return currPath == givenPath + (query.length > 0 ? ('?' + query) : '');
    }
    simulateUrlPop(pathname) {
        ObservableWrapper.callEmit(this._subject, { 'url': pathname, 'pop': true });
    }
    simulateHashChange(pathname) {
        // Because we don't prevent the native event, the browser will independently update the path
        this.setInitialPath(pathname);
        this.urlChanges.push('hash: ' + pathname);
        ObservableWrapper.callEmit(this._subject, { 'url': pathname, 'pop': true, 'type': 'hashchange' });
    }
    prepareExternalUrl(url) {
        if (url.length > 0 && !url.startsWith('/')) {
            url = '/' + url;
        }
        return this._baseHref + url;
    }
    go(path, query = '') {
        path = this.prepareExternalUrl(path);
        if (this._historyIndex > 0) {
            this._history.splice(this._historyIndex + 1);
        }
        this._history.push(new LocationState(path, query));
        this._historyIndex = this._history.length - 1;
        var locationState = this._history[this._historyIndex - 1];
        if (locationState.path == path && locationState.query == query) {
            return;
        }
        var url = path + (query.length > 0 ? ('?' + query) : '');
        this.urlChanges.push(url);
    }
    replaceState(path, query = '') {
        path = this.prepareExternalUrl(path);
        var history = this._history[this._historyIndex];
        if (history.path == path && history.query == query) {
            return;
        }
        history.path = path;
        history.query = query;
        var url = path + (query.length > 0 ? ('?' + query) : '');
        this.urlChanges.push('replace: ' + url);
    }
    forward() {
        if (this._historyIndex < (this._history.length - 1)) {
            this._historyIndex++;
            ObservableWrapper.callEmit(this._subject, { 'url': this.path(), 'pop': true });
        }
    }
    back() {
        if (this._historyIndex > 0) {
            this._historyIndex--;
            ObservableWrapper.callEmit(this._subject, { 'url': this.path(), 'pop': true });
        }
    }
    subscribe(onNext, onThrow = null, onReturn = null) {
        return ObservableWrapper.subscribe(this._subject, onNext, onThrow, onReturn);
    }
    normalize(url) { return null; }
}
/** @nocollapse */
SpyLocation.decorators = [
    { type: Injectable },
];
class LocationState {
    constructor(path, query) {
        this.path = path;
        this.query = query;
    }
}
//# sourceMappingURL=location_mock.js.map