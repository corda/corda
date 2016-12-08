/**
 * @license
 * Copyright Google Inc. All Rights Reserved.
 *
 * Use of this source code is governed by an MIT-style license that can be
 * found in the LICENSE file at https://angular.io/license
 */
import { StringMapWrapper } from './facade/collection';
import { IS_DART, StringWrapper, isArray, isBlank, isPrimitive, isStrictStringMap } from './facade/lang';
export var MODULE_SUFFIX = IS_DART ? '.dart' : '';
var CAMEL_CASE_REGEXP = /([A-Z])/g;
export function camelCaseToDashCase(input) {
    return StringWrapper.replaceAllMapped(input, CAMEL_CASE_REGEXP, (m) => { return '-' + m[1].toLowerCase(); });
}
export function splitAtColon(input, defaultValues) {
    var parts = StringWrapper.split(input.trim(), /\s*:\s*/g);
    if (parts.length > 1) {
        return parts;
    }
    else {
        return defaultValues;
    }
}
export function sanitizeIdentifier(name) {
    return StringWrapper.replaceAll(name, /\W/g, '_');
}
export function visitValue(value, visitor, context) {
    if (isArray(value)) {
        return visitor.visitArray(value, context);
    }
    else if (isStrictStringMap(value)) {
        return visitor.visitStringMap(value, context);
    }
    else if (isBlank(value) || isPrimitive(value)) {
        return visitor.visitPrimitive(value, context);
    }
    else {
        return visitor.visitOther(value, context);
    }
}
export class ValueTransformer {
    visitArray(arr, context) {
        return arr.map(value => visitValue(value, this, context));
    }
    visitStringMap(map, context) {
        var result = {};
        StringMapWrapper.forEach(map, (value /** TODO #9100 */, key /** TODO #9100 */) => {
            result[key] = visitValue(value, this, context);
        });
        return result;
    }
    visitPrimitive(value, context) { return value; }
    visitOther(value, context) { return value; }
}
export function assetUrl(pkg, path = null, type = 'src') {
    if (IS_DART) {
        if (path == null) {
            return `asset:angular2/${pkg}/${pkg}.dart`;
        }
        else {
            return `asset:angular2/lib/${pkg}/src/${path}.dart`;
        }
    }
    else {
        if (path == null) {
            return `asset:@angular/lib/${pkg}/index`;
        }
        else {
            return `asset:@angular/lib/${pkg}/src/${path}`;
        }
    }
}
//# sourceMappingURL=util.js.map