/**
 * @license
 * Copyright Google Inc. All Rights Reserved.
 *
 * Use of this source code is governed by an MIT-style license that can be
 * found in the LICENSE file at https://angular.io/license
 */
"use strict";
var ParseLocation = (function () {
    function ParseLocation(file, offset, line, col) {
        this.file = file;
        this.offset = offset;
        this.line = line;
        this.col = col;
    }
    ParseLocation.prototype.toString = function () { return this.file.url + "@" + this.line + ":" + this.col; };
    return ParseLocation;
}());
exports.ParseLocation = ParseLocation;
var ParseSourceFile = (function () {
    function ParseSourceFile(content, url) {
        this.content = content;
        this.url = url;
    }
    return ParseSourceFile;
}());
exports.ParseSourceFile = ParseSourceFile;
var ParseSourceSpan = (function () {
    function ParseSourceSpan(start, end) {
        this.start = start;
        this.end = end;
    }
    ParseSourceSpan.prototype.toString = function () {
        return this.start.file.content.substring(this.start.offset, this.end.offset);
    };
    return ParseSourceSpan;
}());
exports.ParseSourceSpan = ParseSourceSpan;
(function (ParseErrorLevel) {
    ParseErrorLevel[ParseErrorLevel["WARNING"] = 0] = "WARNING";
    ParseErrorLevel[ParseErrorLevel["FATAL"] = 1] = "FATAL";
})(exports.ParseErrorLevel || (exports.ParseErrorLevel = {}));
var ParseErrorLevel = exports.ParseErrorLevel;
var ParseError = (function () {
    function ParseError(span, msg, level) {
        if (level === void 0) { level = ParseErrorLevel.FATAL; }
        this.span = span;
        this.msg = msg;
        this.level = level;
    }
    ParseError.prototype.toString = function () {
        var source = this.span.start.file.content;
        var ctxStart = this.span.start.offset;
        if (ctxStart > source.length - 1) {
            ctxStart = source.length - 1;
        }
        var ctxEnd = ctxStart;
        var ctxLen = 0;
        var ctxLines = 0;
        while (ctxLen < 100 && ctxStart > 0) {
            ctxStart--;
            ctxLen++;
            if (source[ctxStart] == '\n') {
                if (++ctxLines == 3) {
                    break;
                }
            }
        }
        ctxLen = 0;
        ctxLines = 0;
        while (ctxLen < 100 && ctxEnd < source.length - 1) {
            ctxEnd++;
            ctxLen++;
            if (source[ctxEnd] == '\n') {
                if (++ctxLines == 3) {
                    break;
                }
            }
        }
        var context = source.substring(ctxStart, this.span.start.offset) + '[ERROR ->]' +
            source.substring(this.span.start.offset, ctxEnd + 1);
        return this.msg + " (\"" + context + "\"): " + this.span.start;
    };
    return ParseError;
}());
exports.ParseError = ParseError;
//# sourceMappingURL=parse_util.js.map