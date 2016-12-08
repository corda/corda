/**
 * @license
 * Copyright Google Inc. All Rights Reserved.
 *
 * Use of this source code is governed by an MIT-style license that can be
 * found in the LICENSE file at https://angular.io/license
 */
"use strict";
var core_1 = require('@angular/core');
var router_1 = require('../router');
var url_tree_1 = require('../url_tree');
var router_link_1 = require('./router_link');
var RouterLinkActive = (function () {
    function RouterLinkActive(router, element, renderer) {
        var _this = this;
        this.router = router;
        this.element = element;
        this.renderer = renderer;
        this.classes = [];
        this.routerLinkActiveOptions = { exact: false };
        this.subscription = router.events.subscribe(function (s) {
            if (s instanceof router_1.NavigationEnd) {
                _this.update();
            }
        });
    }
    RouterLinkActive.prototype.ngAfterContentInit = function () {
        var _this = this;
        this.links.changes.subscribe(function (s) { return _this.update(); });
        this.linksWithHrefs.changes.subscribe(function (s) { return _this.update(); });
        this.update();
    };
    Object.defineProperty(RouterLinkActive.prototype, "routerLinkActive", {
        set: function (data) {
            if (Array.isArray(data)) {
                this.classes = data;
            }
            else {
                this.classes = data.split(' ');
            }
        },
        enumerable: true,
        configurable: true
    });
    RouterLinkActive.prototype.ngOnChanges = function (changes) { this.update(); };
    RouterLinkActive.prototype.ngOnDestroy = function () { this.subscription.unsubscribe(); };
    RouterLinkActive.prototype.update = function () {
        var _this = this;
        if (!this.links || !this.linksWithHrefs)
            return;
        var currentUrlTree = this.router.parseUrl(this.router.url);
        var isActiveLinks = this.reduceList(currentUrlTree, this.links);
        var isActiveLinksWithHrefs = this.reduceList(currentUrlTree, this.linksWithHrefs);
        this.classes.forEach(function (c) { return _this.renderer.setElementClass(_this.element.nativeElement, c, isActiveLinks || isActiveLinksWithHrefs); });
    };
    RouterLinkActive.prototype.reduceList = function (currentUrlTree, q) {
        var _this = this;
        return q.reduce(function (res, link) {
            return res || url_tree_1.containsTree(currentUrlTree, link.urlTree, _this.routerLinkActiveOptions.exact);
        }, false);
    };
    /** @nocollapse */
    RouterLinkActive.decorators = [
        { type: core_1.Directive, args: [{ selector: '[routerLinkActive]' },] },
    ];
    /** @nocollapse */
    RouterLinkActive.ctorParameters = [
        { type: router_1.Router, },
        { type: core_1.ElementRef, },
        { type: core_1.Renderer, },
    ];
    /** @nocollapse */
    RouterLinkActive.propDecorators = {
        'links': [{ type: core_1.ContentChildren, args: [router_link_1.RouterLink,] },],
        'linksWithHrefs': [{ type: core_1.ContentChildren, args: [router_link_1.RouterLinkWithHref,] },],
        'routerLinkActiveOptions': [{ type: core_1.Input },],
        'routerLinkActive': [{ type: core_1.Input },],
    };
    return RouterLinkActive;
}());
exports.RouterLinkActive = RouterLinkActive;
//# sourceMappingURL=router_link_active.js.map