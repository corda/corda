/**
 * @license
 * Copyright Google Inc. All Rights Reserved.
 *
 * Use of this source code is governed by an MIT-style license that can be
 * found in the LICENSE file at https://angular.io/license
 */
"use strict";
var exceptions_1 = require('../facade/exceptions');
var lang_1 = require('../facade/lang');
// asset:<package-name>/<realm>/<path-to-module>
var _ASSET_URL_RE = /asset:([^\/]+)\/([^\/]+)\/(.+)/g;
/**
 * Interface that defines how import statements should be generated.
 */
var ImportGenerator = (function () {
    function ImportGenerator() {
    }
    ImportGenerator.parseAssetUrl = function (url) { return AssetUrl.parse(url); };
    return ImportGenerator;
}());
exports.ImportGenerator = ImportGenerator;
var AssetUrl = (function () {
    function AssetUrl(packageName, firstLevelDir, modulePath) {
        this.packageName = packageName;
        this.firstLevelDir = firstLevelDir;
        this.modulePath = modulePath;
    }
    AssetUrl.parse = function (url, allowNonMatching) {
        if (allowNonMatching === void 0) { allowNonMatching = true; }
        var match = lang_1.RegExpWrapper.firstMatch(_ASSET_URL_RE, url);
        if (lang_1.isPresent(match)) {
            return new AssetUrl(match[1], match[2], match[3]);
        }
        if (allowNonMatching) {
            return null;
        }
        throw new exceptions_1.BaseException("Url " + url + " is not a valid asset: url");
    };
    return AssetUrl;
}());
exports.AssetUrl = AssetUrl;
//# sourceMappingURL=path_util.js.map