/**
 * @license
 * Copyright Google Inc. All Rights Reserved.
 *
 * Use of this source code is governed by an MIT-style license that can be
 * found in the LICENSE file at https://angular.io/license
 */
import { ChangeDetectionStrategy, ViewEncapsulation } from '@angular/core';
import { ChangeDetectorStatus, ViewType } from '../../core_private';
import { CompileIdentifierMetadata } from '../compile_metadata';
import { isBlank, resolveEnumToken } from '../facade/lang';
import { Identifiers } from '../identifiers';
import * as o from '../output/output_ast';
function _enumExpression(classIdentifier, value) {
    if (isBlank(value))
        return o.NULL_EXPR;
    var name = resolveEnumToken(classIdentifier.runtime, value);
    return o.importExpr(new CompileIdentifierMetadata({
        name: `${classIdentifier.name}.${name}`,
        moduleUrl: classIdentifier.moduleUrl,
        runtime: value
    }));
}
export class ViewTypeEnum {
    static fromValue(value) {
        return _enumExpression(Identifiers.ViewType, value);
    }
}
ViewTypeEnum.HOST = ViewTypeEnum.fromValue(ViewType.HOST);
ViewTypeEnum.COMPONENT = ViewTypeEnum.fromValue(ViewType.COMPONENT);
ViewTypeEnum.EMBEDDED = ViewTypeEnum.fromValue(ViewType.EMBEDDED);
export class ViewEncapsulationEnum {
    static fromValue(value) {
        return _enumExpression(Identifiers.ViewEncapsulation, value);
    }
}
ViewEncapsulationEnum.Emulated = ViewEncapsulationEnum.fromValue(ViewEncapsulation.Emulated);
ViewEncapsulationEnum.Native = ViewEncapsulationEnum.fromValue(ViewEncapsulation.Native);
ViewEncapsulationEnum.None = ViewEncapsulationEnum.fromValue(ViewEncapsulation.None);
export class ChangeDetectionStrategyEnum {
    static fromValue(value) {
        return _enumExpression(Identifiers.ChangeDetectionStrategy, value);
    }
}
ChangeDetectionStrategyEnum.OnPush = ChangeDetectionStrategyEnum.fromValue(ChangeDetectionStrategy.OnPush);
ChangeDetectionStrategyEnum.Default = ChangeDetectionStrategyEnum.fromValue(ChangeDetectionStrategy.Default);
export class ChangeDetectorStatusEnum {
    static fromValue(value) {
        return _enumExpression(Identifiers.ChangeDetectorStatus, value);
    }
}
ChangeDetectorStatusEnum.CheckOnce = ChangeDetectorStatusEnum.fromValue(ChangeDetectorStatus.CheckOnce);
ChangeDetectorStatusEnum.Checked = ChangeDetectorStatusEnum.fromValue(ChangeDetectorStatus.Checked);
ChangeDetectorStatusEnum.CheckAlways = ChangeDetectorStatusEnum.fromValue(ChangeDetectorStatus.CheckAlways);
ChangeDetectorStatusEnum.Detached = ChangeDetectorStatusEnum.fromValue(ChangeDetectorStatus.Detached);
ChangeDetectorStatusEnum.Errored = ChangeDetectorStatusEnum.fromValue(ChangeDetectorStatus.Errored);
ChangeDetectorStatusEnum.Destroyed = ChangeDetectorStatusEnum.fromValue(ChangeDetectorStatus.Destroyed);
export class ViewConstructorVars {
}
ViewConstructorVars.viewUtils = o.variable('viewUtils');
ViewConstructorVars.parentInjector = o.variable('parentInjector');
ViewConstructorVars.declarationEl = o.variable('declarationEl');
export class ViewProperties {
}
ViewProperties.renderer = o.THIS_EXPR.prop('renderer');
ViewProperties.projectableNodes = o.THIS_EXPR.prop('projectableNodes');
ViewProperties.viewUtils = o.THIS_EXPR.prop('viewUtils');
export class EventHandlerVars {
}
EventHandlerVars.event = o.variable('$event');
export class InjectMethodVars {
}
InjectMethodVars.token = o.variable('token');
InjectMethodVars.requestNodeIndex = o.variable('requestNodeIndex');
InjectMethodVars.notFoundResult = o.variable('notFoundResult');
export class DetectChangesVars {
}
DetectChangesVars.throwOnChange = o.variable(`throwOnChange`);
DetectChangesVars.changes = o.variable(`changes`);
DetectChangesVars.changed = o.variable(`changed`);
DetectChangesVars.valUnwrapper = o.variable(`valUnwrapper`);
//# sourceMappingURL=constants.js.map