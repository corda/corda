/**
 * @license
 * Copyright Google Inc. All Rights Reserved.
 *
 * Use of this source code is governed by an MIT-style license that can be
 * found in the LICENSE file at https://angular.io/license
 */
import { unimplemented } from '../../facade/exceptions';
import { AbstractControlDirective } from './abstract_control_directive';
/**
 * A base class that all control directive extend.
 * It binds a {@link Control} object to a DOM element.
 *
 * Used internally by Angular forms.
 *
 * @experimental
 */
export class NgControl extends AbstractControlDirective {
    constructor(...args) {
        super(...args);
        this.name = null;
        this.valueAccessor = null;
    }
    get validator() { return unimplemented(); }
    get asyncValidator() { return unimplemented(); }
}
//# sourceMappingURL=ng_control.js.map