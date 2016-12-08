/**
 * @license
 * Copyright Google Inc. All Rights Reserved.
 *
 * Use of this source code is governed by an MIT-style license that can be
 * found in the LICENSE file at https://angular.io/license
 */
"use strict";
var core_1 = require('@angular/core');
var core_private_1 = require('../core_private');
var collection_1 = require('../src/facade/collection');
var exceptions_1 = require('../src/facade/exceptions');
var lang_1 = require('../src/facade/lang');
function _isDirectiveMetadata(type) {
    return type instanceof core_1.DirectiveMetadata;
}
var DirectiveResolver = (function () {
    function DirectiveResolver(_reflector) {
        if (_reflector === void 0) { _reflector = core_private_1.reflector; }
        this._reflector = _reflector;
    }
    /**
     * Return {@link DirectiveMetadata} for a given `Type`.
     */
    DirectiveResolver.prototype.resolve = function (type) {
        var typeMetadata = this._reflector.annotations(core_1.resolveForwardRef(type));
        if (lang_1.isPresent(typeMetadata)) {
            var metadata = typeMetadata.find(_isDirectiveMetadata);
            if (lang_1.isPresent(metadata)) {
                var propertyMetadata = this._reflector.propMetadata(type);
                return this._mergeWithPropertyMetadata(metadata, propertyMetadata, type);
            }
        }
        throw new exceptions_1.BaseException("No Directive annotation found on " + lang_1.stringify(type));
    };
    DirectiveResolver.prototype._mergeWithPropertyMetadata = function (dm, propertyMetadata, directiveType) {
        var inputs = [];
        var outputs = [];
        var host = {};
        var queries = {};
        collection_1.StringMapWrapper.forEach(propertyMetadata, function (metadata, propName) {
            metadata.forEach(function (a) {
                if (a instanceof core_1.InputMetadata) {
                    if (lang_1.isPresent(a.bindingPropertyName)) {
                        inputs.push(propName + ": " + a.bindingPropertyName);
                    }
                    else {
                        inputs.push(propName);
                    }
                }
                if (a instanceof core_1.OutputMetadata) {
                    if (lang_1.isPresent(a.bindingPropertyName)) {
                        outputs.push(propName + ": " + a.bindingPropertyName);
                    }
                    else {
                        outputs.push(propName);
                    }
                }
                if (a instanceof core_1.HostBindingMetadata) {
                    if (lang_1.isPresent(a.hostPropertyName)) {
                        host[("[" + a.hostPropertyName + "]")] = propName;
                    }
                    else {
                        host[("[" + propName + "]")] = propName;
                    }
                }
                if (a instanceof core_1.HostListenerMetadata) {
                    var args = lang_1.isPresent(a.args) ? a.args.join(', ') : '';
                    host[("(" + a.eventName + ")")] = propName + "(" + args + ")";
                }
                if (a instanceof core_1.QueryMetadata) {
                    queries[propName] = a;
                }
            });
        });
        return this._merge(dm, inputs, outputs, host, queries, directiveType);
    };
    DirectiveResolver.prototype._merge = function (dm, inputs, outputs, host, queries, directiveType) {
        var mergedInputs = lang_1.isPresent(dm.inputs) ? collection_1.ListWrapper.concat(dm.inputs, inputs) : inputs;
        var mergedOutputs;
        if (lang_1.isPresent(dm.outputs)) {
            dm.outputs.forEach(function (propName) {
                if (collection_1.ListWrapper.contains(outputs, propName)) {
                    throw new exceptions_1.BaseException("Output event '" + propName + "' defined multiple times in '" + lang_1.stringify(directiveType) + "'");
                }
            });
            mergedOutputs = collection_1.ListWrapper.concat(dm.outputs, outputs);
        }
        else {
            mergedOutputs = outputs;
        }
        var mergedHost = lang_1.isPresent(dm.host) ? collection_1.StringMapWrapper.merge(dm.host, host) : host;
        var mergedQueries = lang_1.isPresent(dm.queries) ? collection_1.StringMapWrapper.merge(dm.queries, queries) : queries;
        if (dm instanceof core_1.ComponentMetadata) {
            return new core_1.ComponentMetadata({
                selector: dm.selector,
                inputs: mergedInputs,
                outputs: mergedOutputs,
                host: mergedHost,
                exportAs: dm.exportAs,
                moduleId: dm.moduleId,
                queries: mergedQueries,
                changeDetection: dm.changeDetection,
                providers: dm.providers,
                viewProviders: dm.viewProviders,
                precompile: dm.precompile
            });
        }
        else {
            return new core_1.DirectiveMetadata({
                selector: dm.selector,
                inputs: mergedInputs,
                outputs: mergedOutputs,
                host: mergedHost,
                exportAs: dm.exportAs,
                queries: mergedQueries,
                providers: dm.providers
            });
        }
    };
    /** @nocollapse */
    DirectiveResolver.decorators = [
        { type: core_1.Injectable },
    ];
    /** @nocollapse */
    DirectiveResolver.ctorParameters = [
        { type: core_private_1.ReflectorReader, },
    ];
    return DirectiveResolver;
}());
exports.DirectiveResolver = DirectiveResolver;
exports.CODEGEN_DIRECTIVE_RESOLVER = new DirectiveResolver(core_private_1.reflector);
//# sourceMappingURL=directive_resolver.js.map