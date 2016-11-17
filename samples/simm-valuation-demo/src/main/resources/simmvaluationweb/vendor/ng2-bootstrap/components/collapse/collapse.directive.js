"use strict";
var __decorate = (this && this.__decorate) || function (decorators, target, key, desc) {
    var c = arguments.length, r = c < 3 ? target : desc === null ? desc = Object.getOwnPropertyDescriptor(target, key) : desc, d;
    if (typeof Reflect === "object" && typeof Reflect.decorate === "function") r = Reflect.decorate(decorators, target, key, desc);
    else for (var i = decorators.length - 1; i >= 0; i--) if (d = decorators[i]) r = (c < 3 ? d(r) : c > 3 ? d(target, key, r) : d(target, key)) || r;
    return c > 3 && r && Object.defineProperty(target, key, r), r;
};
var __metadata = (this && this.__metadata) || function (k, v) {
    if (typeof Reflect === "object" && typeof Reflect.metadata === "function") return Reflect.metadata(k, v);
};
// FIX: in order to update to rc.1 had to disable animation, sorry
var core_1 = require('@angular/core');
// import {AnimationBuilder} from '@angular/platform-browser/src/animate/animation_builder';
// import {animation, style, animate, state, transition} from '@angular/core';
/*@Directive({
  selector: '[collapse]',
  // templateUrl: 'app/panel.html',
  // styleUrls: ['app/panel.css'],
  animations: [
    animation('active', [
      state('void', style({ height: 0 })),
      state('closed', style({ height: 0 })),
      state('open', style({ height: '*' })),
      transition('void => closed', [ animate(0) ]),
      transition('closed => open', [ animate('350ms ease-out') ]),
      transition('open => closed', [ animate('350ms ease-out') ])
    ])
  ]
})*/
// fix: replace with // '@angular/animate';
// when https://github.com/angular/angular/issues/5984 will be fixed
// TODO: remove ElementRef
// TODO: add on change
// TODO: #576 add callbacks: expanding, expanded, collapsing, collapsed
var CollapseDirective = (function () {
    function CollapseDirective(/*_ab:AnimationBuilder, */ _el, _renderer) {
        // shown
        this.isExpanded = true;
        // hidden
        this.isCollapsed = false;
        // stale state
        this.isCollapse = true;
        // animation state
        this.isCollapsing = false;
        // this._ab = _ab;
        this._el = _el;
        this._renderer = _renderer;
    }
    Object.defineProperty(CollapseDirective.prototype, "collapse", {
        get: function () {
            return this.isExpanded;
        },
        // @Input() private transitionDuration:number = 500; // Duration in ms
        set: function (value) {
            this.isExpanded = value;
            this.toggle();
        },
        enumerable: true,
        configurable: true
    });
    CollapseDirective.prototype.ngOnInit = function () {
        // this.animation = this._ab.css();
        // this.animation.setDuration(this.transitionDuration);
    };
    CollapseDirective.prototype.toggle = function () {
        // this.open = !this.open;
        if (this.isExpanded) {
            this.hide();
        }
        else {
            this.show();
        }
    };
    CollapseDirective.prototype.hide = function () {
        this.isCollapse = false;
        this.isCollapsing = true;
        this.isExpanded = false;
        this.isCollapsed = true;
        this.isCollapse = true;
        this.isCollapsing = false;
        this.display = 'none';
        /*  setTimeout(() => {
              // this.height = '0';
              // this.isCollapse = true;
              // this.isCollapsing = false;
              this.animation
                .setFromStyles({
                  height: this._el.nativeElement.scrollHeight + 'px'
                })
                .setToStyles({
                  height: '0',
                  overflow: 'hidden'
                });
    
              this.animation.start(this._el.nativeElement)
                .onComplete(() => {
                  if (this._el.nativeElement.offsetHeight === 0) {
                    this.display = 'none';
                  }
    
                  this.isCollapse = true;
                  this.isCollapsing = false;
                });
            }, 4);*/
    };
    CollapseDirective.prototype.show = function () {
        this.isCollapse = false;
        this.isCollapsing = true;
        this.isExpanded = true;
        this.isCollapsed = false;
        this.display = 'block';
        // this.height = 'auto';
        this.isCollapse = true;
        this.isCollapsing = false;
        this._renderer.setElementStyle(this._el.nativeElement, 'overflow', 'visible');
        this._renderer.setElementStyle(this._el.nativeElement, 'height', 'auto');
        /*setTimeout(() => {
            // this.height = 'auto';
            // this.isCollapse = true;
            // this.isCollapsing = false;
            this.animation
              .setFromStyles({
                height: this._el.nativeElement.offsetHeight,
                overflow: 'hidden'
              })
              .setToStyles({
                height: this._el.nativeElement.scrollHeight + 'px'
              });
    
            this.animation.start(this._el.nativeElement)
              .onComplete(() => {
                this.isCollapse = true;
                this.isCollapsing = false;
                this._renderer.setElementStyle(this._el.nativeElement, 'overflow', 'visible');
                this._renderer.setElementStyle(this._el.nativeElement, 'height', 'auto');
              });
          }, 4);*/
    };
    __decorate([
        core_1.HostBinding('style.display'), 
        __metadata('design:type', String)
    ], CollapseDirective.prototype, "display", void 0);
    __decorate([
        core_1.HostBinding('class.in'),
        core_1.HostBinding('attr.aria-expanded'), 
        __metadata('design:type', Boolean)
    ], CollapseDirective.prototype, "isExpanded", void 0);
    __decorate([
        core_1.HostBinding('attr.aria-hidden'), 
        __metadata('design:type', Boolean)
    ], CollapseDirective.prototype, "isCollapsed", void 0);
    __decorate([
        core_1.HostBinding('class.collapse'), 
        __metadata('design:type', Boolean)
    ], CollapseDirective.prototype, "isCollapse", void 0);
    __decorate([
        core_1.HostBinding('class.collapsing'), 
        __metadata('design:type', Boolean)
    ], CollapseDirective.prototype, "isCollapsing", void 0);
    __decorate([
        core_1.Input(), 
        __metadata('design:type', Boolean), 
        __metadata('design:paramtypes', [Boolean])
    ], CollapseDirective.prototype, "collapse", null);
    CollapseDirective = __decorate([
        core_1.Directive({ selector: '[collapse]' }), 
        __metadata('design:paramtypes', [core_1.ElementRef, core_1.Renderer])
    ], CollapseDirective);
    return CollapseDirective;
}());
exports.CollapseDirective = CollapseDirective;
