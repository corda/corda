/**
 * @license
 * Copyright Google Inc. All Rights Reserved.
 *
 * Use of this source code is governed by an MIT-style license that can be
 * found in the LICENSE file at https://angular.io/license
 */
import { Injectable } from '@angular/core';
import { AnimationCompiler } from '../animation/animation_compiler';
import { CompilerConfig } from '../config';
import { CompileElement } from './compile_element';
import { CompileView } from './compile_view';
import { bindView } from './view_binder';
import { buildView, finishView } from './view_builder';
export { ComponentFactoryDependency, ViewFactoryDependency } from './view_builder';
export class ViewCompileResult {
    constructor(statements, viewFactoryVar, dependencies) {
        this.statements = statements;
        this.viewFactoryVar = viewFactoryVar;
        this.dependencies = dependencies;
    }
}
export class ViewCompiler {
    constructor(_genConfig) {
        this._genConfig = _genConfig;
        this._animationCompiler = new AnimationCompiler();
    }
    compileComponent(component, template, styles, pipes) {
        var dependencies = [];
        var compiledAnimations = this._animationCompiler.compileComponent(component);
        var statements = [];
        compiledAnimations.map(entry => {
            statements.push(entry.statesMapStatement);
            statements.push(entry.fnStatement);
        });
        var view = new CompileView(component, this._genConfig, pipes, styles, compiledAnimations, 0, CompileElement.createNull(), []);
        buildView(view, template, dependencies);
        // Need to separate binding from creation to be able to refer to
        // variables that have been declared after usage.
        bindView(view, template);
        finishView(view, statements);
        return new ViewCompileResult(statements, view.viewFactory.name, dependencies);
    }
}
/** @nocollapse */
ViewCompiler.decorators = [
    { type: Injectable },
];
/** @nocollapse */
ViewCompiler.ctorParameters = [
    { type: CompilerConfig, },
];
//# sourceMappingURL=view_compiler.js.map