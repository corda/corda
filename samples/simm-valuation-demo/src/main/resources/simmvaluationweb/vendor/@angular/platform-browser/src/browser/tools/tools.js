/**
 * @license
 * Copyright Google Inc. All Rights Reserved.
 *
 * Use of this source code is governed by an MIT-style license that can be
 * found in the LICENSE file at https://angular.io/license
 */
"use strict";
var lang_1 = require('../../facade/lang');
var common_tools_1 = require('./common_tools');
var context = lang_1.global;
/**
 * Enabled Angular 2 debug tools that are accessible via your browser's
 * developer console.
 *
 * Usage:
 *
 * 1. Open developer console (e.g. in Chrome Ctrl + Shift + j)
 * 1. Type `ng.` (usually the console will show auto-complete suggestion)
 * 1. Try the change detection profiler `ng.profiler.timeChangeDetection()`
 *    then hit Enter.
 *
 * @experimental All debugging apis are currently experimental.
 */
function enableDebugTools(ref) {
    context.ng = new common_tools_1.AngularTools(ref);
    return ref;
}
exports.enableDebugTools = enableDebugTools;
/**
 * Disables Angular 2 tools.
 *
 * @experimental All debugging apis are currently experimental.
 */
function disableDebugTools() {
    delete context.ng;
}
exports.disableDebugTools = disableDebugTools;
//# sourceMappingURL=tools.js.map