/**
 * @license
 * Copyright Google Inc. All Rights Reserved.
 *
 * Use of this source code is governed by an MIT-style license that can be
 * found in the LICENSE file at https://angular.io/license
 */
"use strict";
var collection_1 = require('../facade/collection');
var lang_1 = require('../facade/lang');
var html_ast_1 = require('../html_ast');
var interpolation_config_1 = require('../interpolation_config');
var message_1 = require('./message');
var shared_1 = require('./shared');
/**
 * All messages extracted from a template.
 */
var ExtractionResult = (function () {
    function ExtractionResult(messages, errors) {
        this.messages = messages;
        this.errors = errors;
    }
    return ExtractionResult;
}());
exports.ExtractionResult = ExtractionResult;
/**
 * Removes duplicate messages.
 */
function removeDuplicates(messages) {
    var uniq = {};
    messages.forEach(function (m) {
        if (!collection_1.StringMapWrapper.contains(uniq, message_1.id(m))) {
            uniq[message_1.id(m)] = m;
        }
    });
    return collection_1.StringMapWrapper.values(uniq);
}
exports.removeDuplicates = removeDuplicates;
/**
 * Extracts all messages from a template.
 *
 * Algorithm:
 *
 * To understand the algorithm, you need to know how partitioning works.
 * Partitioning is required as we can use two i18n comments to group node siblings together.
 * That is why we cannot just use nodes.
 *
 * Partitioning transforms an array of HtmlAst into an array of Part.
 * A part can optionally contain a root element or a root text node. And it can also contain
 * children.
 * A part can contain i18n property, in which case it needs to be extracted.
 *
 * Example:
 *
 * The following array of nodes will be split into four parts:
 *
 * ```
 * <a>A</a>
 * <b i18n>B</b>
 * <!-- i18n -->
 * <c>C</c>
 * D
 * <!-- /i18n -->
 * E
 * ```
 *
 * Part 1 containing the a tag. It should not be translated.
 * Part 2 containing the b tag. It should be translated.
 * Part 3 containing the c tag and the D text node. It should be translated.
 * Part 4 containing the E text node. It should not be translated..
 *
 * It is also important to understand how we stringify nodes to create a message.
 *
 * We walk the tree and replace every element node with a placeholder. We also replace
 * all expressions in interpolation with placeholders. We also insert a placeholder element
 * to wrap a text node containing interpolation.
 *
 * Example:
 *
 * The following tree:
 *
 * ```
 * <a>A{{I}}</a><b>B</b>
 * ```
 *
 * will be stringified into:
 * ```
 * <ph name="e0"><ph name="t1">A<ph name="0"/></ph></ph><ph name="e2">B</ph>
 * ```
 *
 * This is what the algorithm does:
 *
 * 1. Use the provided html parser to get the html AST of the template.
 * 2. Partition the root nodes, and process each part separately.
 * 3. If a part does not have the i18n attribute, recurse to process children and attributes.
 * 4. If a part has the i18n attribute, stringify the nodes to create a Message.
 */
var MessageExtractor = (function () {
    function MessageExtractor(_htmlParser, _parser, _implicitTags, _implicitAttrs) {
        this._htmlParser = _htmlParser;
        this._parser = _parser;
        this._implicitTags = _implicitTags;
        this._implicitAttrs = _implicitAttrs;
    }
    MessageExtractor.prototype.extract = function (template, sourceUrl, interpolationConfig) {
        if (interpolationConfig === void 0) { interpolationConfig = interpolation_config_1.DEFAULT_INTERPOLATION_CONFIG; }
        this._messages = [];
        this._errors = [];
        var res = this._htmlParser.parse(template, sourceUrl, true);
        if (res.errors.length == 0) {
            this._recurse(res.rootNodes, interpolationConfig);
        }
        return new ExtractionResult(this._messages, this._errors.concat(res.errors));
    };
    MessageExtractor.prototype._extractMessagesFromPart = function (part, interpolationConfig) {
        if (part.hasI18n) {
            this._messages.push(part.createMessage(this._parser, interpolationConfig));
            this._recurseToExtractMessagesFromAttributes(part.children, interpolationConfig);
        }
        else {
            this._recurse(part.children, interpolationConfig);
        }
        if (lang_1.isPresent(part.rootElement)) {
            this._extractMessagesFromAttributes(part.rootElement, interpolationConfig);
        }
    };
    MessageExtractor.prototype._recurse = function (nodes, interpolationConfig) {
        var _this = this;
        if (lang_1.isPresent(nodes)) {
            var parts = shared_1.partition(nodes, this._errors, this._implicitTags);
            parts.forEach(function (part) { return _this._extractMessagesFromPart(part, interpolationConfig); });
        }
    };
    MessageExtractor.prototype._recurseToExtractMessagesFromAttributes = function (nodes, interpolationConfig) {
        var _this = this;
        nodes.forEach(function (n) {
            if (n instanceof html_ast_1.HtmlElementAst) {
                _this._extractMessagesFromAttributes(n, interpolationConfig);
                _this._recurseToExtractMessagesFromAttributes(n.children, interpolationConfig);
            }
        });
    };
    MessageExtractor.prototype._extractMessagesFromAttributes = function (p, interpolationConfig) {
        var _this = this;
        var transAttrs = lang_1.isPresent(this._implicitAttrs[p.name]) ? this._implicitAttrs[p.name] : [];
        var explicitAttrs = [];
        // `i18n-` prefixed attributes should be translated
        p.attrs.filter(function (attr) { return attr.name.startsWith(shared_1.I18N_ATTR_PREFIX); }).forEach(function (attr) {
            try {
                explicitAttrs.push(attr.name.substring(shared_1.I18N_ATTR_PREFIX.length));
                _this._messages.push(shared_1.messageFromI18nAttribute(_this._parser, interpolationConfig, p, attr));
            }
            catch (e) {
                if (e instanceof shared_1.I18nError) {
                    _this._errors.push(e);
                }
                else {
                    throw e;
                }
            }
        });
        // implicit attributes should also be translated
        p.attrs.filter(function (attr) { return !attr.name.startsWith(shared_1.I18N_ATTR_PREFIX); })
            .filter(function (attr) { return explicitAttrs.indexOf(attr.name) == -1; })
            .filter(function (attr) { return transAttrs.indexOf(attr.name) > -1; })
            .forEach(function (attr) {
            return _this._messages.push(shared_1.messageFromAttribute(_this._parser, interpolationConfig, attr));
        });
    };
    return MessageExtractor;
}());
exports.MessageExtractor = MessageExtractor;
//# sourceMappingURL=message_extractor.js.map