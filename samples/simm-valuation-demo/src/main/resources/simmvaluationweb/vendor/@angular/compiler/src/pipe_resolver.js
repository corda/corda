/**
 * @license
 * Copyright Google Inc. All Rights Reserved.
 *
 * Use of this source code is governed by an MIT-style license that can be
 * found in the LICENSE file at https://angular.io/license
 */
"use strict";
var core_1 = require('@angular/core');
var core_private_1 = require('../core_private');
var exceptions_1 = require('../src/facade/exceptions');
var lang_1 = require('../src/facade/lang');
function _isPipeMetadata(type) {
    return type instanceof core_1.PipeMetadata;
}
var PipeResolver = (function () {
    function PipeResolver(_reflector) {
        if (_reflector === void 0) { _reflector = core_private_1.reflector; }
        this._reflector = _reflector;
    }
    /**
     * Return {@link PipeMetadata} for a given `Type`.
     */
    PipeResolver.prototype.resolve = function (type) {
        var metas = this._reflector.annotations(core_1.resolveForwardRef(type));
        if (lang_1.isPresent(metas)) {
            var annotation = metas.find(_isPipeMetadata);
            if (lang_1.isPresent(annotation)) {
                return annotation;
            }
        }
        throw new exceptions_1.BaseException("No Pipe decorator found on " + lang_1.stringify(type));
    };
    /** @nocollapse */
    PipeResolver.decorators = [
        { type: core_1.Injectable },
    ];
    /** @nocollapse */
    PipeResolver.ctorParameters = [
        { type: core_private_1.ReflectorReader, },
    ];
    return PipeResolver;
}());
exports.PipeResolver = PipeResolver;
//# sourceMappingURL=pipe_resolver.js.map