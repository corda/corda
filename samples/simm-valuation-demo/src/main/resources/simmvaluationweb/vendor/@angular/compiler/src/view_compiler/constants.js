/**
 * @license
 * Copyright Google Inc. All Rights Reserved.
 *
 * Use of this source code is governed by an MIT-style license that can be
 * found in the LICENSE file at https://angular.io/license
 */
"use strict";
var core_1 = require('@angular/core');
var core_private_1 = require('../../core_private');
var compile_metadata_1 = require('../compile_metadata');
var lang_1 = require('../facade/lang');
var identifiers_1 = require('../identifiers');
var o = require('../output/output_ast');
function _enumExpression(classIdentifier, value) {
    if (lang_1.isBlank(value))
        return o.NULL_EXPR;
    var name = lang_1.resolveEnumToken(classIdentifier.runtime, value);
    return o.importExpr(new compile_metadata_1.CompileIdentifierMetadata({
        name: classIdentifier.name + "." + name,
        moduleUrl: classIdentifier.moduleUrl,
        runtime: value
    }));
}
var ViewTypeEnum = (function () {
    function ViewTypeEnum() {
    }
    ViewTypeEnum.fromValue = function (value) {
        return _enumExpression(identifiers_1.Identifiers.ViewType, value);
    };
    ViewTypeEnum.HOST = ViewTypeEnum.fromValue(core_private_1.ViewType.HOST);
    ViewTypeEnum.COMPONENT = ViewTypeEnum.fromValue(core_private_1.ViewType.COMPONENT);
    ViewTypeEnum.EMBEDDED = ViewTypeEnum.fromValue(core_private_1.ViewType.EMBEDDED);
    return ViewTypeEnum;
}());
exports.ViewTypeEnum = ViewTypeEnum;
var ViewEncapsulationEnum = (function () {
    function ViewEncapsulationEnum() {
    }
    ViewEncapsulationEnum.fromValue = function (value) {
        return _enumExpression(identifiers_1.Identifiers.ViewEncapsulation, value);
    };
    ViewEncapsulationEnum.Emulated = ViewEncapsulationEnum.fromValue(core_1.ViewEncapsulation.Emulated);
    ViewEncapsulationEnum.Native = ViewEncapsulationEnum.fromValue(core_1.ViewEncapsulation.Native);
    ViewEncapsulationEnum.None = ViewEncapsulationEnum.fromValue(core_1.ViewEncapsulation.None);
    return ViewEncapsulationEnum;
}());
exports.ViewEncapsulationEnum = ViewEncapsulationEnum;
var ChangeDetectionStrategyEnum = (function () {
    function ChangeDetectionStrategyEnum() {
    }
    ChangeDetectionStrategyEnum.fromValue = function (value) {
        return _enumExpression(identifiers_1.Identifiers.ChangeDetectionStrategy, value);
    };
    ChangeDetectionStrategyEnum.OnPush = ChangeDetectionStrategyEnum.fromValue(core_1.ChangeDetectionStrategy.OnPush);
    ChangeDetectionStrategyEnum.Default = ChangeDetectionStrategyEnum.fromValue(core_1.ChangeDetectionStrategy.Default);
    return ChangeDetectionStrategyEnum;
}());
exports.ChangeDetectionStrategyEnum = ChangeDetectionStrategyEnum;
var ChangeDetectorStatusEnum = (function () {
    function ChangeDetectorStatusEnum() {
    }
    ChangeDetectorStatusEnum.fromValue = function (value) {
        return _enumExpression(identifiers_1.Identifiers.ChangeDetectorStatus, value);
    };
    ChangeDetectorStatusEnum.CheckOnce = ChangeDetectorStatusEnum.fromValue(core_private_1.ChangeDetectorStatus.CheckOnce);
    ChangeDetectorStatusEnum.Checked = ChangeDetectorStatusEnum.fromValue(core_private_1.ChangeDetectorStatus.Checked);
    ChangeDetectorStatusEnum.CheckAlways = ChangeDetectorStatusEnum.fromValue(core_private_1.ChangeDetectorStatus.CheckAlways);
    ChangeDetectorStatusEnum.Detached = ChangeDetectorStatusEnum.fromValue(core_private_1.ChangeDetectorStatus.Detached);
    ChangeDetectorStatusEnum.Errored = ChangeDetectorStatusEnum.fromValue(core_private_1.ChangeDetectorStatus.Errored);
    ChangeDetectorStatusEnum.Destroyed = ChangeDetectorStatusEnum.fromValue(core_private_1.ChangeDetectorStatus.Destroyed);
    return ChangeDetectorStatusEnum;
}());
exports.ChangeDetectorStatusEnum = ChangeDetectorStatusEnum;
var ViewConstructorVars = (function () {
    function ViewConstructorVars() {
    }
    ViewConstructorVars.viewUtils = o.variable('viewUtils');
    ViewConstructorVars.parentInjector = o.variable('parentInjector');
    ViewConstructorVars.declarationEl = o.variable('declarationEl');
    return ViewConstructorVars;
}());
exports.ViewConstructorVars = ViewConstructorVars;
var ViewProperties = (function () {
    function ViewProperties() {
    }
    ViewProperties.renderer = o.THIS_EXPR.prop('renderer');
    ViewProperties.projectableNodes = o.THIS_EXPR.prop('projectableNodes');
    ViewProperties.viewUtils = o.THIS_EXPR.prop('viewUtils');
    return ViewProperties;
}());
exports.ViewProperties = ViewProperties;
var EventHandlerVars = (function () {
    function EventHandlerVars() {
    }
    EventHandlerVars.event = o.variable('$event');
    return EventHandlerVars;
}());
exports.EventHandlerVars = EventHandlerVars;
var InjectMethodVars = (function () {
    function InjectMethodVars() {
    }
    InjectMethodVars.token = o.variable('token');
    InjectMethodVars.requestNodeIndex = o.variable('requestNodeIndex');
    InjectMethodVars.notFoundResult = o.variable('notFoundResult');
    return InjectMethodVars;
}());
exports.InjectMethodVars = InjectMethodVars;
var DetectChangesVars = (function () {
    function DetectChangesVars() {
    }
    DetectChangesVars.throwOnChange = o.variable("throwOnChange");
    DetectChangesVars.changes = o.variable("changes");
    DetectChangesVars.changed = o.variable("changed");
    DetectChangesVars.valUnwrapper = o.variable("valUnwrapper");
    return DetectChangesVars;
}());
exports.DetectChangesVars = DetectChangesVars;
//# sourceMappingURL=constants.js.map