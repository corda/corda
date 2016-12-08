/**
 * @license
 * Copyright Google Inc. All Rights Reserved.
 *
 * Use of this source code is governed by an MIT-style license that can be
 * found in the LICENSE file at https://angular.io/license
 */
import { ChangeDetectorStatus } from '../change_detection/constants';
import { unimplemented } from '../facade/exceptions';
/**
 * @stable
 */
export class ViewRef {
    get destroyed() { return unimplemented(); }
}
/**
 * Represents an Angular View.
 *
 * <!-- TODO: move the next two paragraphs to the dev guide -->
 * A View is a fundamental building block of the application UI. It is the smallest grouping of
 * Elements which are created and destroyed together.
 *
 * Properties of elements in a View can change, but the structure (number and order) of elements in
 * a View cannot. Changing the structure of Elements can only be done by inserting, moving or
 * removing nested Views via a {@link ViewContainerRef}. Each View can contain many View Containers.
 * <!-- /TODO -->
 *
 * ### Example
 *
 * Given this template...
 *
 * ```
 * Count: {{items.length}}
 * <ul>
 *   <li *ngFor="let  item of items">{{item}}</li>
 * </ul>
 * ```
 *
 * We have two {@link TemplateRef}s:
 *
 * Outer {@link TemplateRef}:
 * ```
 * Count: {{items.length}}
 * <ul>
 *   <template ngFor let-item [ngForOf]="items"></template>
 * </ul>
 * ```
 *
 * Inner {@link TemplateRef}:
 * ```
 *   <li>{{item}}</li>
 * ```
 *
 * Notice that the original template is broken down into two separate {@link TemplateRef}s.
 *
 * The outer/inner {@link TemplateRef}s are then assembled into views like so:
 *
 * ```
 * <!-- ViewRef: outer-0 -->
 * Count: 2
 * <ul>
 *   <template view-container-ref></template>
 *   <!-- ViewRef: inner-1 --><li>first</li><!-- /ViewRef: inner-1 -->
 *   <!-- ViewRef: inner-2 --><li>second</li><!-- /ViewRef: inner-2 -->
 * </ul>
 * <!-- /ViewRef: outer-0 -->
 * ```
 * @experimental
 */
export class EmbeddedViewRef extends ViewRef {
    get context() { return unimplemented(); }
    get rootNodes() { return unimplemented(); }
    ;
}
export class ViewRef_ {
    constructor(_view) {
        this._view = _view;
        this._view = _view;
        this._originalMode = this._view.cdMode;
    }
    get internalView() { return this._view; }
    get rootNodes() { return this._view.flatRootNodes; }
    get context() { return this._view.context; }
    get destroyed() { return this._view.destroyed; }
    markForCheck() { this._view.markPathToRootAsCheckOnce(); }
    detach() { this._view.cdMode = ChangeDetectorStatus.Detached; }
    detectChanges() { this._view.detectChanges(false); }
    checkNoChanges() { this._view.detectChanges(true); }
    reattach() {
        this._view.cdMode = this._originalMode;
        this.markForCheck();
    }
    onDestroy(callback) { this._view.disposables.push(callback); }
    destroy() { this._view.destroy(); }
}
//# sourceMappingURL=view_ref.js.map