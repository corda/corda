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
var lang_1 = require('../src/facade/lang');
var collection_1 = require('../src/facade/collection');
var html_ast_1 = require('./html_ast');
var html_lexer_1 = require('./html_lexer');
var parse_util_1 = require('./parse_util');
var html_tags_1 = require('./html_tags');
var HtmlTreeError = (function (_super) {
    __extends(HtmlTreeError, _super);
    function HtmlTreeError(elementName, span, msg) {
        _super.call(this, span, msg);
        this.elementName = elementName;
    }
    HtmlTreeError.create = function (elementName, span, msg) {
        return new HtmlTreeError(elementName, span, msg);
    };
    return HtmlTreeError;
}(parse_util_1.ParseError));
exports.HtmlTreeError = HtmlTreeError;
var HtmlParseTreeResult = (function () {
    function HtmlParseTreeResult(rootNodes, errors) {
        this.rootNodes = rootNodes;
        this.errors = errors;
    }
    return HtmlParseTreeResult;
}());
exports.HtmlParseTreeResult = HtmlParseTreeResult;
var HtmlParser = (function () {
    function HtmlParser() {
    }
    HtmlParser.prototype.parse = function (sourceContent, sourceUrl, parseExpansionForms) {
        if (parseExpansionForms === void 0) { parseExpansionForms = false; }
        var tokensAndErrors = html_lexer_1.tokenizeHtml(sourceContent, sourceUrl, parseExpansionForms);
        var treeAndErrors = new TreeBuilder(tokensAndErrors.tokens).build();
        return new HtmlParseTreeResult(treeAndErrors.rootNodes, tokensAndErrors.errors.concat(treeAndErrors.errors));
    };
    /** @nocollapse */
    HtmlParser.decorators = [
        { type: core_1.Injectable },
    ];
    return HtmlParser;
}());
exports.HtmlParser = HtmlParser;
var TreeBuilder = (function () {
    function TreeBuilder(tokens) {
        this.tokens = tokens;
        this.index = -1;
        this.rootNodes = [];
        this.errors = [];
        this.elementStack = [];
        this._advance();
    }
    TreeBuilder.prototype.build = function () {
        while (this.peek.type !== html_lexer_1.HtmlTokenType.EOF) {
            if (this.peek.type === html_lexer_1.HtmlTokenType.TAG_OPEN_START) {
                this._consumeStartTag(this._advance());
            }
            else if (this.peek.type === html_lexer_1.HtmlTokenType.TAG_CLOSE) {
                this._consumeEndTag(this._advance());
            }
            else if (this.peek.type === html_lexer_1.HtmlTokenType.CDATA_START) {
                this._closeVoidElement();
                this._consumeCdata(this._advance());
            }
            else if (this.peek.type === html_lexer_1.HtmlTokenType.COMMENT_START) {
                this._closeVoidElement();
                this._consumeComment(this._advance());
            }
            else if (this.peek.type === html_lexer_1.HtmlTokenType.TEXT || this.peek.type === html_lexer_1.HtmlTokenType.RAW_TEXT ||
                this.peek.type === html_lexer_1.HtmlTokenType.ESCAPABLE_RAW_TEXT) {
                this._closeVoidElement();
                this._consumeText(this._advance());
            }
            else if (this.peek.type === html_lexer_1.HtmlTokenType.EXPANSION_FORM_START) {
                this._consumeExpansion(this._advance());
            }
            else {
                // Skip all other tokens...
                this._advance();
            }
        }
        return new HtmlParseTreeResult(this.rootNodes, this.errors);
    };
    TreeBuilder.prototype._advance = function () {
        var prev = this.peek;
        if (this.index < this.tokens.length - 1) {
            // Note: there is always an EOF token at the end
            this.index++;
        }
        this.peek = this.tokens[this.index];
        return prev;
    };
    TreeBuilder.prototype._advanceIf = function (type) {
        if (this.peek.type === type) {
            return this._advance();
        }
        return null;
    };
    TreeBuilder.prototype._consumeCdata = function (startToken) {
        this._consumeText(this._advance());
        this._advanceIf(html_lexer_1.HtmlTokenType.CDATA_END);
    };
    TreeBuilder.prototype._consumeComment = function (token) {
        var text = this._advanceIf(html_lexer_1.HtmlTokenType.RAW_TEXT);
        this._advanceIf(html_lexer_1.HtmlTokenType.COMMENT_END);
        var value = lang_1.isPresent(text) ? text.parts[0].trim() : null;
        this._addToParent(new html_ast_1.HtmlCommentAst(value, token.sourceSpan));
    };
    TreeBuilder.prototype._consumeExpansion = function (token) {
        var switchValue = this._advance();
        var type = this._advance();
        var cases = [];
        // read =
        while (this.peek.type === html_lexer_1.HtmlTokenType.EXPANSION_CASE_VALUE) {
            var expCase = this._parseExpansionCase();
            if (lang_1.isBlank(expCase))
                return; // error
            cases.push(expCase);
        }
        // read the final }
        if (this.peek.type !== html_lexer_1.HtmlTokenType.EXPANSION_FORM_END) {
            this.errors.push(HtmlTreeError.create(null, this.peek.sourceSpan, "Invalid expansion form. Missing '}'."));
            return;
        }
        this._advance();
        var mainSourceSpan = new parse_util_1.ParseSourceSpan(token.sourceSpan.start, this.peek.sourceSpan.end);
        this._addToParent(new html_ast_1.HtmlExpansionAst(switchValue.parts[0], type.parts[0], cases, mainSourceSpan, switchValue.sourceSpan));
    };
    TreeBuilder.prototype._parseExpansionCase = function () {
        var value = this._advance();
        // read {
        if (this.peek.type !== html_lexer_1.HtmlTokenType.EXPANSION_CASE_EXP_START) {
            this.errors.push(HtmlTreeError.create(null, this.peek.sourceSpan, "Invalid expansion form. Missing '{'.,"));
            return null;
        }
        // read until }
        var start = this._advance();
        var exp = this._collectExpansionExpTokens(start);
        if (lang_1.isBlank(exp))
            return null;
        var end = this._advance();
        exp.push(new html_lexer_1.HtmlToken(html_lexer_1.HtmlTokenType.EOF, [], end.sourceSpan));
        // parse everything in between { and }
        var parsedExp = new TreeBuilder(exp).build();
        if (parsedExp.errors.length > 0) {
            this.errors = this.errors.concat(parsedExp.errors);
            return null;
        }
        var sourceSpan = new parse_util_1.ParseSourceSpan(value.sourceSpan.start, end.sourceSpan.end);
        var expSourceSpan = new parse_util_1.ParseSourceSpan(start.sourceSpan.start, end.sourceSpan.end);
        return new html_ast_1.HtmlExpansionCaseAst(value.parts[0], parsedExp.rootNodes, sourceSpan, value.sourceSpan, expSourceSpan);
    };
    TreeBuilder.prototype._collectExpansionExpTokens = function (start) {
        var exp = [];
        var expansionFormStack = [html_lexer_1.HtmlTokenType.EXPANSION_CASE_EXP_START];
        while (true) {
            if (this.peek.type === html_lexer_1.HtmlTokenType.EXPANSION_FORM_START ||
                this.peek.type === html_lexer_1.HtmlTokenType.EXPANSION_CASE_EXP_START) {
                expansionFormStack.push(this.peek.type);
            }
            if (this.peek.type === html_lexer_1.HtmlTokenType.EXPANSION_CASE_EXP_END) {
                if (lastOnStack(expansionFormStack, html_lexer_1.HtmlTokenType.EXPANSION_CASE_EXP_START)) {
                    expansionFormStack.pop();
                    if (expansionFormStack.length == 0)
                        return exp;
                }
                else {
                    this.errors.push(HtmlTreeError.create(null, start.sourceSpan, "Invalid expansion form. Missing '}'."));
                    return null;
                }
            }
            if (this.peek.type === html_lexer_1.HtmlTokenType.EXPANSION_FORM_END) {
                if (lastOnStack(expansionFormStack, html_lexer_1.HtmlTokenType.EXPANSION_FORM_START)) {
                    expansionFormStack.pop();
                }
                else {
                    this.errors.push(HtmlTreeError.create(null, start.sourceSpan, "Invalid expansion form. Missing '}'."));
                    return null;
                }
            }
            if (this.peek.type === html_lexer_1.HtmlTokenType.EOF) {
                this.errors.push(HtmlTreeError.create(null, start.sourceSpan, "Invalid expansion form. Missing '}'."));
                return null;
            }
            exp.push(this._advance());
        }
    };
    TreeBuilder.prototype._consumeText = function (token) {
        var text = token.parts[0];
        if (text.length > 0 && text[0] == '\n') {
            var parent_1 = this._getParentElement();
            if (lang_1.isPresent(parent_1) && parent_1.children.length == 0 &&
                html_tags_1.getHtmlTagDefinition(parent_1.name).ignoreFirstLf) {
                text = text.substring(1);
            }
        }
        if (text.length > 0) {
            this._addToParent(new html_ast_1.HtmlTextAst(text, token.sourceSpan));
        }
    };
    TreeBuilder.prototype._closeVoidElement = function () {
        if (this.elementStack.length > 0) {
            var el = collection_1.ListWrapper.last(this.elementStack);
            if (html_tags_1.getHtmlTagDefinition(el.name).isVoid) {
                this.elementStack.pop();
            }
        }
    };
    TreeBuilder.prototype._consumeStartTag = function (startTagToken) {
        var prefix = startTagToken.parts[0];
        var name = startTagToken.parts[1];
        var attrs = [];
        while (this.peek.type === html_lexer_1.HtmlTokenType.ATTR_NAME) {
            attrs.push(this._consumeAttr(this._advance()));
        }
        var fullName = getElementFullName(prefix, name, this._getParentElement());
        var selfClosing = false;
        // Note: There could have been a tokenizer error
        // so that we don't get a token for the end tag...
        if (this.peek.type === html_lexer_1.HtmlTokenType.TAG_OPEN_END_VOID) {
            this._advance();
            selfClosing = true;
            if (html_tags_1.getNsPrefix(fullName) == null && !html_tags_1.getHtmlTagDefinition(fullName).isVoid) {
                this.errors.push(HtmlTreeError.create(fullName, startTagToken.sourceSpan, "Only void and foreign elements can be self closed \"" + startTagToken.parts[1] + "\""));
            }
        }
        else if (this.peek.type === html_lexer_1.HtmlTokenType.TAG_OPEN_END) {
            this._advance();
            selfClosing = false;
        }
        var end = this.peek.sourceSpan.start;
        var span = new parse_util_1.ParseSourceSpan(startTagToken.sourceSpan.start, end);
        var el = new html_ast_1.HtmlElementAst(fullName, attrs, [], span, span, null);
        this._pushElement(el);
        if (selfClosing) {
            this._popElement(fullName);
            el.endSourceSpan = span;
        }
    };
    TreeBuilder.prototype._pushElement = function (el) {
        if (this.elementStack.length > 0) {
            var parentEl = collection_1.ListWrapper.last(this.elementStack);
            if (html_tags_1.getHtmlTagDefinition(parentEl.name).isClosedByChild(el.name)) {
                this.elementStack.pop();
            }
        }
        var tagDef = html_tags_1.getHtmlTagDefinition(el.name);
        var _a = this._getParentElementSkippingContainers(), parent = _a.parent, container = _a.container;
        if (lang_1.isPresent(parent) && tagDef.requireExtraParent(parent.name)) {
            var newParent = new html_ast_1.HtmlElementAst(tagDef.parentToAdd, [], [], el.sourceSpan, el.startSourceSpan, el.endSourceSpan);
            this._insertBeforeContainer(parent, container, newParent);
        }
        this._addToParent(el);
        this.elementStack.push(el);
    };
    TreeBuilder.prototype._consumeEndTag = function (endTagToken) {
        var fullName = getElementFullName(endTagToken.parts[0], endTagToken.parts[1], this._getParentElement());
        if (this._getParentElement()) {
            this._getParentElement().endSourceSpan = endTagToken.sourceSpan;
        }
        if (html_tags_1.getHtmlTagDefinition(fullName).isVoid) {
            this.errors.push(HtmlTreeError.create(fullName, endTagToken.sourceSpan, "Void elements do not have end tags \"" + endTagToken.parts[1] + "\""));
        }
        else if (!this._popElement(fullName)) {
            this.errors.push(HtmlTreeError.create(fullName, endTagToken.sourceSpan, "Unexpected closing tag \"" + endTagToken.parts[1] + "\""));
        }
    };
    TreeBuilder.prototype._popElement = function (fullName) {
        for (var stackIndex = this.elementStack.length - 1; stackIndex >= 0; stackIndex--) {
            var el = this.elementStack[stackIndex];
            if (el.name == fullName) {
                collection_1.ListWrapper.splice(this.elementStack, stackIndex, this.elementStack.length - stackIndex);
                return true;
            }
            if (!html_tags_1.getHtmlTagDefinition(el.name).closedByParent) {
                return false;
            }
        }
        return false;
    };
    TreeBuilder.prototype._consumeAttr = function (attrName) {
        var fullName = html_tags_1.mergeNsAndName(attrName.parts[0], attrName.parts[1]);
        var end = attrName.sourceSpan.end;
        var value = '';
        if (this.peek.type === html_lexer_1.HtmlTokenType.ATTR_VALUE) {
            var valueToken = this._advance();
            value = valueToken.parts[0];
            end = valueToken.sourceSpan.end;
        }
        return new html_ast_1.HtmlAttrAst(fullName, value, new parse_util_1.ParseSourceSpan(attrName.sourceSpan.start, end));
    };
    TreeBuilder.prototype._getParentElement = function () {
        return this.elementStack.length > 0 ? collection_1.ListWrapper.last(this.elementStack) : null;
    };
    /**
     * Returns the parent in the DOM and the container.
     *
     * `<ng-container>` elements are skipped as they are not rendered as DOM element.
     */
    TreeBuilder.prototype._getParentElementSkippingContainers = function () {
        var container = null;
        for (var i = this.elementStack.length - 1; i >= 0; i--) {
            if (this.elementStack[i].name !== 'ng-container') {
                return { parent: this.elementStack[i], container: container };
            }
            container = this.elementStack[i];
        }
        return { parent: collection_1.ListWrapper.last(this.elementStack), container: container };
    };
    TreeBuilder.prototype._addToParent = function (node) {
        var parent = this._getParentElement();
        if (lang_1.isPresent(parent)) {
            parent.children.push(node);
        }
        else {
            this.rootNodes.push(node);
        }
    };
    /**
     * Insert a node between the parent and the container.
     * When no container is given, the node is appended as a child of the parent.
     * Also updates the element stack accordingly.
     *
     * @internal
     */
    TreeBuilder.prototype._insertBeforeContainer = function (parent, container, node) {
        if (!container) {
            this._addToParent(node);
            this.elementStack.push(node);
        }
        else {
            if (parent) {
                // replace the container with the new node in the children
                var index = parent.children.indexOf(container);
                parent.children[index] = node;
            }
            else {
                this.rootNodes.push(node);
            }
            node.children.push(container);
            this.elementStack.splice(this.elementStack.indexOf(container), 0, node);
        }
    };
    return TreeBuilder;
}());
function getElementFullName(prefix, localName, parentElement) {
    if (lang_1.isBlank(prefix)) {
        prefix = html_tags_1.getHtmlTagDefinition(localName).implicitNamespacePrefix;
        if (lang_1.isBlank(prefix) && lang_1.isPresent(parentElement)) {
            prefix = html_tags_1.getNsPrefix(parentElement.name);
        }
    }
    return html_tags_1.mergeNsAndName(prefix, localName);
}
function lastOnStack(stack, element) {
    return stack.length > 0 && stack[stack.length - 1] === element;
}
//# sourceMappingURL=html_parser.js.map