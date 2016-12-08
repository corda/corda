/**
 * @license
 * Copyright Google Inc. All Rights Reserved.
 *
 * Use of this source code is governed by an MIT-style license that can be
 * found in the LICENSE file at https://angular.io/license
 */
import { ViewEncapsulation, isDevMode } from '@angular/core';
import { unimplemented } from '../src/facade/exceptions';
import { Identifiers } from './identifiers';
export class CompilerConfig {
    constructor({ renderTypes = new DefaultRenderTypes(), defaultEncapsulation = ViewEncapsulation.Emulated, genDebugInfo, logBindingUpdate, useJit = true, platformDirectives = [], platformPipes = [] } = {}) {
        this.renderTypes = renderTypes;
        this.defaultEncapsulation = defaultEncapsulation;
        this._genDebugInfo = genDebugInfo;
        this._logBindingUpdate = logBindingUpdate;
        this.useJit = useJit;
        this.platformDirectives = platformDirectives;
        this.platformPipes = platformPipes;
    }
    get genDebugInfo() {
        return this._genDebugInfo === void 0 ? isDevMode() : this._genDebugInfo;
    }
    get logBindingUpdate() {
        return this._logBindingUpdate === void 0 ? isDevMode() : this._logBindingUpdate;
    }
}
/**
 * Types used for the renderer.
 * Can be replaced to specialize the generated output to a specific renderer
 * to help tree shaking.
 */
export class RenderTypes {
    get renderer() { return unimplemented(); }
    get renderText() { return unimplemented(); }
    get renderElement() { return unimplemented(); }
    get renderComment() { return unimplemented(); }
    get renderNode() { return unimplemented(); }
    get renderEvent() { return unimplemented(); }
}
export class DefaultRenderTypes {
    constructor() {
        this.renderer = Identifiers.Renderer;
        this.renderText = null;
        this.renderElement = null;
        this.renderComment = null;
        this.renderNode = null;
        this.renderEvent = null;
    }
}
//# sourceMappingURL=config.js.map