/**
 * @license
 * Copyright Google Inc. All Rights Reserved.
 *
 * Use of this source code is governed by an MIT-style license that can be
 * found in the LICENSE file at https://angular.io/license
 */
import { ComponentMetadata, DirectiveMetadata, HostBindingMetadata, HostListenerMetadata, Injectable, InputMetadata, OutputMetadata, QueryMetadata, resolveForwardRef } from '@angular/core';
import { ReflectorReader, reflector } from '../core_private';
import { ListWrapper, StringMapWrapper } from '../src/facade/collection';
import { BaseException } from '../src/facade/exceptions';
import { isPresent, stringify } from '../src/facade/lang';
function _isDirectiveMetadata(type) {
    return type instanceof DirectiveMetadata;
}
export class DirectiveResolver {
    constructor(_reflector = reflector) {
        this._reflector = _reflector;
    }
    /**
     * Return {@link DirectiveMetadata} for a given `Type`.
     */
    resolve(type) {
        var typeMetadata = this._reflector.annotations(resolveForwardRef(type));
        if (isPresent(typeMetadata)) {
            var metadata = typeMetadata.find(_isDirectiveMetadata);
            if (isPresent(metadata)) {
                var propertyMetadata = this._reflector.propMetadata(type);
                return this._mergeWithPropertyMetadata(metadata, propertyMetadata, type);
            }
        }
        throw new BaseException(`No Directive annotation found on ${stringify(type)}`);
    }
    _mergeWithPropertyMetadata(dm, propertyMetadata, directiveType) {
        var inputs = [];
        var outputs = [];
        var host = {};
        var queries = {};
        StringMapWrapper.forEach(propertyMetadata, (metadata, propName) => {
            metadata.forEach(a => {
                if (a instanceof InputMetadata) {
                    if (isPresent(a.bindingPropertyName)) {
                        inputs.push(`${propName}: ${a.bindingPropertyName}`);
                    }
                    else {
                        inputs.push(propName);
                    }
                }
                if (a instanceof OutputMetadata) {
                    if (isPresent(a.bindingPropertyName)) {
                        outputs.push(`${propName}: ${a.bindingPropertyName}`);
                    }
                    else {
                        outputs.push(propName);
                    }
                }
                if (a instanceof HostBindingMetadata) {
                    if (isPresent(a.hostPropertyName)) {
                        host[`[${a.hostPropertyName}]`] = propName;
                    }
                    else {
                        host[`[${propName}]`] = propName;
                    }
                }
                if (a instanceof HostListenerMetadata) {
                    var args = isPresent(a.args) ? a.args.join(', ') : '';
                    host[`(${a.eventName})`] = `${propName}(${args})`;
                }
                if (a instanceof QueryMetadata) {
                    queries[propName] = a;
                }
            });
        });
        return this._merge(dm, inputs, outputs, host, queries, directiveType);
    }
    _merge(dm, inputs, outputs, host, queries, directiveType) {
        var mergedInputs = isPresent(dm.inputs) ? ListWrapper.concat(dm.inputs, inputs) : inputs;
        var mergedOutputs;
        if (isPresent(dm.outputs)) {
            dm.outputs.forEach((propName) => {
                if (ListWrapper.contains(outputs, propName)) {
                    throw new BaseException(`Output event '${propName}' defined multiple times in '${stringify(directiveType)}'`);
                }
            });
            mergedOutputs = ListWrapper.concat(dm.outputs, outputs);
        }
        else {
            mergedOutputs = outputs;
        }
        var mergedHost = isPresent(dm.host) ? StringMapWrapper.merge(dm.host, host) : host;
        var mergedQueries = isPresent(dm.queries) ? StringMapWrapper.merge(dm.queries, queries) : queries;
        if (dm instanceof ComponentMetadata) {
            return new ComponentMetadata({
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
            return new DirectiveMetadata({
                selector: dm.selector,
                inputs: mergedInputs,
                outputs: mergedOutputs,
                host: mergedHost,
                exportAs: dm.exportAs,
                queries: mergedQueries,
                providers: dm.providers
            });
        }
    }
}
/** @nocollapse */
DirectiveResolver.decorators = [
    { type: Injectable },
];
/** @nocollapse */
DirectiveResolver.ctorParameters = [
    { type: ReflectorReader, },
];
export var CODEGEN_DIRECTIVE_RESOLVER = new DirectiveResolver(reflector);
//# sourceMappingURL=directive_resolver.js.map