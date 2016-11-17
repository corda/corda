/**
 * @license
 * Copyright Google Inc. All Rights Reserved.
 *
 * Use of this source code is governed by an MIT-style license that can be
 * found in the LICENSE file at https://angular.io/license
 */
import { ListWrapper, StringMapWrapper } from '../facade/collection';
import { BaseException } from '../facade/exceptions';
import { NumberWrapper, RegExpWrapper, isPresent } from '../facade/lang';
import { HtmlAttrAst, HtmlElementAst, HtmlTextAst, htmlVisitAll } from '../html_ast';
import { HtmlParseTreeResult } from '../html_parser';
import { DEFAULT_INTERPOLATION_CONFIG } from '../interpolation_config';
import { expandNodes } from './expander';
import { id } from './message';
import { I18N_ATTR, I18N_ATTR_PREFIX, I18nError, dedupePhName, getPhNameFromBinding, messageFromAttribute, messageFromI18nAttribute, partition } from './shared';
const _PLACEHOLDER_ELEMENT = 'ph';
const _NAME_ATTR = 'name';
let _PLACEHOLDER_EXPANDED_REGEXP = /<ph(\s)+name=("(\w)+")><\/ph>/gi;
/**
 * Creates an i18n-ed version of the parsed template.
 *
 * Algorithm:
 *
 * See `message_extractor.ts` for details on the partitioning algorithm.
 *
 * This is how the merging works:
 *
 * 1. Use the stringify function to get the message id. Look up the message in the map.
 * 2. Get the translated message. At this point we have two trees: the original tree
 * and the translated tree, where all the elements are replaced with placeholders.
 * 3. Use the original tree to create a mapping Index:number -> HtmlAst.
 * 4. Walk the translated tree.
 * 5. If we encounter a placeholder element, get its name property.
 * 6. Get the type and the index of the node using the name property.
 * 7. If the type is 'e', which means element, then:
 *     - translate the attributes of the original element
 *     - recurse to merge the children
 *     - create a new element using the original element name, original position,
 *     and translated children and attributes
 * 8. If the type if 't', which means text, then:
 *     - get the list of expressions from the original node.
 *     - get the string version of the interpolation subtree
 *     - find all the placeholders in the translated message, and replace them with the
 *     corresponding original expressions
 */
export class I18nHtmlParser {
    constructor(_htmlParser, _parser, _messagesContent, _messages, _implicitTags, _implicitAttrs) {
        this._htmlParser = _htmlParser;
        this._parser = _parser;
        this._messagesContent = _messagesContent;
        this._messages = _messages;
        this._implicitTags = _implicitTags;
        this._implicitAttrs = _implicitAttrs;
    }
    parse(sourceContent, sourceUrl, parseExpansionForms = false, interpolationConfig = DEFAULT_INTERPOLATION_CONFIG) {
        this.errors = [];
        this._interpolationConfig = interpolationConfig;
        let res = this._htmlParser.parse(sourceContent, sourceUrl, true);
        if (res.errors.length > 0) {
            return res;
        }
        else {
            let expanded = expandNodes(res.rootNodes);
            let nodes = this._recurse(expanded.nodes);
            this.errors.push(...expanded.errors);
            return this.errors.length > 0 ? new HtmlParseTreeResult([], this.errors) :
                new HtmlParseTreeResult(nodes, []);
        }
    }
    _processI18nPart(part) {
        try {
            return part.hasI18n ? this._mergeI18Part(part) : this._recurseIntoI18nPart(part);
        }
        catch (e) {
            if (e instanceof I18nError) {
                this.errors.push(e);
                return [];
            }
            else {
                throw e;
            }
        }
    }
    _mergeI18Part(part) {
        let message = part.createMessage(this._parser, this._interpolationConfig);
        let messageId = id(message);
        if (!StringMapWrapper.contains(this._messages, messageId)) {
            throw new I18nError(part.sourceSpan, `Cannot find message for id '${messageId}', content '${message.content}'.`);
        }
        let parsedMessage = this._messages[messageId];
        return this._mergeTrees(part, parsedMessage, part.children);
    }
    _recurseIntoI18nPart(p) {
        // we found an element without an i18n attribute
        // we need to recurse in cause its children may have i18n set
        // we also need to translate its attributes
        if (isPresent(p.rootElement)) {
            let root = p.rootElement;
            let children = this._recurse(p.children);
            let attrs = this._i18nAttributes(root);
            return [new HtmlElementAst(root.name, attrs, children, root.sourceSpan, root.startSourceSpan, root.endSourceSpan)];
        }
        else if (isPresent(p.rootTextNode)) {
            return [p.rootTextNode];
        }
        else {
            return this._recurse(p.children);
        }
    }
    _recurse(nodes) {
        let parts = partition(nodes, this.errors, this._implicitTags);
        return ListWrapper.flatten(parts.map(p => this._processI18nPart(p)));
    }
    _mergeTrees(p, translated, original) {
        let l = new _CreateNodeMapping();
        htmlVisitAll(l, original);
        // merge the translated tree with the original tree.
        // we do it by preserving the source code position of the original tree
        let merged = this._mergeTreesHelper(translated, l.mapping);
        // if the root element is present, we need to create a new root element with its attributes
        // translated
        if (isPresent(p.rootElement)) {
            let root = p.rootElement;
            let attrs = this._i18nAttributes(root);
            return [new HtmlElementAst(root.name, attrs, merged, root.sourceSpan, root.startSourceSpan, root.endSourceSpan)];
        }
        else if (isPresent(p.rootTextNode)) {
            throw new BaseException('should not be reached');
        }
        else {
            return merged;
        }
    }
    _mergeTreesHelper(translated, mapping) {
        return translated.map(t => {
            if (t instanceof HtmlElementAst) {
                return this._mergeElementOrInterpolation(t, translated, mapping);
            }
            else if (t instanceof HtmlTextAst) {
                return t;
            }
            else {
                throw new BaseException('should not be reached');
            }
        });
    }
    _mergeElementOrInterpolation(t, translated, mapping) {
        let name = this._getName(t);
        let type = name[0];
        let index = NumberWrapper.parseInt(name.substring(1), 10);
        let originalNode = mapping[index];
        if (type == 't') {
            return this._mergeTextInterpolation(t, originalNode);
        }
        else if (type == 'e') {
            return this._mergeElement(t, originalNode, mapping);
        }
        else {
            throw new BaseException('should not be reached');
        }
    }
    _getName(t) {
        if (t.name != _PLACEHOLDER_ELEMENT) {
            throw new I18nError(t.sourceSpan, `Unexpected tag "${t.name}". Only "${_PLACEHOLDER_ELEMENT}" tags are allowed.`);
        }
        let names = t.attrs.filter(a => a.name == _NAME_ATTR);
        if (names.length == 0) {
            throw new I18nError(t.sourceSpan, `Missing "${_NAME_ATTR}" attribute.`);
        }
        return names[0].value;
    }
    _mergeTextInterpolation(t, originalNode) {
        let split = this._parser.splitInterpolation(originalNode.value, originalNode.sourceSpan.toString(), this._interpolationConfig);
        let exps = isPresent(split) ? split.expressions : [];
        let messageSubstring = this._messagesContent.substring(t.startSourceSpan.end.offset, t.endSourceSpan.start.offset);
        let translated = this._replacePlaceholdersWithExpressions(messageSubstring, exps, originalNode.sourceSpan);
        return new HtmlTextAst(translated, originalNode.sourceSpan);
    }
    _mergeElement(t, originalNode, mapping) {
        let children = this._mergeTreesHelper(t.children, mapping);
        return new HtmlElementAst(originalNode.name, this._i18nAttributes(originalNode), children, originalNode.sourceSpan, originalNode.startSourceSpan, originalNode.endSourceSpan);
    }
    _i18nAttributes(el) {
        let res = [];
        let implicitAttrs = isPresent(this._implicitAttrs[el.name]) ? this._implicitAttrs[el.name] : [];
        el.attrs.forEach(attr => {
            if (attr.name.startsWith(I18N_ATTR_PREFIX) || attr.name == I18N_ATTR)
                return;
            let message;
            let i18ns = el.attrs.filter(a => a.name == `${I18N_ATTR_PREFIX}${attr.name}`);
            if (i18ns.length == 0) {
                if (implicitAttrs.indexOf(attr.name) == -1) {
                    res.push(attr);
                    return;
                }
                message = messageFromAttribute(this._parser, this._interpolationConfig, attr);
            }
            else {
                message = messageFromI18nAttribute(this._parser, this._interpolationConfig, el, i18ns[0]);
            }
            let messageId = id(message);
            if (StringMapWrapper.contains(this._messages, messageId)) {
                let updatedMessage = this._replaceInterpolationInAttr(attr, this._messages[messageId]);
                res.push(new HtmlAttrAst(attr.name, updatedMessage, attr.sourceSpan));
            }
            else {
                throw new I18nError(attr.sourceSpan, `Cannot find message for id '${messageId}', content '${message.content}'.`);
            }
        });
        return res;
    }
    _replaceInterpolationInAttr(attr, msg) {
        let split = this._parser.splitInterpolation(attr.value, attr.sourceSpan.toString(), this._interpolationConfig);
        let exps = isPresent(split) ? split.expressions : [];
        let first = msg[0];
        let last = msg[msg.length - 1];
        let start = first.sourceSpan.start.offset;
        let end = last instanceof HtmlElementAst ? last.endSourceSpan.end.offset : last.sourceSpan.end.offset;
        let messageSubstring = this._messagesContent.substring(start, end);
        return this._replacePlaceholdersWithExpressions(messageSubstring, exps, attr.sourceSpan);
    }
    ;
    _replacePlaceholdersWithExpressions(message, exps, sourceSpan) {
        let expMap = this._buildExprMap(exps);
        return RegExpWrapper.replaceAll(_PLACEHOLDER_EXPANDED_REGEXP, message, (match) => {
            let nameWithQuotes = match[2];
            let name = nameWithQuotes.substring(1, nameWithQuotes.length - 1);
            return this._convertIntoExpression(name, expMap, sourceSpan);
        });
    }
    _buildExprMap(exps) {
        let expMap = new Map();
        let usedNames = new Map();
        for (var i = 0; i < exps.length; i++) {
            let phName = getPhNameFromBinding(exps[i], i);
            expMap.set(dedupePhName(usedNames, phName), exps[i]);
        }
        return expMap;
    }
    _convertIntoExpression(name, expMap, sourceSpan) {
        if (expMap.has(name)) {
            return `${this._interpolationConfig.start}${expMap.get(name)}${this._interpolationConfig.end}`;
        }
        else {
            throw new I18nError(sourceSpan, `Invalid interpolation name '${name}'`);
        }
    }
}
class _CreateNodeMapping {
    constructor() {
        this.mapping = [];
    }
    visitElement(ast, context) {
        this.mapping.push(ast);
        htmlVisitAll(this, ast.children);
        return null;
    }
    visitAttr(ast, context) { return null; }
    visitText(ast, context) {
        this.mapping.push(ast);
        return null;
    }
    visitExpansion(ast, context) { return null; }
    visitExpansionCase(ast, context) { return null; }
    visitComment(ast, context) { return ''; }
}
//# sourceMappingURL=i18n_html_parser.js.map