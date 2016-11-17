// todo: should we support enforce focus in?
// todo: in original bs there are was a way to prevent modal from showing
// todo: original modal had resize events
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
var __param = (this && this.__param) || function (paramIndex, decorator) {
    return function (target, key) { decorator(target, key, paramIndex); }
};
var core_1 = require('@angular/core');
var lang_1 = require('@angular/core/src/facade/lang');
var modal_backdrop_component_1 = require('./modal-backdrop.component');
var modal_options_class_1 = require('./modal-options.class');
var components_helper_service_1 = require('../utils/components-helper.service');
var utils_class_1 = require('../utils/utils.class');
var TRANSITION_DURATION = 300;
var BACKDROP_TRANSITION_DURATION = 150;
var ModalDirective = (function () {
    function ModalDirective(element, renderer, componentsHelper) {
        this.onShow = new core_1.EventEmitter();
        this.onShown = new core_1.EventEmitter();
        this.onHide = new core_1.EventEmitter();
        this.onHidden = new core_1.EventEmitter();
        // seems like an Options
        this.isAnimated = true;
        this._isShown = false;
        this.isBodyOverflowing = false;
        this.originalBodyPadding = 0;
        this.scrollbarWidth = 0;
        this.element = element;
        this.renderer = renderer;
        this.componentsHelper = componentsHelper;
    }
    Object.defineProperty(ModalDirective.prototype, "config", {
        get: function () {
            return this._config;
        },
        set: function (conf) {
            this._config = this.getConfig(conf);
        },
        enumerable: true,
        configurable: true
    });
    ;
    Object.defineProperty(ModalDirective.prototype, "isShown", {
        get: function () {
            return this._isShown;
        },
        enumerable: true,
        configurable: true
    });
    Object.defineProperty(ModalDirective.prototype, "document", {
        get: function () {
            return this.componentsHelper.getDocument();
        },
        enumerable: true,
        configurable: true
    });
    ;
    /** Host element manipulations */
    // @HostBinding(`class.${ClassName.IN}`) private _addClassIn:boolean;
    ModalDirective.prototype.onClick = function (event) {
        if (this.config.ignoreBackdropClick || this.config.backdrop === 'static' || event.target !== this.element.nativeElement) {
            return;
        }
        this.hide(event);
    };
    // todo: consider preventing default and stopping propagation
    ModalDirective.prototype.onEsc = function () {
        if (this.config.keyboard) {
            this.hide();
        }
    };
    ModalDirective.prototype.ngOnDestroy = function () {
        this.config = void 0;
        // this._element             = null
        // this._dialog              = null
        // this._backdrop            = null
        this._isShown = void 0;
        this.isBodyOverflowing = void 0;
        this.originalBodyPadding = void 0;
        this.scrollbarWidth = void 0;
    };
    ModalDirective.prototype.ngAfterViewInit = function () {
        this._config = this._config || this.getConfig();
    };
    /** Public methods */
    ModalDirective.prototype.toggle = function () {
        return this._isShown ? this.hide() : this.show();
    };
    ModalDirective.prototype.show = function () {
        var _this = this;
        this.onShow.emit(this);
        if (this._isShown) {
            return;
        }
        this._isShown = true;
        this.checkScrollbar();
        this.setScrollbar();
        if (this.document && this.document.body) {
            this.renderer.setElementClass(this.document.body, modal_options_class_1.ClassName.OPEN, true);
        }
        this.showBackdrop(function () {
            _this.showElement();
        });
    };
    ModalDirective.prototype.hide = function (event) {
        var _this = this;
        if (event) {
            event.preventDefault();
        }
        this.onHide.emit(this);
        // todo: add an option to prevent hiding
        if (!this._isShown) {
            return;
        }
        this._isShown = false;
        this.renderer.setElementClass(this.element.nativeElement, modal_options_class_1.ClassName.IN, false);
        // this._addClassIn = false;
        if (this.isAnimated) {
            setTimeout(function () { return _this.hideModal(); }, TRANSITION_DURATION);
        }
        else {
            this.hideModal();
        }
    };
    /** Private methods */
    ModalDirective.prototype.getConfig = function (config) {
        return Object.assign({}, modal_options_class_1.modalConfigDefaults, config);
    };
    /**
     *  Show dialog
     */
    ModalDirective.prototype.showElement = function () {
        var _this = this;
        // todo: replace this with component helper usage `add to root`
        if (!this.element.nativeElement.parentNode ||
            (this.element.nativeElement.parentNode.nodeType !== Node.ELEMENT_NODE)) {
            // don't move modals dom position
            if (this.document && this.document.body) {
                this.document.body.appendChild(this.element.nativeElement);
            }
        }
        this.renderer.setElementAttribute(this.element.nativeElement, 'aria-hidden', 'false');
        this.renderer.setElementStyle(this.element.nativeElement, 'display', 'block');
        this.renderer.setElementProperty(this.element.nativeElement, 'scrollTop', 0);
        if (this.isAnimated) {
            utils_class_1.Utils.reflow(this.element.nativeElement);
        }
        // this._addClassIn = true;
        this.renderer.setElementClass(this.element.nativeElement, modal_options_class_1.ClassName.IN, true);
        this.onShown.emit(this);
        var transitionComplete = function () {
            if (_this._config.focus) {
                _this.element.nativeElement.focus();
            }
            _this.onShown.emit(_this);
        };
        if (this.isAnimated) {
            setTimeout(transitionComplete, TRANSITION_DURATION);
        }
        else {
            transitionComplete();
        }
    };
    ModalDirective.prototype.hideModal = function () {
        var _this = this;
        this.renderer.setElementAttribute(this.element.nativeElement, 'aria-hidden', 'true');
        this.renderer.setElementStyle(this.element.nativeElement, 'display', 'none');
        this.showBackdrop(function () {
            if (_this.document && _this.document.body) {
                _this.renderer.setElementClass(_this.document.body, modal_options_class_1.ClassName.OPEN, false);
            }
            _this.resetAdjustments();
            _this.resetScrollbar();
            _this.onHidden.emit(_this);
        });
    };
    // todo: original show was calling a callback when done, but we can use promise
    ModalDirective.prototype.showBackdrop = function (callback) {
        var _this = this;
        if (this._isShown && this.config.backdrop) {
            this.backdrop = this.componentsHelper
                .appendNextToRoot(modal_backdrop_component_1.ModalBackdropComponent, modal_backdrop_component_1.ModalBackdropOptions, new modal_backdrop_component_1.ModalBackdropOptions({ animate: false }));
            this.backdrop.then(function (backdrop) {
                if (_this.isAnimated) {
                    backdrop.instance.isAnimated = _this.isAnimated;
                    utils_class_1.Utils.reflow(backdrop.instance.element.nativeElement);
                }
                backdrop.instance.isShown = true;
                if (!callback) {
                    return;
                }
                if (!_this.isAnimated) {
                    callback();
                    return;
                }
                setTimeout(callback, BACKDROP_TRANSITION_DURATION);
            });
        }
        else if (!this._isShown && this.backdrop) {
            this.backdrop.then(function (backdrop) {
                backdrop.instance.isShown = false;
                var callbackRemove = function () {
                    _this.removeBackdrop();
                    if (callback) {
                        callback();
                    }
                };
                if (backdrop.instance.isAnimated) {
                    setTimeout(callbackRemove, BACKDROP_TRANSITION_DURATION);
                }
                else {
                    callbackRemove();
                }
            });
        }
        else if (callback) {
            callback();
        }
    };
    ModalDirective.prototype.removeBackdrop = function () {
        var _this = this;
        if (this.backdrop) {
            this.backdrop.then(function (backdrop) {
                backdrop.destroy();
                _this.backdrop = void 0;
            });
        }
    };
    /** Events tricks */
    // no need for it
    // private setEscapeEvent():void {
    //   if (this._isShown && this._config.keyboard) {
    //     $(this._element).on(Event.KEYDOWN_DISMISS, (event) => {
    //       if (event.which === 27) {
    //         this.hide()
    //       }
    //     })
    //
    //   } else if (!this._isShown) {
    //     $(this._element).off(Event.KEYDOWN_DISMISS)
    //   }
    // }
    // private setResizeEvent():void {
    // console.log(this.renderer.listenGlobal('', Event.RESIZE));
    // if (this._isShown) {
    //   $(window).on(Event.RESIZE, $.proxy(this._handleUpdate, this))
    // } else {
    //   $(window).off(Event.RESIZE)
    // }
    // }
    ModalDirective.prototype.resetAdjustments = function () {
        this.renderer.setElementStyle(this.element.nativeElement, 'paddingLeft', '');
        this.renderer.setElementStyle(this.element.nativeElement, 'paddingRight', '');
    };
    /** Scroll bar tricks */
    ModalDirective.prototype.checkScrollbar = function () {
        // this._isBodyOverflowing = document.body.clientWidth < window.innerWidth
        this.isBodyOverflowing = this.document.body.clientWidth < lang_1.global.innerWidth;
        this.scrollbarWidth = this.getScrollbarWidth();
    };
    ModalDirective.prototype.setScrollbar = function () {
        if (!this.document) {
            return;
        }
        var fixedEl = this.document.querySelector(modal_options_class_1.Selector.FIXED_CONTENT);
        if (!fixedEl) {
            return;
        }
        var bodyPadding = parseInt(utils_class_1.Utils.getStyles(fixedEl).paddingRight || 0, 10);
        this.originalBodyPadding = parseInt(this.document.body.style.paddingRight || 0, 10);
        if (this.isBodyOverflowing) {
            this.document.body.style.paddingRight = (bodyPadding + this.scrollbarWidth) + "px";
        }
    };
    ModalDirective.prototype.resetScrollbar = function () {
        this.document.body.style.paddingRight = this.originalBodyPadding;
    };
    // thx d.walsh
    ModalDirective.prototype.getScrollbarWidth = function () {
        var scrollDiv = this.renderer.createElement(this.document.body, 'div', void 0);
        scrollDiv.className = modal_options_class_1.ClassName.SCROLLBAR_MEASURER;
        var scrollbarWidth = scrollDiv.offsetWidth - scrollDiv.clientWidth;
        this.document.body.removeChild(scrollDiv);
        return scrollbarWidth;
    };
    __decorate([
        core_1.Input(), 
        __metadata('design:type', Object), 
        __metadata('design:paramtypes', [Object])
    ], ModalDirective.prototype, "config", null);
    __decorate([
        core_1.Output(), 
        __metadata('design:type', core_1.EventEmitter)
    ], ModalDirective.prototype, "onShow", void 0);
    __decorate([
        core_1.Output(), 
        __metadata('design:type', core_1.EventEmitter)
    ], ModalDirective.prototype, "onShown", void 0);
    __decorate([
        core_1.Output(), 
        __metadata('design:type', core_1.EventEmitter)
    ], ModalDirective.prototype, "onHide", void 0);
    __decorate([
        core_1.Output(), 
        __metadata('design:type', core_1.EventEmitter)
    ], ModalDirective.prototype, "onHidden", void 0);
    __decorate([
        core_1.HostListener('click', ['$event']), 
        __metadata('design:type', Function), 
        __metadata('design:paramtypes', [Object]), 
        __metadata('design:returntype', void 0)
    ], ModalDirective.prototype, "onClick", null);
    __decorate([
        core_1.HostListener('keydown.esc'), 
        __metadata('design:type', Function), 
        __metadata('design:paramtypes', []), 
        __metadata('design:returntype', void 0)
    ], ModalDirective.prototype, "onEsc", null);
    ModalDirective = __decorate([
        core_1.Directive({
            selector: '[bsModal]',
            exportAs: 'bs-modal'
        }),
        __param(2, core_1.Inject(components_helper_service_1.ComponentsHelper)), 
        __metadata('design:paramtypes', [core_1.ElementRef, core_1.Renderer, components_helper_service_1.ComponentsHelper])
    ], ModalDirective);
    return ModalDirective;
}());
exports.ModalDirective = ModalDirective;
