/**
 * @license
 * Copyright Google Inc. All Rights Reserved.
 *
 * Use of this source code is governed by an MIT-style license that can be
 * found in the LICENSE file at https://angular.io/license
 */
import { SecurityContext } from '../core_private';
import { isPresent } from '../src/facade/lang';
export class MockSchemaRegistry {
    constructor(existingProperties, attrPropMapping) {
        this.existingProperties = existingProperties;
        this.attrPropMapping = attrPropMapping;
    }
    hasProperty(tagName, property) {
        var result = this.existingProperties[property];
        return isPresent(result) ? result : true;
    }
    securityContext(tagName, property) {
        return SecurityContext.NONE;
    }
    getMappedPropName(attrName) {
        var result = this.attrPropMapping[attrName];
        return isPresent(result) ? result : attrName;
    }
}
//# sourceMappingURL=schema_registry_mock.js.map