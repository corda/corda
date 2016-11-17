/**
 * @license
 * Copyright Google Inc. All Rights Reserved.
 *
 * Use of this source code is governed by an MIT-style license that can be
 * found in the LICENSE file at https://angular.io/license
 */
"use strict";
var __extends = (this && this.__extends) || function (d, b) {
    for (var p in b) if (b.hasOwnProperty(p)) d[p] = b[p];
    function __() { this.constructor = d; }
    d.prototype = b === null ? Object.create(b) : (__.prototype = b.prototype, new __());
};
var core_1 = require('@angular/core');
var core_private_1 = require('../../core_private');
exports.SecurityContext = core_private_1.SecurityContext;
var html_sanitizer_1 = require('./html_sanitizer');
var style_sanitizer_1 = require('./style_sanitizer');
var url_sanitizer_1 = require('./url_sanitizer');
/**
 * DomSanitizationService helps preventing Cross Site Scripting Security bugs (XSS) by sanitizing
 * values to be safe to use in the different DOM contexts.
 *
 * For example, when binding a URL in an `<a [href]="someValue">` hyperlink, `someValue` will be
 * sanitized so that an attacker cannot inject e.g. a `javascript:` URL that would execute code on
 * the website.
 *
 * In specific situations, it might be necessary to disable sanitization, for example if the
 * application genuinely needs to produce a `javascript:` style link with a dynamic value in it.
 * Users can bypass security by constructing a value with one of the `bypassSecurityTrust...`
 * methods, and then binding to that value from the template.
 *
 * These situations should be very rare, and extraordinary care must be taken to avoid creating a
 * Cross Site Scripting (XSS) security bug!
 *
 * When using `bypassSecurityTrust...`, make sure to call the method as early as possible and as
 * close as possible to the source of the value, to make it easy to verify no security bug is
 * created by its use.
 *
 * It is not required (and not recommended) to bypass security if the value is safe, e.g. a URL that
 * does not start with a suspicious protocol, or an HTML snippet that does not contain dangerous
 * code. The sanitizer leaves safe values intact.
 *
 * @security Calling any of the `bypassSecurityTrust...` APIs disables Angular's built-in
 * sanitization for the value passed in. Carefully check and audit all values and code paths going
 * into this call. Make sure any user data is appropriately escaped for this security context.
 * For more detail, see the [Security Guide](http://g.co/ng/security).
 *
 * @stable
 */
var DomSanitizationService = (function () {
    function DomSanitizationService() {
    }
    return DomSanitizationService;
}());
exports.DomSanitizationService = DomSanitizationService;
var DomSanitizationServiceImpl = (function (_super) {
    __extends(DomSanitizationServiceImpl, _super);
    function DomSanitizationServiceImpl() {
        _super.apply(this, arguments);
    }
    DomSanitizationServiceImpl.prototype.sanitize = function (ctx, value) {
        if (value == null)
            return null;
        switch (ctx) {
            case core_private_1.SecurityContext.NONE:
                return value;
            case core_private_1.SecurityContext.HTML:
                if (value instanceof SafeHtmlImpl)
                    return value.changingThisBreaksApplicationSecurity;
                this.checkNotSafeValue(value, 'HTML');
                return html_sanitizer_1.sanitizeHtml(String(value));
            case core_private_1.SecurityContext.STYLE:
                if (value instanceof SafeStyleImpl)
                    return value.changingThisBreaksApplicationSecurity;
                this.checkNotSafeValue(value, 'Style');
                return style_sanitizer_1.sanitizeStyle(value);
            case core_private_1.SecurityContext.SCRIPT:
                if (value instanceof SafeScriptImpl)
                    return value.changingThisBreaksApplicationSecurity;
                this.checkNotSafeValue(value, 'Script');
                throw new Error('unsafe value used in a script context');
            case core_private_1.SecurityContext.URL:
                if (value instanceof SafeUrlImpl)
                    return value.changingThisBreaksApplicationSecurity;
                this.checkNotSafeValue(value, 'URL');
                return url_sanitizer_1.sanitizeUrl(String(value));
            case core_private_1.SecurityContext.RESOURCE_URL:
                if (value instanceof SafeResourceUrlImpl) {
                    return value.changingThisBreaksApplicationSecurity;
                }
                this.checkNotSafeValue(value, 'ResourceURL');
                throw new Error('unsafe value used in a resource URL context (see http://g.co/ng/security#xss)');
            default:
                throw new Error("Unexpected SecurityContext " + ctx + " (see http://g.co/ng/security#xss)");
        }
    };
    DomSanitizationServiceImpl.prototype.checkNotSafeValue = function (value, expectedType) {
        if (value instanceof SafeValueImpl) {
            throw new Error(("Required a safe " + expectedType + ", got a " + value.getTypeName() + " ") +
                "(see http://g.co/ng/security#xss)");
        }
    };
    DomSanitizationServiceImpl.prototype.bypassSecurityTrustHtml = function (value) { return new SafeHtmlImpl(value); };
    DomSanitizationServiceImpl.prototype.bypassSecurityTrustStyle = function (value) { return new SafeStyleImpl(value); };
    DomSanitizationServiceImpl.prototype.bypassSecurityTrustScript = function (value) { return new SafeScriptImpl(value); };
    DomSanitizationServiceImpl.prototype.bypassSecurityTrustUrl = function (value) { return new SafeUrlImpl(value); };
    DomSanitizationServiceImpl.prototype.bypassSecurityTrustResourceUrl = function (value) {
        return new SafeResourceUrlImpl(value);
    };
    /** @nocollapse */
    DomSanitizationServiceImpl.decorators = [
        { type: core_1.Injectable },
    ];
    return DomSanitizationServiceImpl;
}(DomSanitizationService));
exports.DomSanitizationServiceImpl = DomSanitizationServiceImpl;
var SafeValueImpl = (function () {
    function SafeValueImpl(changingThisBreaksApplicationSecurity) {
        this.changingThisBreaksApplicationSecurity = changingThisBreaksApplicationSecurity;
        // empty
    }
    SafeValueImpl.prototype.toString = function () {
        return ("SafeValue must use [property]=binding: " + this.changingThisBreaksApplicationSecurity) +
            " (see http://g.co/ng/security#xss)";
    };
    return SafeValueImpl;
}());
var SafeHtmlImpl = (function (_super) {
    __extends(SafeHtmlImpl, _super);
    function SafeHtmlImpl() {
        _super.apply(this, arguments);
    }
    SafeHtmlImpl.prototype.getTypeName = function () { return 'HTML'; };
    return SafeHtmlImpl;
}(SafeValueImpl));
var SafeStyleImpl = (function (_super) {
    __extends(SafeStyleImpl, _super);
    function SafeStyleImpl() {
        _super.apply(this, arguments);
    }
    SafeStyleImpl.prototype.getTypeName = function () { return 'Style'; };
    return SafeStyleImpl;
}(SafeValueImpl));
var SafeScriptImpl = (function (_super) {
    __extends(SafeScriptImpl, _super);
    function SafeScriptImpl() {
        _super.apply(this, arguments);
    }
    SafeScriptImpl.prototype.getTypeName = function () { return 'Script'; };
    return SafeScriptImpl;
}(SafeValueImpl));
var SafeUrlImpl = (function (_super) {
    __extends(SafeUrlImpl, _super);
    function SafeUrlImpl() {
        _super.apply(this, arguments);
    }
    SafeUrlImpl.prototype.getTypeName = function () { return 'URL'; };
    return SafeUrlImpl;
}(SafeValueImpl));
var SafeResourceUrlImpl = (function (_super) {
    __extends(SafeResourceUrlImpl, _super);
    function SafeResourceUrlImpl() {
        _super.apply(this, arguments);
    }
    SafeResourceUrlImpl.prototype.getTypeName = function () { return 'ResourceURL'; };
    return SafeResourceUrlImpl;
}(SafeValueImpl));
//# sourceMappingURL=dom_sanitization_service.js.map