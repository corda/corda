/**
 * @license
 * Copyright Google Inc. All Rights Reserved.
 *
 * Use of this source code is governed by an MIT-style license that can be
 * found in the LICENSE file at https://angular.io/license
 */
import { Directive, Self } from '@angular/core';
import { isPresent } from '../../facade/lang';
import { NgControl } from './ng_control';
export class NgControlStatus {
    constructor(cd) {
        this._cd = cd;
    }
    get ngClassUntouched() {
        return isPresent(this._cd.control) ? this._cd.control.untouched : false;
    }
    get ngClassTouched() {
        return isPresent(this._cd.control) ? this._cd.control.touched : false;
    }
    get ngClassPristine() {
        return isPresent(this._cd.control) ? this._cd.control.pristine : false;
    }
    get ngClassDirty() {
        return isPresent(this._cd.control) ? this._cd.control.dirty : false;
    }
    get ngClassValid() {
        return isPresent(this._cd.control) ? this._cd.control.valid : false;
    }
    get ngClassInvalid() {
        return isPresent(this._cd.control) ? !this._cd.control.valid : false;
    }
}
/** @nocollapse */
NgControlStatus.decorators = [
    { type: Directive, args: [{
                selector: '[ngControl],[ngModel],[ngFormControl]',
                host: {
                    '[class.ng-untouched]': 'ngClassUntouched',
                    '[class.ng-touched]': 'ngClassTouched',
                    '[class.ng-pristine]': 'ngClassPristine',
                    '[class.ng-dirty]': 'ngClassDirty',
                    '[class.ng-valid]': 'ngClassValid',
                    '[class.ng-invalid]': 'ngClassInvalid'
                }
            },] },
];
/** @nocollapse */
NgControlStatus.ctorParameters = [
    { type: NgControl, decorators: [{ type: Self },] },
];
//# sourceMappingURL=ng_control_status.js.map