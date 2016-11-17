/**
 * @license
 * Copyright Google Inc. All Rights Reserved.
 *
 * Use of this source code is governed by an MIT-style license that can be
 * found in the LICENSE file at https://angular.io/license
 */
import { Subject } from 'rxjs/Subject';
import { PromiseObservable } from 'rxjs/observable/PromiseObservable';
import { toPromise } from 'rxjs/operator/toPromise';
import { global, noop } from './lang';
export { Observable } from 'rxjs/Observable';
export { Subject } from 'rxjs/Subject';
export { PromiseCompleter, PromiseWrapper } from './promise';
export class TimerWrapper {
    static setTimeout(fn, millis) {
        return global.setTimeout(fn, millis);
    }
    static clearTimeout(id) { global.clearTimeout(id); }
    static setInterval(fn, millis) {
        return global.setInterval(fn, millis);
    }
    static clearInterval(id) { global.clearInterval(id); }
}
export class ObservableWrapper {
    // TODO(vsavkin): when we use rxnext, try inferring the generic type from the first arg
    static subscribe(emitter, onNext, onError, onComplete = () => { }) {
        onError = (typeof onError === 'function') && onError || noop;
        onComplete = (typeof onComplete === 'function') && onComplete || noop;
        return emitter.subscribe({ next: onNext, error: onError, complete: onComplete });
    }
    static isObservable(obs) { return !!obs.subscribe; }
    /**
     * Returns whether `obs` has any subscribers listening to events.
     */
    static hasSubscribers(obs) { return obs.observers.length > 0; }
    static dispose(subscription) { subscription.unsubscribe(); }
    /**
     * @deprecated - use callEmit() instead
     */
    static callNext(emitter, value) { emitter.emit(value); }
    static callEmit(emitter, value) { emitter.emit(value); }
    static callError(emitter, error) { emitter.error(error); }
    static callComplete(emitter) { emitter.complete(); }
    static fromPromise(promise) {
        return PromiseObservable.create(promise);
    }
    static toPromise(obj) { return toPromise.call(obj); }
}
/**
 * Use by directives and components to emit custom Events.
 *
 * ### Examples
 *
 * In the following example, `Zippy` alternatively emits `open` and `close` events when its
 * title gets clicked:
 *
 * ```
 * @Component({
 *   selector: 'zippy',
 *   template: `
 *   <div class="zippy">
 *     <div (click)="toggle()">Toggle</div>
 *     <div [hidden]="!visible">
 *       <ng-content></ng-content>
 *     </div>
 *  </div>`})
 * export class Zippy {
 *   visible: boolean = true;
 *   @Output() open: EventEmitter<any> = new EventEmitter();
 *   @Output() close: EventEmitter<any> = new EventEmitter();
 *
 *   toggle() {
 *     this.visible = !this.visible;
 *     if (this.visible) {
 *       this.open.emit(null);
 *     } else {
 *       this.close.emit(null);
 *     }
 *   }
 * }
 * ```
 *
 * The events payload can be accessed by the parameter `$event` on the components output event
 * handler:
 *
 * ```
 * <zippy (open)="onOpen($event)" (close)="onClose($event)"></zippy>
 * ```
 *
 * Uses Rx.Observable but provides an adapter to make it work as specified here:
 * https://github.com/jhusain/observable-spec
 *
 * Once a reference implementation of the spec is available, switch to it.
 * @stable
 */
export class EventEmitter extends Subject {
    /**
     * Creates an instance of [EventEmitter], which depending on [isAsync],
     * delivers events synchronously or asynchronously.
     */
    constructor(isAsync = false) {
        super();
        this.__isAsync = isAsync;
    }
    emit(value) { super.next(value); }
    /**
     * @deprecated - use .emit(value) instead
     */
    next(value) { super.next(value); }
    subscribe(generatorOrNext, error, complete) {
        let schedulerFn;
        let errorFn = (err) => null;
        let completeFn = () => null;
        if (generatorOrNext && typeof generatorOrNext === 'object') {
            schedulerFn = this.__isAsync ? (value /** TODO #9100 */) => {
                setTimeout(() => generatorOrNext.next(value));
            } : (value /** TODO #9100 */) => { generatorOrNext.next(value); };
            if (generatorOrNext.error) {
                errorFn = this.__isAsync ? (err) => { setTimeout(() => generatorOrNext.error(err)); } :
                        (err) => { generatorOrNext.error(err); };
            }
            if (generatorOrNext.complete) {
                completeFn = this.__isAsync ? () => { setTimeout(() => generatorOrNext.complete()); } :
                        () => { generatorOrNext.complete(); };
            }
        }
        else {
            schedulerFn = this.__isAsync ? (value /** TODO #9100 */) => {
                setTimeout(() => generatorOrNext(value));
            } : (value /** TODO #9100 */) => { generatorOrNext(value); };
            if (error) {
                errorFn =
                    this.__isAsync ? (err) => { setTimeout(() => error(err)); } : (err) => { error(err); };
            }
            if (complete) {
                completeFn =
                    this.__isAsync ? () => { setTimeout(() => complete()); } : () => { complete(); };
            }
        }
        return super.subscribe(schedulerFn, errorFn, completeFn);
    }
}
//# sourceMappingURL=async.js.map