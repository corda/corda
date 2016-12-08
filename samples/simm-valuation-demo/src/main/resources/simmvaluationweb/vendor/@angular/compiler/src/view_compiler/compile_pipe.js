/**
 * @license
 * Copyright Google Inc. All Rights Reserved.
 *
 * Use of this source code is governed by an MIT-style license that can be
 * found in the LICENSE file at https://angular.io/license
 */
"use strict";
var exceptions_1 = require('../facade/exceptions');
var lang_1 = require('../facade/lang');
var identifiers_1 = require('../identifiers');
var o = require('../output/output_ast');
var util_1 = require('./util');
var _PurePipeProxy = (function () {
    function _PurePipeProxy(view, instance, argCount) {
        this.view = view;
        this.instance = instance;
        this.argCount = argCount;
    }
    return _PurePipeProxy;
}());
var CompilePipe = (function () {
    function CompilePipe(view, meta) {
        this.view = view;
        this.meta = meta;
        this._purePipeProxies = [];
        this.instance = o.THIS_EXPR.prop("_pipe_" + meta.name + "_" + view.pipeCount++);
    }
    CompilePipe.call = function (view, name, args) {
        var compView = view.componentView;
        var meta = _findPipeMeta(compView, name);
        var pipe;
        if (meta.pure) {
            // pure pipes live on the component view
            pipe = compView.purePipes.get(name);
            if (lang_1.isBlank(pipe)) {
                pipe = new CompilePipe(compView, meta);
                compView.purePipes.set(name, pipe);
                compView.pipes.push(pipe);
            }
        }
        else {
            // Non pure pipes live on the view that called it
            pipe = new CompilePipe(view, meta);
            view.pipes.push(pipe);
        }
        return pipe._call(view, args);
    };
    Object.defineProperty(CompilePipe.prototype, "pure", {
        get: function () { return this.meta.pure; },
        enumerable: true,
        configurable: true
    });
    CompilePipe.prototype.create = function () {
        var _this = this;
        var deps = this.meta.type.diDeps.map(function (diDep) {
            if (diDep.token.equalsTo(identifiers_1.identifierToken(identifiers_1.Identifiers.ChangeDetectorRef))) {
                return util_1.getPropertyInView(o.THIS_EXPR.prop('ref'), _this.view, _this.view.componentView);
            }
            return util_1.injectFromViewParentInjector(diDep.token, false);
        });
        this.view.fields.push(new o.ClassField(this.instance.name, o.importType(this.meta.type)));
        this.view.createMethod.resetDebugInfo(null, null);
        this.view.createMethod.addStmt(o.THIS_EXPR.prop(this.instance.name)
            .set(o.importExpr(this.meta.type).instantiate(deps))
            .toStmt());
        this._purePipeProxies.forEach(function (purePipeProxy) {
            var pipeInstanceSeenFromPureProxy = util_1.getPropertyInView(_this.instance, purePipeProxy.view, _this.view);
            util_1.createPureProxy(pipeInstanceSeenFromPureProxy.prop('transform')
                .callMethod(o.BuiltinMethod.bind, [pipeInstanceSeenFromPureProxy]), purePipeProxy.argCount, purePipeProxy.instance, purePipeProxy.view);
        });
    };
    CompilePipe.prototype._call = function (callingView, args) {
        if (this.meta.pure) {
            // PurePipeProxies live on the view that called them.
            var purePipeProxy = new _PurePipeProxy(callingView, o.THIS_EXPR.prop(this.instance.name + "_" + this._purePipeProxies.length), args.length);
            this._purePipeProxies.push(purePipeProxy);
            return o.importExpr(identifiers_1.Identifiers.castByValue)
                .callFn([
                purePipeProxy.instance,
                util_1.getPropertyInView(this.instance.prop('transform'), callingView, this.view)
            ])
                .callFn(args);
        }
        else {
            return util_1.getPropertyInView(this.instance, callingView, this.view).callMethod('transform', args);
        }
    };
    return CompilePipe;
}());
exports.CompilePipe = CompilePipe;
function _findPipeMeta(view, name) {
    var pipeMeta = null;
    for (var i = view.pipeMetas.length - 1; i >= 0; i--) {
        var localPipeMeta = view.pipeMetas[i];
        if (localPipeMeta.name == name) {
            pipeMeta = localPipeMeta;
            break;
        }
    }
    if (lang_1.isBlank(pipeMeta)) {
        throw new exceptions_1.BaseException("Illegal state: Could not find pipe " + name + " although the parser should have detected this error!");
    }
    return pipeMeta;
}
//# sourceMappingURL=compile_pipe.js.map