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
var lang_1 = require('../facade/lang');
var html_ast_1 = require('../html_ast');
var html_parser_1 = require('../html_parser');
var parse_util_1 = require('../parse_util');
var message_1 = require('./message');
var _PLACEHOLDER_REGEXP = lang_1.RegExpWrapper.create("\\<ph(\\s)+name=(\"(\\w)+\")\\/\\>");
var _ID_ATTR = 'id';
var _MSG_ELEMENT = 'msg';
var _BUNDLE_ELEMENT = 'message-bundle';
function serializeXmb(messages) {
    var ms = messages.map(function (m) { return _serializeMessage(m); }).join('');
    return "<message-bundle>" + ms + "</message-bundle>";
}
exports.serializeXmb = serializeXmb;
var XmbDeserializationResult = (function () {
    function XmbDeserializationResult(content, messages, errors) {
        this.content = content;
        this.messages = messages;
        this.errors = errors;
    }
    return XmbDeserializationResult;
}());
exports.XmbDeserializationResult = XmbDeserializationResult;
var XmbDeserializationError = (function (_super) {
    __extends(XmbDeserializationError, _super);
    function XmbDeserializationError(span, msg) {
        _super.call(this, span, msg);
    }
    return XmbDeserializationError;
}(parse_util_1.ParseError));
exports.XmbDeserializationError = XmbDeserializationError;
function deserializeXmb(content, url) {
    var parser = new html_parser_1.HtmlParser();
    var normalizedContent = _expandPlaceholder(content.trim());
    var parsed = parser.parse(normalizedContent, url);
    if (parsed.errors.length > 0) {
        return new XmbDeserializationResult(null, {}, parsed.errors);
    }
    if (_checkRootElement(parsed.rootNodes)) {
        return new XmbDeserializationResult(null, {}, [new XmbDeserializationError(null, "Missing element \"" + _BUNDLE_ELEMENT + "\"")]);
    }
    var bundleEl = parsed.rootNodes[0]; // test this
    var errors = [];
    var messages = {};
    _createMessages(bundleEl.children, messages, errors);
    return (errors.length == 0) ?
        new XmbDeserializationResult(normalizedContent, messages, []) :
        new XmbDeserializationResult(null, {}, errors);
}
exports.deserializeXmb = deserializeXmb;
function _checkRootElement(nodes) {
    return nodes.length < 1 || !(nodes[0] instanceof html_ast_1.HtmlElementAst) ||
        nodes[0].name != _BUNDLE_ELEMENT;
}
function _createMessages(nodes, messages, errors) {
    nodes.forEach(function (item) {
        if (item instanceof html_ast_1.HtmlElementAst) {
            var msg = item;
            if (msg.name != _MSG_ELEMENT) {
                errors.push(new XmbDeserializationError(item.sourceSpan, "Unexpected element \"" + msg.name + "\""));
                return;
            }
            var id_1 = _id(msg);
            if (lang_1.isBlank(id_1)) {
                errors.push(new XmbDeserializationError(item.sourceSpan, "\"" + _ID_ATTR + "\" attribute is missing"));
                return;
            }
            messages[id_1] = msg.children;
        }
    });
}
function _id(el) {
    var ids = el.attrs.filter(function (a) { return a.name == _ID_ATTR; });
    return ids.length > 0 ? ids[0].value : null;
}
function _serializeMessage(m) {
    var desc = lang_1.isPresent(m.description) ? " desc='" + _escapeXml(m.description) + "'" : '';
    var meaning = lang_1.isPresent(m.meaning) ? " meaning='" + _escapeXml(m.meaning) + "'" : '';
    return "<msg id='" + message_1.id(m) + "'" + desc + meaning + ">" + m.content + "</msg>";
}
function _expandPlaceholder(input) {
    return lang_1.RegExpWrapper.replaceAll(_PLACEHOLDER_REGEXP, input, function (match) {
        var nameWithQuotes = match[2];
        return "<ph name=" + nameWithQuotes + "></ph>";
    });
}
var _XML_ESCAPED_CHARS = [
    [/&/g, '&amp;'],
    [/"/g, '&quot;'],
    [/'/g, '&apos;'],
    [/</g, '&lt;'],
    [/>/g, '&gt;'],
];
function _escapeXml(value) {
    return _XML_ESCAPED_CHARS.reduce(function (value, escape) { return value.replace(escape[0], escape[1]); }, value);
}
//# sourceMappingURL=xmb_serializer.js.map