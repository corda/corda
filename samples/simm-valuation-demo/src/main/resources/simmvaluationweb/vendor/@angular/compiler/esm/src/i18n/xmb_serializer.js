/**
 * @license
 * Copyright Google Inc. All Rights Reserved.
 *
 * Use of this source code is governed by an MIT-style license that can be
 * found in the LICENSE file at https://angular.io/license
 */
import { RegExpWrapper, isBlank, isPresent } from '../facade/lang';
import { HtmlElementAst } from '../html_ast';
import { HtmlParser } from '../html_parser';
import { ParseError } from '../parse_util';
import { id } from './message';
let _PLACEHOLDER_REGEXP = RegExpWrapper.create(`\\<ph(\\s)+name=("(\\w)+")\\/\\>`);
const _ID_ATTR = 'id';
const _MSG_ELEMENT = 'msg';
const _BUNDLE_ELEMENT = 'message-bundle';
export function serializeXmb(messages) {
    let ms = messages.map((m) => _serializeMessage(m)).join('');
    return `<message-bundle>${ms}</message-bundle>`;
}
export class XmbDeserializationResult {
    constructor(content, messages, errors) {
        this.content = content;
        this.messages = messages;
        this.errors = errors;
    }
}
export class XmbDeserializationError extends ParseError {
    constructor(span, msg) {
        super(span, msg);
    }
}
export function deserializeXmb(content, url) {
    let parser = new HtmlParser();
    let normalizedContent = _expandPlaceholder(content.trim());
    let parsed = parser.parse(normalizedContent, url);
    if (parsed.errors.length > 0) {
        return new XmbDeserializationResult(null, {}, parsed.errors);
    }
    if (_checkRootElement(parsed.rootNodes)) {
        return new XmbDeserializationResult(null, {}, [new XmbDeserializationError(null, `Missing element "${_BUNDLE_ELEMENT}"`)]);
    }
    let bundleEl = parsed.rootNodes[0]; // test this
    let errors = [];
    let messages = {};
    _createMessages(bundleEl.children, messages, errors);
    return (errors.length == 0) ?
        new XmbDeserializationResult(normalizedContent, messages, []) :
        new XmbDeserializationResult(null, {}, errors);
}
function _checkRootElement(nodes) {
    return nodes.length < 1 || !(nodes[0] instanceof HtmlElementAst) ||
        nodes[0].name != _BUNDLE_ELEMENT;
}
function _createMessages(nodes, messages, errors) {
    nodes.forEach((item) => {
        if (item instanceof HtmlElementAst) {
            let msg = item;
            if (msg.name != _MSG_ELEMENT) {
                errors.push(new XmbDeserializationError(item.sourceSpan, `Unexpected element "${msg.name}"`));
                return;
            }
            let id = _id(msg);
            if (isBlank(id)) {
                errors.push(new XmbDeserializationError(item.sourceSpan, `"${_ID_ATTR}" attribute is missing`));
                return;
            }
            messages[id] = msg.children;
        }
    });
}
function _id(el) {
    let ids = el.attrs.filter(a => a.name == _ID_ATTR);
    return ids.length > 0 ? ids[0].value : null;
}
function _serializeMessage(m) {
    const desc = isPresent(m.description) ? ` desc='${_escapeXml(m.description)}'` : '';
    const meaning = isPresent(m.meaning) ? ` meaning='${_escapeXml(m.meaning)}'` : '';
    return `<msg id='${id(m)}'${desc}${meaning}>${m.content}</msg>`;
}
function _expandPlaceholder(input) {
    return RegExpWrapper.replaceAll(_PLACEHOLDER_REGEXP, input, (match) => {
        let nameWithQuotes = match[2];
        return `<ph name=${nameWithQuotes}></ph>`;
    });
}
const _XML_ESCAPED_CHARS = [
    [/&/g, '&amp;'],
    [/"/g, '&quot;'],
    [/'/g, '&apos;'],
    [/</g, '&lt;'],
    [/>/g, '&gt;'],
];
function _escapeXml(value) {
    return _XML_ESCAPED_CHARS.reduce((value, escape) => value.replace(escape[0], escape[1]), value);
}
//# sourceMappingURL=xmb_serializer.js.map