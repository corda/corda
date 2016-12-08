/**
 * @license
 * Copyright Google Inc. All Rights Reserved.
 *
 * Use of this source code is governed by an MIT-style license that can be
 * found in the LICENSE file at https://angular.io/license
 */
import { Injectable, ViewEncapsulation } from '@angular/core';
import { CompileIdentifierMetadata, CompileStylesheetMetadata } from './compile_metadata';
import * as o from './output/output_ast';
import { ShadowCss } from './shadow_css';
import { UrlResolver } from './url_resolver';
const COMPONENT_VARIABLE = '%COMP%';
const HOST_ATTR = `_nghost-${COMPONENT_VARIABLE}`;
const CONTENT_ATTR = `_ngcontent-${COMPONENT_VARIABLE}`;
export class StylesCompileDependency {
    constructor(moduleUrl, isShimmed, valuePlaceholder) {
        this.moduleUrl = moduleUrl;
        this.isShimmed = isShimmed;
        this.valuePlaceholder = valuePlaceholder;
    }
}
export class StylesCompileResult {
    constructor(componentStylesheet, externalStylesheets) {
        this.componentStylesheet = componentStylesheet;
        this.externalStylesheets = externalStylesheets;
    }
}
export class CompiledStylesheet {
    constructor(statements, stylesVar, dependencies, isShimmed, meta) {
        this.statements = statements;
        this.stylesVar = stylesVar;
        this.dependencies = dependencies;
        this.isShimmed = isShimmed;
        this.meta = meta;
    }
}
export class StyleCompiler {
    constructor(_urlResolver) {
        this._urlResolver = _urlResolver;
        this._shadowCss = new ShadowCss();
    }
    compileComponent(comp) {
        var shim = comp.template.encapsulation === ViewEncapsulation.Emulated;
        var externalStylesheets = [];
        var componentStylesheet = this._compileStyles(comp, new CompileStylesheetMetadata({
            styles: comp.template.styles,
            styleUrls: comp.template.styleUrls,
            moduleUrl: comp.type.moduleUrl
        }), true);
        comp.template.externalStylesheets.forEach((stylesheetMeta) => {
            var compiledStylesheet = this._compileStyles(comp, stylesheetMeta, false);
            externalStylesheets.push(compiledStylesheet);
        });
        return new StylesCompileResult(componentStylesheet, externalStylesheets);
    }
    _compileStyles(comp, stylesheet, isComponentStylesheet) {
        var shim = comp.template.encapsulation === ViewEncapsulation.Emulated;
        var styleExpressions = stylesheet.styles.map(plainStyle => o.literal(this._shimIfNeeded(plainStyle, shim)));
        var dependencies = [];
        for (var i = 0; i < stylesheet.styleUrls.length; i++) {
            var identifier = new CompileIdentifierMetadata({ name: getStylesVarName(null) });
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
    }
    _shimIfNeeded(style, shim) {
        return shim ? this._shadowCss.shimCssText(style, CONTENT_ATTR, HOST_ATTR) : style;
    }
}
/** @nocollapse */
StyleCompiler.decorators = [
    { type: Injectable },
];
/** @nocollapse */
StyleCompiler.ctorParameters = [
    { type: UrlResolver, },
];
function getStylesVarName(component) {
    var result = `styles`;
    if (component) {
        result += `_${component.type.name}`;
    }
    return result;
}
//# sourceMappingURL=style_compiler.js.map