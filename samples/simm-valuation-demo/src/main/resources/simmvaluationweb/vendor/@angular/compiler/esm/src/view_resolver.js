/**
 * @license
 * Copyright Google Inc. All Rights Reserved.
 *
 * Use of this source code is governed by an MIT-style license that can be
 * found in the LICENSE file at https://angular.io/license
 */
import { Injectable, ViewMetadata, ComponentMetadata } from '@angular/core';
import { ReflectorReader, reflector } from '../core_private';
import { stringify, isBlank, isPresent } from '../src/facade/lang';
import { BaseException } from '../src/facade/exceptions';
export class ViewResolver {
    constructor(_reflector = reflector) {
        this._reflector = _reflector;
    }
    resolve(component) {
        var compMeta;
        this._reflector.annotations(component).forEach(m => {
            if (m instanceof ComponentMetadata) {
                compMeta = m;
            }
        });
        if (isPresent(compMeta)) {
            if (isBlank(compMeta.template) && isBlank(compMeta.templateUrl)) {
                throw new BaseException(`Component '${stringify(component)}' must have either 'template' or 'templateUrl' set.`);
            }
            else {
                return new ViewMetadata({
                    templateUrl: compMeta.templateUrl,
                    template: compMeta.template,
                    directives: compMeta.directives,
                    pipes: compMeta.pipes,
                    encapsulation: compMeta.encapsulation,
                    styles: compMeta.styles,
                    styleUrls: compMeta.styleUrls,
                    animations: compMeta.animations,
                    interpolation: compMeta.interpolation
                });
            }
        }
        else {
            throw new BaseException(`Could not compile '${stringify(component)}' because it is not a component.`);
        }
    }
}
/** @nocollapse */
ViewResolver.decorators = [
    { type: Injectable },
];
/** @nocollapse */
ViewResolver.ctorParameters = [
    { type: ReflectorReader, },
];
//# sourceMappingURL=view_resolver.js.map