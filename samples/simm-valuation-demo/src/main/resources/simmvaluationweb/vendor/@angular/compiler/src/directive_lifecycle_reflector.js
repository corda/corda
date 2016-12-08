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
var LIFECYCLE_INTERFACES = collection_1.MapWrapper.createFromPairs([
    [core_private_1.LifecycleHooks.OnInit, core_1.OnInit],
    [core_private_1.LifecycleHooks.OnDestroy, core_1.OnDestroy],
    [core_private_1.LifecycleHooks.DoCheck, core_1.DoCheck],
    [core_private_1.LifecycleHooks.OnChanges, core_1.OnChanges],
    [core_private_1.LifecycleHooks.AfterContentInit, core_1.AfterContentInit],
    [core_private_1.LifecycleHooks.AfterContentChecked, core_1.AfterContentChecked],
    [core_private_1.LifecycleHooks.AfterViewInit, core_1.AfterViewInit],
    [core_private_1.LifecycleHooks.AfterViewChecked, core_1.AfterViewChecked],
]);
var LIFECYCLE_PROPS = collection_1.MapWrapper.createFromPairs([
    [core_private_1.LifecycleHooks.OnInit, 'ngOnInit'],
    [core_private_1.LifecycleHooks.OnDestroy, 'ngOnDestroy'],
    [core_private_1.LifecycleHooks.DoCheck, 'ngDoCheck'],
    [core_private_1.LifecycleHooks.OnChanges, 'ngOnChanges'],
    [core_private_1.LifecycleHooks.AfterContentInit, 'ngAfterContentInit'],
    [core_private_1.LifecycleHooks.AfterContentChecked, 'ngAfterContentChecked'],
    [core_private_1.LifecycleHooks.AfterViewInit, 'ngAfterViewInit'],
    [core_private_1.LifecycleHooks.AfterViewChecked, 'ngAfterViewChecked'],
]);
function hasLifecycleHook(hook, token) {
    var lcInterface = LIFECYCLE_INTERFACES.get(hook);
    var lcProp = LIFECYCLE_PROPS.get(hook);
    return core_private_1.reflector.hasLifecycleHook(token, lcInterface, lcProp);
}
exports.hasLifecycleHook = hasLifecycleHook;
//# sourceMappingURL=directive_lifecycle_reflector.js.map