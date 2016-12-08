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
var chars = require('./chars');
var lang_1 = require('./facade/lang');
var html_tags_1 = require('./html_tags');
var interpolation_config_1 = require('./interpolation_config');
var parse_util_1 = require('./parse_util');
(function (HtmlTokenType) {
    HtmlTokenType[HtmlTokenType["TAG_OPEN_START"] = 0] = "TAG_OPEN_START";
    HtmlTokenType[HtmlTokenType["TAG_OPEN_END"] = 1] = "TAG_OPEN_END";
    HtmlTokenType[HtmlTokenType["TAG_OPEN_END_VOID"] = 2] = "TAG_OPEN_END_VOID";
    HtmlTokenType[HtmlTokenType["TAG_CLOSE"] = 3] = "TAG_CLOSE";
    HtmlTokenType[HtmlTokenType["TEXT"] = 4] = "TEXT";
    HtmlTokenType[HtmlTokenType["ESCAPABLE_RAW_TEXT"] = 5] = "ESCAPABLE_RAW_TEXT";
    HtmlTokenType[HtmlTokenType["RAW_TEXT"] = 6] = "RAW_TEXT";
    HtmlTokenType[HtmlTokenType["COMMENT_START"] = 7] = "COMMENT_START";
    HtmlTokenType[HtmlTokenType["COMMENT_END"] = 8] = "COMMENT_END";
    HtmlTokenType[HtmlTokenType["CDATA_START"] = 9] = "CDATA_START";
    HtmlTokenType[HtmlTokenType["CDATA_END"] = 10] = "CDATA_END";
    HtmlTokenType[HtmlTokenType["ATTR_NAME"] = 11] = "ATTR_NAME";
    HtmlTokenType[HtmlTokenType["ATTR_VALUE"] = 12] = "ATTR_VALUE";
    HtmlTokenType[HtmlTokenType["DOC_TYPE"] = 13] = "DOC_TYPE";
    HtmlTokenType[HtmlTokenType["EXPANSION_FORM_START"] = 14] = "EXPANSION_FORM_START";
    HtmlTokenType[HtmlTokenType["EXPANSION_CASE_VALUE"] = 15] = "EXPANSION_CASE_VALUE";
    HtmlTokenType[HtmlTokenType["EXPANSION_CASE_EXP_START"] = 16] = "EXPANSION_CASE_EXP_START";
    HtmlTokenType[HtmlTokenType["EXPANSION_CASE_EXP_END"] = 17] = "EXPANSION_CASE_EXP_END";
    HtmlTokenType[HtmlTokenType["EXPANSION_FORM_END"] = 18] = "EXPANSION_FORM_END";
    HtmlTokenType[HtmlTokenType["EOF"] = 19] = "EOF";
})(exports.HtmlTokenType || (exports.HtmlTokenType = {}));
var HtmlTokenType = exports.HtmlTokenType;
var HtmlToken = (function () {
    function HtmlToken(type, parts, sourceSpan) {
        this.type = type;
        this.parts = parts;
        this.sourceSpan = sourceSpan;
    }
    return HtmlToken;
}());
exports.HtmlToken = HtmlToken;
var HtmlTokenError = (function (_super) {
    __extends(HtmlTokenError, _super);
    function HtmlTokenError(errorMsg, tokenType, span) {
        _super.call(this, span, errorMsg);
        this.tokenType = tokenType;
    }
    return HtmlTokenError;
}(parse_util_1.ParseError));
exports.HtmlTokenError = HtmlTokenError;
var HtmlTokenizeResult = (function () {
    function HtmlTokenizeResult(tokens, errors) {
        this.tokens = tokens;
        this.errors = errors;
    }
    return HtmlTokenizeResult;
}());
exports.HtmlTokenizeResult = HtmlTokenizeResult;
function tokenizeHtml(sourceContent, sourceUrl, tokenizeExpansionForms, interpolationConfig) {
    if (tokenizeExpansionForms === void 0) { tokenizeExpansionForms = false; }
    if (interpolationConfig === void 0) { interpolationConfig = interpolation_config_1.DEFAULT_INTERPOLATION_CONFIG; }
    return new _HtmlTokenizer(new parse_util_1.ParseSourceFile(sourceContent, sourceUrl), tokenizeExpansionForms, interpolationConfig)
        .tokenize();
}
exports.tokenizeHtml = tokenizeHtml;
var CR_OR_CRLF_REGEXP = /\r\n?/g;
function unexpectedCharacterErrorMsg(charCode) {
    var char = charCode === chars.$EOF ? 'EOF' : lang_1.StringWrapper.fromCharCode(charCode);
    return "Unexpected character \"" + char + "\"";
}
function unknownEntityErrorMsg(entitySrc) {
    return "Unknown entity \"" + entitySrc + "\" - use the \"&#<decimal>;\" or  \"&#x<hex>;\" syntax";
}
var ControlFlowError = (function () {
    function ControlFlowError(error) {
        this.error = error;
    }
    return ControlFlowError;
}());
// See http://www.w3.org/TR/html51/syntax.html#writing
var _HtmlTokenizer = (function () {
    function _HtmlTokenizer(file, tokenizeExpansionForms, interpolationConfig) {
        if (interpolationConfig === void 0) { interpolationConfig = interpolation_config_1.DEFAULT_INTERPOLATION_CONFIG; }
        this.file = file;
        this.tokenizeExpansionForms = tokenizeExpansionForms;
        this.interpolationConfig = interpolationConfig;
        // Note: this is always lowercase!
        this._peek = -1;
        this._nextPeek = -1;
        this._index = -1;
        this._line = 0;
        this._column = -1;
        this._expansionCaseStack = [];
        this._inInterpolation = false;
        this.tokens = [];
        this.errors = [];
        this._input = file.content;
        this._length = file.content.length;
        this._advance();
    }
    _HtmlTokenizer.prototype._processCarriageReturns = function (content) {
        // http://www.w3.org/TR/html5/syntax.html#preprocessing-the-input-stream
        // In order to keep the original position in the source, we can not
        // pre-process it.
        // Instead CRs are processed right before instantiating the tokens.
        return lang_1.StringWrapper.replaceAll(content, CR_OR_CRLF_REGEXP, '\n');
    };
    _HtmlTokenizer.prototype.tokenize = function () {
        while (this._peek !== chars.$EOF) {
            var start = this._getLocation();
            try {
                if (this._attemptCharCode(chars.$LT)) {
                    if (this._attemptCharCode(chars.$BANG)) {
                        if (this._attemptCharCode(chars.$LBRACKET)) {
                            this._consumeCdata(start);
                        }
                        else if (this._attemptCharCode(chars.$MINUS)) {
                            this._consumeComment(start);
                        }
                        else {
                            this._consumeDocType(start);
                        }
                    }
                    else if (this._attemptCharCode(chars.$SLASH)) {
                        this._consumeTagClose(start);
                    }
                    else {
                        this._consumeTagOpen(start);
                    }
                }
                else if (isExpansionFormStart(this._input, this._index, this.interpolationConfig.start) &&
                    this.tokenizeExpansionForms) {
                    this._consumeExpansionFormStart();
                }
                else if (isExpansionCaseStart(this._peek) && this._isInExpansionForm() &&
                    this.tokenizeExpansionForms) {
                    this._consumeExpansionCaseStart();
                }
                else if (this._peek === chars.$RBRACE && this._isInExpansionCase() &&
                    this.tokenizeExpansionForms) {
                    this._consumeExpansionCaseEnd();
                }
                else if (this._peek === chars.$RBRACE && this._isInExpansionForm() &&
                    this.tokenizeExpansionForms) {
                    this._consumeExpansionFormEnd();
                }
                else {
                    this._consumeText();
                }
            }
            catch (e) {
                if (e instanceof ControlFlowError) {
                    this.errors.push(e.error);
                }
                else {
                    throw e;
                }
            }
        }
        this._beginToken(HtmlTokenType.EOF);
        this._endToken([]);
        return new HtmlTokenizeResult(mergeTextTokens(this.tokens), this.errors);
    };
    _HtmlTokenizer.prototype._getLocation = function () {
        return new parse_util_1.ParseLocation(this.file, this._index, this._line, this._column);
    };
    _HtmlTokenizer.prototype._getSpan = function (start, end) {
        if (lang_1.isBlank(start)) {
            start = this._getLocation();
        }
        if (lang_1.isBlank(end)) {
            end = this._getLocation();
        }
        return new parse_util_1.ParseSourceSpan(start, end);
    };
    _HtmlTokenizer.prototype._beginToken = function (type, start) {
        if (start === void 0) { start = null; }
        if (lang_1.isBlank(start)) {
            start = this._getLocation();
        }
        this._currentTokenStart = start;
        this._currentTokenType = type;
    };
    _HtmlTokenizer.prototype._endToken = function (parts, end) {
        if (end === void 0) { end = null; }
        if (lang_1.isBlank(end)) {
            end = this._getLocation();
        }
        var token = new HtmlToken(this._currentTokenType, parts, new parse_util_1.ParseSourceSpan(this._currentTokenStart, end));
        this.tokens.push(token);
        this._currentTokenStart = null;
        this._currentTokenType = null;
        return token;
    };
    _HtmlTokenizer.prototype._createError = function (msg, span) {
        var error = new HtmlTokenError(msg, this._currentTokenType, span);
        this._currentTokenStart = null;
        this._currentTokenType = null;
        return new ControlFlowError(error);
    };
    _HtmlTokenizer.prototype._advance = function () {
        if (this._index >= this._length) {
            throw this._createError(unexpectedCharacterErrorMsg(chars.$EOF), this._getSpan());
        }
        if (this._peek === chars.$LF) {
            this._line++;
            this._column = 0;
        }
        else if (this._peek !== chars.$LF && this._peek !== chars.$CR) {
            this._column++;
        }
        this._index++;
        this._peek = this._index >= this._length ? chars.$EOF :
            lang_1.StringWrapper.charCodeAt(this._input, this._index);
        this._nextPeek = this._index + 1 >= this._length ?
            chars.$EOF :
            lang_1.StringWrapper.charCodeAt(this._input, this._index + 1);
    };
    _HtmlTokenizer.prototype._attemptCharCode = function (charCode) {
        if (this._peek === charCode) {
            this._advance();
            return true;
        }
        return false;
    };
    _HtmlTokenizer.prototype._attemptCharCodeCaseInsensitive = function (charCode) {
        if (compareCharCodeCaseInsensitive(this._peek, charCode)) {
            this._advance();
            return true;
        }
        return false;
    };
    _HtmlTokenizer.prototype._requireCharCode = function (charCode) {
        var location = this._getLocation();
        if (!this._attemptCharCode(charCode)) {
            throw this._createError(unexpectedCharacterErrorMsg(this._peek), this._getSpan(location, location));
        }
    };
    _HtmlTokenizer.prototype._attemptStr = function (chars) {
        var len = chars.length;
        if (this._index + len > this._length) {
            return false;
        }
        var initialPosition = this._savePosition();
        for (var i = 0; i < len; i++) {
            if (!this._attemptCharCode(lang_1.StringWrapper.charCodeAt(chars, i))) {
                // If attempting to parse the string fails, we want to reset the parser
                // to where it was before the attempt
                this._restorePosition(initialPosition);
                return false;
            }
        }
        return true;
    };
    _HtmlTokenizer.prototype._attemptStrCaseInsensitive = function (chars) {
        for (var i = 0; i < chars.length; i++) {
            if (!this._attemptCharCodeCaseInsensitive(lang_1.StringWrapper.charCodeAt(chars, i))) {
                return false;
            }
        }
        return true;
    };
    _HtmlTokenizer.prototype._requireStr = function (chars) {
        var location = this._getLocation();
        if (!this._attemptStr(chars)) {
            throw this._createError(unexpectedCharacterErrorMsg(this._peek), this._getSpan(location));
        }
    };
    _HtmlTokenizer.prototype._attemptCharCodeUntilFn = function (predicate) {
        while (!predicate(this._peek)) {
            this._advance();
        }
    };
    _HtmlTokenizer.prototype._requireCharCodeUntilFn = function (predicate, len) {
        var start = this._getLocation();
        this._attemptCharCodeUntilFn(predicate);
        if (this._index - start.offset < len) {
            throw this._createError(unexpectedCharacterErrorMsg(this._peek), this._getSpan(start, start));
        }
    };
    _HtmlTokenizer.prototype._attemptUntilChar = function (char) {
        while (this._peek !== char) {
            this._advance();
        }
    };
    _HtmlTokenizer.prototype._readChar = function (decodeEntities) {
        if (decodeEntities && this._peek === chars.$AMPERSAND) {
            return this._decodeEntity();
        }
        else {
            var index = this._index;
            this._advance();
            return this._input[index];
        }
    };
    _HtmlTokenizer.prototype._decodeEntity = function () {
        var start = this._getLocation();
        this._advance();
        if (this._attemptCharCode(chars.$HASH)) {
            var isHex = this._attemptCharCode(chars.$x) || this._attemptCharCode(chars.$X);
            var numberStart = this._getLocation().offset;
            this._attemptCharCodeUntilFn(isDigitEntityEnd);
            if (this._peek != chars.$SEMICOLON) {
                throw this._createError(unexpectedCharacterErrorMsg(this._peek), this._getSpan());
            }
            this._advance();
            var strNum = this._input.substring(numberStart, this._index - 1);
            try {
                var charCode = lang_1.NumberWrapper.parseInt(strNum, isHex ? 16 : 10);
                return lang_1.StringWrapper.fromCharCode(charCode);
            }
            catch (e) {
                var entity = this._input.substring(start.offset + 1, this._index - 1);
                throw this._createError(unknownEntityErrorMsg(entity), this._getSpan(start));
            }
        }
        else {
            var startPosition = this._savePosition();
            this._attemptCharCodeUntilFn(isNamedEntityEnd);
            if (this._peek != chars.$SEMICOLON) {
                this._restorePosition(startPosition);
                return '&';
            }
            this._advance();
            var name_1 = this._input.substring(start.offset + 1, this._index - 1);
            var char = html_tags_1.NAMED_ENTITIES[name_1];
            if (lang_1.isBlank(char)) {
                throw this._createError(unknownEntityErrorMsg(name_1), this._getSpan(start));
            }
            return char;
        }
    };
    _HtmlTokenizer.prototype._consumeRawText = function (decodeEntities, firstCharOfEnd, attemptEndRest) {
        var tagCloseStart;
        var textStart = this._getLocation();
        this._beginToken(decodeEntities ? HtmlTokenType.ESCAPABLE_RAW_TEXT : HtmlTokenType.RAW_TEXT, textStart);
        var parts = [];
        while (true) {
            tagCloseStart = this._getLocation();
            if (this._attemptCharCode(firstCharOfEnd) && attemptEndRest()) {
                break;
            }
            if (this._index > tagCloseStart.offset) {
                // add the characters consumed by the previous if statement to the output
                parts.push(this._input.substring(tagCloseStart.offset, this._index));
            }
            while (this._peek !== firstCharOfEnd) {
                parts.push(this._readChar(decodeEntities));
            }
        }
        return this._endToken([this._processCarriageReturns(parts.join(''))], tagCloseStart);
    };
    _HtmlTokenizer.prototype._consumeComment = function (start) {
        var _this = this;
        this._beginToken(HtmlTokenType.COMMENT_START, start);
        this._requireCharCode(chars.$MINUS);
        this._endToken([]);
        var textToken = this._consumeRawText(false, chars.$MINUS, function () { return _this._attemptStr('->'); });
        this._beginToken(HtmlTokenType.COMMENT_END, textToken.sourceSpan.end);
        this._endToken([]);
    };
    _HtmlTokenizer.prototype._consumeCdata = function (start) {
        var _this = this;
        this._beginToken(HtmlTokenType.CDATA_START, start);
        this._requireStr('CDATA[');
        this._endToken([]);
        var textToken = this._consumeRawText(false, chars.$RBRACKET, function () { return _this._attemptStr(']>'); });
        this._beginToken(HtmlTokenType.CDATA_END, textToken.sourceSpan.end);
        this._endToken([]);
    };
    _HtmlTokenizer.prototype._consumeDocType = function (start) {
        this._beginToken(HtmlTokenType.DOC_TYPE, start);
        this._attemptUntilChar(chars.$GT);
        this._advance();
        this._endToken([this._input.substring(start.offset + 2, this._index - 1)]);
    };
    _HtmlTokenizer.prototype._consumePrefixAndName = function () {
        var nameOrPrefixStart = this._index;
        var prefix = null;
        while (this._peek !== chars.$COLON && !isPrefixEnd(this._peek)) {
            this._advance();
        }
        var nameStart;
        if (this._peek === chars.$COLON) {
            this._advance();
            prefix = this._input.substring(nameOrPrefixStart, this._index - 1);
            nameStart = this._index;
        }
        else {
            nameStart = nameOrPrefixStart;
        }
        this._requireCharCodeUntilFn(isNameEnd, this._index === nameStart ? 1 : 0);
        var name = this._input.substring(nameStart, this._index);
        return [prefix, name];
    };
    _HtmlTokenizer.prototype._consumeTagOpen = function (start) {
        var savedPos = this._savePosition();
        var lowercaseTagName;
        try {
            if (!chars.isAsciiLetter(this._peek)) {
                throw this._createError(unexpectedCharacterErrorMsg(this._peek), this._getSpan());
            }
            var nameStart = this._index;
            this._consumeTagOpenStart(start);
            lowercaseTagName = this._input.substring(nameStart, this._index).toLowerCase();
            this._attemptCharCodeUntilFn(isNotWhitespace);
            while (this._peek !== chars.$SLASH && this._peek !== chars.$GT) {
                this._consumeAttributeName();
                this._attemptCharCodeUntilFn(isNotWhitespace);
                if (this._attemptCharCode(chars.$EQ)) {
                    this._attemptCharCodeUntilFn(isNotWhitespace);
                    this._consumeAttributeValue();
                }
                this._attemptCharCodeUntilFn(isNotWhitespace);
            }
            this._consumeTagOpenEnd();
        }
        catch (e) {
            if (e instanceof ControlFlowError) {
                // When the start tag is invalid, assume we want a "<"
                this._restorePosition(savedPos);
                // Back to back text tokens are merged at the end
                this._beginToken(HtmlTokenType.TEXT, start);
                this._endToken(['<']);
                return;
            }
            throw e;
        }
        var contentTokenType = html_tags_1.getHtmlTagDefinition(lowercaseTagName).contentType;
        if (contentTokenType === html_tags_1.HtmlTagContentType.RAW_TEXT) {
            this._consumeRawTextWithTagClose(lowercaseTagName, false);
        }
        else if (contentTokenType === html_tags_1.HtmlTagContentType.ESCAPABLE_RAW_TEXT) {
            this._consumeRawTextWithTagClose(lowercaseTagName, true);
        }
    };
    _HtmlTokenizer.prototype._consumeRawTextWithTagClose = function (lowercaseTagName, decodeEntities) {
        var _this = this;
        var textToken = this._consumeRawText(decodeEntities, chars.$LT, function () {
            if (!_this._attemptCharCode(chars.$SLASH))
                return false;
            _this._attemptCharCodeUntilFn(isNotWhitespace);
            if (!_this._attemptStrCaseInsensitive(lowercaseTagName))
                return false;
            _this._attemptCharCodeUntilFn(isNotWhitespace);
            if (!_this._attemptCharCode(chars.$GT))
                return false;
            return true;
        });
        this._beginToken(HtmlTokenType.TAG_CLOSE, textToken.sourceSpan.end);
        this._endToken([null, lowercaseTagName]);
    };
    _HtmlTokenizer.prototype._consumeTagOpenStart = function (start) {
        this._beginToken(HtmlTokenType.TAG_OPEN_START, start);
        var parts = this._consumePrefixAndName();
        this._endToken(parts);
    };
    _HtmlTokenizer.prototype._consumeAttributeName = function () {
        this._beginToken(HtmlTokenType.ATTR_NAME);
        var prefixAndName = this._consumePrefixAndName();
        this._endToken(prefixAndName);
    };
    _HtmlTokenizer.prototype._consumeAttributeValue = function () {
        this._beginToken(HtmlTokenType.ATTR_VALUE);
        var value;
        if (this._peek === chars.$SQ || this._peek === chars.$DQ) {
            var quoteChar = this._peek;
            this._advance();
            var parts = [];
            while (this._peek !== quoteChar) {
                parts.push(this._readChar(true));
            }
            value = parts.join('');
            this._advance();
        }
        else {
            var valueStart = this._index;
            this._requireCharCodeUntilFn(isNameEnd, 1);
            value = this._input.substring(valueStart, this._index);
        }
        this._endToken([this._processCarriageReturns(value)]);
    };
    _HtmlTokenizer.prototype._consumeTagOpenEnd = function () {
        var tokenType = this._attemptCharCode(chars.$SLASH) ? HtmlTokenType.TAG_OPEN_END_VOID :
            HtmlTokenType.TAG_OPEN_END;
        this._beginToken(tokenType);
        this._requireCharCode(chars.$GT);
        this._endToken([]);
    };
    _HtmlTokenizer.prototype._consumeTagClose = function (start) {
        this._beginToken(HtmlTokenType.TAG_CLOSE, start);
        this._attemptCharCodeUntilFn(isNotWhitespace);
        var prefixAndName = this._consumePrefixAndName();
        this._attemptCharCodeUntilFn(isNotWhitespace);
        this._requireCharCode(chars.$GT);
        this._endToken(prefixAndName);
    };
    _HtmlTokenizer.prototype._consumeExpansionFormStart = function () {
        this._beginToken(HtmlTokenType.EXPANSION_FORM_START, this._getLocation());
        this._requireCharCode(chars.$LBRACE);
        this._endToken([]);
        this._beginToken(HtmlTokenType.RAW_TEXT, this._getLocation());
        var condition = this._readUntil(chars.$COMMA);
        this._endToken([condition], this._getLocation());
        this._requireCharCode(chars.$COMMA);
        this._attemptCharCodeUntilFn(isNotWhitespace);
        this._beginToken(HtmlTokenType.RAW_TEXT, this._getLocation());
        var type = this._readUntil(chars.$COMMA);
        this._endToken([type], this._getLocation());
        this._requireCharCode(chars.$COMMA);
        this._attemptCharCodeUntilFn(isNotWhitespace);
        this._expansionCaseStack.push(HtmlTokenType.EXPANSION_FORM_START);
    };
    _HtmlTokenizer.prototype._consumeExpansionCaseStart = function () {
        this._beginToken(HtmlTokenType.EXPANSION_CASE_VALUE, this._getLocation());
        var value = this._readUntil(chars.$LBRACE).trim();
        this._endToken([value], this._getLocation());
        this._attemptCharCodeUntilFn(isNotWhitespace);
        this._beginToken(HtmlTokenType.EXPANSION_CASE_EXP_START, this._getLocation());
        this._requireCharCode(chars.$LBRACE);
        this._endToken([], this._getLocation());
        this._attemptCharCodeUntilFn(isNotWhitespace);
        this._expansionCaseStack.push(HtmlTokenType.EXPANSION_CASE_EXP_START);
    };
    _HtmlTokenizer.prototype._consumeExpansionCaseEnd = function () {
        this._beginToken(HtmlTokenType.EXPANSION_CASE_EXP_END, this._getLocation());
        this._requireCharCode(chars.$RBRACE);
        this._endToken([], this._getLocation());
        this._attemptCharCodeUntilFn(isNotWhitespace);
        this._expansionCaseStack.pop();
    };
    _HtmlTokenizer.prototype._consumeExpansionFormEnd = function () {
        this._beginToken(HtmlTokenType.EXPANSION_FORM_END, this._getLocation());
        this._requireCharCode(chars.$RBRACE);
        this._endToken([]);
        this._expansionCaseStack.pop();
    };
    _HtmlTokenizer.prototype._consumeText = function () {
        var start = this._getLocation();
        this._beginToken(HtmlTokenType.TEXT, start);
        var parts = [];
        do {
            if (this._attemptStr(this.interpolationConfig.start)) {
                parts.push(this.interpolationConfig.start);
                this._inInterpolation = true;
            }
            else if (this._attemptStr(this.interpolationConfig.end) && this._inInterpolation) {
                parts.push(this.interpolationConfig.end);
                this._inInterpolation = false;
            }
            else {
                parts.push(this._readChar(true));
            }
        } while (!this._isTextEnd());
        this._endToken([this._processCarriageReturns(parts.join(''))]);
    };
    _HtmlTokenizer.prototype._isTextEnd = function () {
        if (this._peek === chars.$LT || this._peek === chars.$EOF) {
            return true;
        }
        if (this.tokenizeExpansionForms) {
            if (isExpansionFormStart(this._input, this._index, this.interpolationConfig.start)) {
                // start of an expansion form
                return true;
            }
            if (this._peek === chars.$RBRACE && !this._inInterpolation && this._isInExpansionCase()) {
                // end of and expansion case
                return true;
            }
        }
        return false;
    };
    _HtmlTokenizer.prototype._savePosition = function () {
        return [this._peek, this._index, this._column, this._line, this.tokens.length];
    };
    _HtmlTokenizer.prototype._readUntil = function (char) {
        var start = this._index;
        this._attemptUntilChar(char);
        return this._input.substring(start, this._index);
    };
    _HtmlTokenizer.prototype._restorePosition = function (position) {
        this._peek = position[0];
        this._index = position[1];
        this._column = position[2];
        this._line = position[3];
        var nbTokens = position[4];
        if (nbTokens < this.tokens.length) {
            // remove any extra tokens
            this.tokens = this.tokens.slice(0, nbTokens);
        }
    };
    _HtmlTokenizer.prototype._isInExpansionCase = function () {
        return this._expansionCaseStack.length > 0 &&
            this._expansionCaseStack[this._expansionCaseStack.length - 1] ===
                HtmlTokenType.EXPANSION_CASE_EXP_START;
    };
    _HtmlTokenizer.prototype._isInExpansionForm = function () {
        return this._expansionCaseStack.length > 0 &&
            this._expansionCaseStack[this._expansionCaseStack.length - 1] ===
                HtmlTokenType.EXPANSION_FORM_START;
    };
    return _HtmlTokenizer;
}());
function isNotWhitespace(code) {
    return !chars.isWhitespace(code) || code === chars.$EOF;
}
function isNameEnd(code) {
    return chars.isWhitespace(code) || code === chars.$GT || code === chars.$SLASH ||
        code === chars.$SQ || code === chars.$DQ || code === chars.$EQ;
}
function isPrefixEnd(code) {
    return (code < chars.$a || chars.$z < code) && (code < chars.$A || chars.$Z < code) &&
        (code < chars.$0 || code > chars.$9);
}
function isDigitEntityEnd(code) {
    return code == chars.$SEMICOLON || code == chars.$EOF || !chars.isAsciiHexDigit(code);
}
function isNamedEntityEnd(code) {
    return code == chars.$SEMICOLON || code == chars.$EOF || !chars.isAsciiLetter(code);
}
function isExpansionFormStart(input, offset, interpolationStart) {
    return input.charCodeAt(offset) == chars.$LBRACE &&
        input.indexOf(interpolationStart, offset) != offset;
}
function isExpansionCaseStart(peek) {
    return peek === chars.$EQ || chars.isAsciiLetter(peek);
}
function compareCharCodeCaseInsensitive(code1, code2) {
    return toUpperCaseCharCode(code1) == toUpperCaseCharCode(code2);
}
function toUpperCaseCharCode(code) {
    return code >= chars.$a && code <= chars.$z ? code - chars.$a + chars.$A : code;
}
function mergeTextTokens(srcTokens) {
    var dstTokens = [];
    var lastDstToken;
    for (var i = 0; i < srcTokens.length; i++) {
        var token = srcTokens[i];
        if (lang_1.isPresent(lastDstToken) && lastDstToken.type == HtmlTokenType.TEXT &&
            token.type == HtmlTokenType.TEXT) {
            lastDstToken.parts[0] += token.parts[0];
            lastDstToken.sourceSpan.end = token.sourceSpan.end;
        }
        else {
            lastDstToken = token;
            dstTokens.push(lastDstToken);
        }
    }
    return dstTokens;
}
//# sourceMappingURL=html_lexer.js.map