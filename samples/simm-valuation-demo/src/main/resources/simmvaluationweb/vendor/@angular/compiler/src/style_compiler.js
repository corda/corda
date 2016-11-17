/**
 * @license
 * Copyright Google Inc. All Rights Reserved.
 *
 * Use of this source code is governed by an MIT-style license that can be
 * found in the LICENSE file at https://angular.io/license
 */
"use strict";
var core_1 = require('@angular/core');
var compile_metadata_1 = require('./compile_metadata');
var o = require('./output/output_ast');
var shadow_css_1 = require('./shadow_css');
var url_resolver_1 = require('./url_resolver');
var COMPONENT_VARIABLE = '%COMP%';
var HOST_ATTR = "_nghost-" + COMPONENT_VARIABLE;
var CONTENT_ATTR = "_ngcontent-" + COMPONENT_VARIABLE;
var StylesCompileDependency = (function () {
    function StylesCompileDependency(moduleUrl, isShimmed, valuePlaceholder) {
        this.moduleUrl = moduleUrl;
        this.isShimmed = isShimmed;
        this.valuePlaceholder = valuePlaceholder;
    }
    return StylesCompileDependency;
}());
exports.StylesCompileDependency = StylesCompileDependency;
var StylesCompileResult = (function () {
    function StylesCompileResult(componentStylesheet, externalStylesheets) {
        this.componentStylesheet = componentStylesheet;
        this.externalStylesheets = externalStylesheets;
    }
    return StylesCompileResult;
}());
exports.StylesCompileResult = StylesCompileResult;
var CompiledStylesheet = (function () {
    function CompiledStylesheet(statements, stylesVar, dependencies, isShimmed, meta) {
        this.statements = statements;
        this.stylesVar = stylesVar;
        this.dependencies = dependencies;
        this.isShimmed = isShimmed;
        this.meta = meta;
    }
    return CompiledStylesheet;
}());
exports.CompiledStylesheet = CompiledStylesheet;
var StyleCompiler = (function () {
    function StyleCompiler(_urlResolver) {
        this._urlResolver = _urlResolver;
        this._shadowCss = new shadow_css_1.ShadowCss();
    }
    StyleCompiler.prototype.compileComponent = function (comp) {
        var _this = this;
        var shim = comp.template.encapsulation === core_1.ViewEncapsulation.Emulated;
        var externalStylesheets = [];
        var componentStylesheet = this._compileStyles(comp, new compile_metadata_1.CompileStylesheetMetadata({
            styles: comp.template.styles,
            styleUrls: comp.template.styleUrls,
            moduleUrl: comp.type.moduleUrl
        }), true);
        comp.template.externalStylesheets.forEach(function (stylesheetMeta) {
            var compiledStylesheet = _this._compileStyles(comp, stylesheetMeta, false);
            externalStylesheets.push(compiledStylesheet);
        });
        return new StylesCompileResult(componentStylesheet, externalStylesheets);
    };
    StyleCompiler.prototype._compileStyles = function (comp, stylesheet, isComponentStylesheet) {
        var _this = this;
        var shim = comp.template.encapsulation === core_1.ViewEncapsulation.Emulated;
        var styleExpressions = stylesheet.styles.map(function (plainStyle) { return o.literal(_this._shimIfNeeded(plainStyle, shim)); });
        var dependencies = [];
        for (var i = 0; i < stylesheet.styleUrls.length; i++) {
            var identifier = new compile_metadata_1.CompileIdentifierMetadata({ name: getStylesVarName(null) });
            dependencies.push(new StylesCompileDependency(stylesheet.styleUrls[i], shim, identifier));
            styleExpressions.push(new o.ExternalExpr(identifier));
        }
        // styles variable contains plain strings and arrays of other styles arrays (recursive),
        // so we set its type to dynamic.
        var stylesVar = getStylesVarName(isComponentStylesheet ? comp : null);
        var stmt = o.variable(stylesVar)
            .set(o.literalArr(styleExpressions, new o.ArrayType(o.DYNAMIC_TYPE, [o.TypeModifier.Const])))
            .toDeclStmt(null, [o.StmtModifier.Final]);
        return new CompiledStylesheet([stmt], stylesVar, dependencies, shim, stylesheet);
    };
    StyleCompiler.prototype._shimIfNeeded = function (style, shim) {
        return shim ? this._shadowCss.shimCssText(style, CONTENT_ATTR, HOST_ATTR) : style;
    };
    /** @nocollapse */
    StyleCompiler.decorators = [
        { type: core_1.Injectable },
    ];
    /** @nocollapse */
    StyleCompiler.ctorParameters = [
        { type: url_resolver_1.UrlResolver, },
    ];
    return StyleCompiler;
}());
exports.StyleCompiler = StyleCompiler;
function getStylesVarName(component) {
    var result = "styles";
    if (component) {
        result += "_" + component.type.name;
    }
    return result;
}
//# sourceMappingURL=style_compiler.js.map