/**
 * @license
 * Copyright Google Inc. All Rights Reserved.
 *
 * Use of this source code is governed by an MIT-style license that can be
 * found in the LICENSE file at https://angular.io/license
 */
"use strict";
var ng_proble = require('./src/dom/debug/ng_probe');
var dom_adapter = require('./src/dom/dom_adapter');
var dom_renderer = require('./src/dom/dom_renderer');
var dom_events = require('./src/dom/events/dom_events');
var shared_styles_host = require('./src/dom/shared_styles_host');
exports.__platform_browser_private__ = {
    DomAdapter: dom_adapter.DomAdapter,
    getDOM: dom_adapter.getDOM,
    setRootDomAdapter: dom_adapter.setRootDomAdapter,
    DomRootRenderer: dom_renderer.DomRootRenderer,
    DomRootRenderer_: dom_renderer.DomRootRenderer_,
    DomSharedStylesHost: shared_styles_host.DomSharedStylesHost,
    SharedStylesHost: shared_styles_host.SharedStylesHost,
    ELEMENT_PROBE_PROVIDERS: ng_proble.ELEMENT_PROBE_PROVIDERS,
    DomEventsPlugin: dom_events.DomEventsPlugin
};
//# sourceMappingURL=private_export.js.map