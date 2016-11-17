/**
 * @license
 * Copyright Google Inc. All Rights Reserved.
 *
 * Use of this source code is governed by an MIT-style license that can be
 * found in the LICENSE file at https://angular.io/license
 */
"use strict";
/**
 * A SecurityContext marks a location that has dangerous security implications, e.g. a DOM property
 * like `innerHTML` that could cause Cross Site Scripting (XSS) security bugs when improperly
 * handled.
 *
 * See DomSanitizationService for more details on security in Angular applications.
 *
 * @stable
 */
(function (SecurityContext) {
    SecurityContext[SecurityContext["NONE"] = 0] = "NONE";
    SecurityContext[SecurityContext["HTML"] = 1] = "HTML";
    SecurityContext[SecurityContext["STYLE"] = 2] = "STYLE";
    SecurityContext[SecurityContext["SCRIPT"] = 3] = "SCRIPT";
    SecurityContext[SecurityContext["URL"] = 4] = "URL";
    SecurityContext[SecurityContext["RESOURCE_URL"] = 5] = "RESOURCE_URL";
})(exports.SecurityContext || (exports.SecurityContext = {}));
var SecurityContext = exports.SecurityContext;
/**
 * SanitizationService is used by the views to sanitize potentially dangerous values. This is a
 * private API, use code should only refer to DomSanitizationService.
 *
 * @stable
 */
var SanitizationService = (function () {
    function SanitizationService() {
    }
    return SanitizationService;
}());
exports.SanitizationService = SanitizationService;
//# sourceMappingURL=security.js.map