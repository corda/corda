/**
 * @license
 * Copyright Google Inc. All Rights Reserved.
 *
 * Use of this source code is governed by an MIT-style license that can be
 * found in the LICENSE file at https://angular.io/license
 */
"use strict";
var lang_1 = require('../facade/lang');
/**
 * A message extracted from a template.
 *
 * The identity of a message is comprised of `content` and `meaning`.
 *
 * `description` is additional information provided to the translator.
 */
var Message = (function () {
    function Message(content, meaning, description) {
        if (description === void 0) { description = null; }
        this.content = content;
        this.meaning = meaning;
        this.description = description;
    }
    return Message;
}());
exports.Message = Message;
/**
 * Computes the id of a message
 */
function id(m) {
    var meaning = lang_1.isPresent(m.meaning) ? m.meaning : '';
    var content = lang_1.isPresent(m.content) ? m.content : '';
    return lang_1.escape("$ng|" + meaning + "|" + content);
}
exports.id = id;
//# sourceMappingURL=message.js.map