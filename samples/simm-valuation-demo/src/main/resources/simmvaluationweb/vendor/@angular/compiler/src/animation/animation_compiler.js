/**
 * @license
 * Copyright Google Inc. All Rights Reserved.
 *
 * Use of this source code is governed by an MIT-style license that can be
 * found in the LICENSE file at https://angular.io/license
 */
"use strict";
var core_private_1 = require('../../core_private');
var collection_1 = require('../facade/collection');
var exceptions_1 = require('../facade/exceptions');
var lang_1 = require('../facade/lang');
var identifiers_1 = require('../identifiers');
var o = require('../output/output_ast');
var animation_ast_1 = require('./animation_ast');
var animation_parser_1 = require('./animation_parser');
var CompiledAnimation = (function () {
    function CompiledAnimation(name, statesMapStatement, statesVariableName, fnStatement, fnVariable) {
        this.name = name;
        this.statesMapStatement = statesMapStatement;
        this.statesVariableName = statesVariableName;
        this.fnStatement = fnStatement;
        this.fnVariable = fnVariable;
    }
    return CompiledAnimation;
}());
exports.CompiledAnimation = CompiledAnimation;
var AnimationCompiler = (function () {
    function AnimationCompiler() {
    }
    AnimationCompiler.prototype.compileComponent = function (component) {
        var compiledAnimations = [];
        var index = 0;
        component.template.animations.forEach(function (entry) {
            var result = animation_parser_1.parseAnimationEntry(entry);
            if (result.errors.length > 0) {
                var errorMessage = '';
                result.errors.forEach(function (error) { errorMessage += '\n- ' + error.msg; });
                // todo (matsko): include the component name when throwing
                throw new exceptions_1.BaseException(("Unable to parse the animation sequence for \"" + entry.name + "\" due to the following errors: ") +
                    errorMessage);
            }
            var factoryName = component.type.name + "_" + entry.name + "_" + index;
            index++;
            var visitor = new _AnimationBuilder(entry.name, factoryName);
            compiledAnimations.push(visitor.build(result.ast));
        });
        return compiledAnimations;
    };
    return AnimationCompiler;
}());
exports.AnimationCompiler = AnimationCompiler;
var _ANIMATION_FACTORY_ELEMENT_VAR = o.variable('element');
var _ANIMATION_DEFAULT_STATE_VAR = o.variable('defaultStateStyles');
var _ANIMATION_FACTORY_VIEW_VAR = o.variable('view');
var _ANIMATION_FACTORY_RENDERER_VAR = _ANIMATION_FACTORY_VIEW_VAR.prop('renderer');
var _ANIMATION_CURRENT_STATE_VAR = o.variable('currentState');
var _ANIMATION_NEXT_STATE_VAR = o.variable('nextState');
var _ANIMATION_PLAYER_VAR = o.variable('player');
var _ANIMATION_START_STATE_STYLES_VAR = o.variable('startStateStyles');
var _ANIMATION_END_STATE_STYLES_VAR = o.variable('endStateStyles');
var _ANIMATION_COLLECTED_STYLES = o.variable('collectedStyles');
var EMPTY_MAP = o.literalMap([]);
var _AnimationBuilder = (function () {
    function _AnimationBuilder(animationName, factoryName) {
        this.animationName = animationName;
        this._fnVarName = factoryName + '_factory';
        this._statesMapVarName = factoryName + '_states';
        this._statesMapVar = o.variable(this._statesMapVarName);
    }
    _AnimationBuilder.prototype.visitAnimationStyles = function (ast, context) {
        var stylesArr = [];
        if (context.isExpectingFirstStyleStep) {
            stylesArr.push(_ANIMATION_START_STATE_STYLES_VAR);
            context.isExpectingFirstStyleStep = false;
        }
        ast.styles.forEach(function (entry) {
            stylesArr.push(o.literalMap(collection_1.StringMapWrapper.keys(entry).map(function (key) { return [key, o.literal(entry[key])]; })));
        });
        return o.importExpr(identifiers_1.Identifiers.AnimationStyles).instantiate([
            o.importExpr(identifiers_1.Identifiers.collectAndResolveStyles).callFn([
                _ANIMATION_COLLECTED_STYLES, o.literalArr(stylesArr)
            ])
        ]);
    };
    _AnimationBuilder.prototype.visitAnimationKeyframe = function (ast, context) {
        return o.importExpr(identifiers_1.Identifiers.AnimationKeyframe).instantiate([
            o.literal(ast.offset), ast.styles.visit(this, context)
        ]);
    };
    _AnimationBuilder.prototype.visitAnimationStep = function (ast, context) {
        var _this = this;
        if (context.endStateAnimateStep === ast) {
            return this._visitEndStateAnimation(ast, context);
        }
        var startingStylesExpr = ast.startingStyles.visit(this, context);
        var keyframeExpressions = ast.keyframes.map(function (keyframeEntry) { return keyframeEntry.visit(_this, context); });
        return this._callAnimateMethod(ast, startingStylesExpr, o.literalArr(keyframeExpressions));
    };
    /** @internal */
    _AnimationBuilder.prototype._visitEndStateAnimation = function (ast, context) {
        var _this = this;
        var startingStylesExpr = ast.startingStyles.visit(this, context);
        var keyframeExpressions = ast.keyframes.map(function (keyframe) { return keyframe.visit(_this, context); });
        var keyframesExpr = o.importExpr(identifiers_1.Identifiers.balanceAnimationKeyframes).callFn([
            _ANIMATION_COLLECTED_STYLES, _ANIMATION_END_STATE_STYLES_VAR,
            o.literalArr(keyframeExpressions)
        ]);
        return this._callAnimateMethod(ast, startingStylesExpr, keyframesExpr);
    };
    /** @internal */
    _AnimationBuilder.prototype._callAnimateMethod = function (ast, startingStylesExpr, keyframesExpr) {
        return _ANIMATION_FACTORY_RENDERER_VAR.callMethod('animate', [
            _ANIMATION_FACTORY_ELEMENT_VAR, startingStylesExpr, keyframesExpr, o.literal(ast.duration),
            o.literal(ast.delay), o.literal(ast.easing)
        ]);
    };
    _AnimationBuilder.prototype.visitAnimationSequence = function (ast, context) {
        var _this = this;
        var playerExprs = ast.steps.map(function (step) { return step.visit(_this, context); });
        return o.importExpr(identifiers_1.Identifiers.AnimationSequencePlayer).instantiate([o.literalArr(playerExprs)]);
    };
    _AnimationBuilder.prototype.visitAnimationGroup = function (ast, context) {
        var _this = this;
        var playerExprs = ast.steps.map(function (step) { return step.visit(_this, context); });
        return o.importExpr(identifiers_1.Identifiers.AnimationGroupPlayer).instantiate([o.literalArr(playerExprs)]);
    };
    _AnimationBuilder.prototype.visitAnimationStateDeclaration = function (ast, context) {
        var flatStyles = {};
        _getStylesArray(ast).forEach(function (entry) {
            collection_1.StringMapWrapper.forEach(entry, function (value, key) { flatStyles[key] = value; });
        });
        context.stateMap.registerState(ast.stateName, flatStyles);
    };
    _AnimationBuilder.prototype.visitAnimationStateTransition = function (ast, context) {
        var steps = ast.animation.steps;
        var lastStep = steps[steps.length - 1];
        if (_isEndStateAnimateStep(lastStep)) {
            context.endStateAnimateStep = lastStep;
        }
        context.isExpectingFirstStyleStep = true;
        var stateChangePreconditions = [];
        ast.stateChanges.forEach(function (stateChange) {
            stateChangePreconditions.push(_compareToAnimationStateExpr(_ANIMATION_CURRENT_STATE_VAR, stateChange.fromState)
                .and(_compareToAnimationStateExpr(_ANIMATION_NEXT_STATE_VAR, stateChange.toState)));
            if (stateChange.fromState != core_private_1.ANY_STATE) {
                context.stateMap.registerState(stateChange.fromState);
            }
            if (stateChange.toState != core_private_1.ANY_STATE) {
                context.stateMap.registerState(stateChange.toState);
            }
        });
        var animationPlayerExpr = ast.animation.visit(this, context);
        var reducedStateChangesPrecondition = stateChangePreconditions.reduce(function (a, b) { return a.or(b); });
        var precondition = _ANIMATION_PLAYER_VAR.equals(o.NULL_EXPR).and(reducedStateChangesPrecondition);
        return new o.IfStmt(precondition, [_ANIMATION_PLAYER_VAR.set(animationPlayerExpr).toStmt()]);
    };
    _AnimationBuilder.prototype.visitAnimationEntry = function (ast, context) {
        var _this = this;
        // visit each of the declarations first to build the context state map
        ast.stateDeclarations.forEach(function (def) { return def.visit(_this, context); });
        // this should always be defined even if the user overrides it
        context.stateMap.registerState(core_private_1.DEFAULT_STATE, {});
        var statements = [];
        statements.push(_ANIMATION_FACTORY_VIEW_VAR
            .callMethod('cancelActiveAnimation', [
            _ANIMATION_FACTORY_ELEMENT_VAR, o.literal(this.animationName),
            _ANIMATION_NEXT_STATE_VAR.equals(o.literal(core_private_1.EMPTY_STATE))
        ])
            .toStmt());
        statements.push(_ANIMATION_COLLECTED_STYLES.set(EMPTY_MAP).toDeclStmt());
        statements.push(_ANIMATION_PLAYER_VAR.set(o.NULL_EXPR).toDeclStmt());
        statements.push(_ANIMATION_DEFAULT_STATE_VAR.set(this._statesMapVar.key(o.literal(core_private_1.DEFAULT_STATE)))
            .toDeclStmt());
        statements.push(_ANIMATION_START_STATE_STYLES_VAR.set(this._statesMapVar.key(_ANIMATION_CURRENT_STATE_VAR))
            .toDeclStmt());
        statements.push(new o.IfStmt(_ANIMATION_START_STATE_STYLES_VAR.equals(o.NULL_EXPR), [_ANIMATION_START_STATE_STYLES_VAR.set(_ANIMATION_DEFAULT_STATE_VAR).toStmt()]));
        statements.push(_ANIMATION_END_STATE_STYLES_VAR.set(this._statesMapVar.key(_ANIMATION_NEXT_STATE_VAR))
            .toDeclStmt());
        statements.push(new o.IfStmt(_ANIMATION_END_STATE_STYLES_VAR.equals(o.NULL_EXPR), [_ANIMATION_END_STATE_STYLES_VAR.set(_ANIMATION_DEFAULT_STATE_VAR).toStmt()]));
        var RENDER_STYLES_FN = o.importExpr(identifiers_1.Identifiers.renderStyles);
        // before we start any animation we want to clear out the starting
        // styles from the element's style property (since they were placed
        // there at the end of the last animation
        statements.push(RENDER_STYLES_FN
            .callFn([
            _ANIMATION_FACTORY_ELEMENT_VAR, _ANIMATION_FACTORY_RENDERER_VAR,
            o.importExpr(identifiers_1.Identifiers.clearStyles).callFn([_ANIMATION_START_STATE_STYLES_VAR])
        ])
            .toStmt());
        ast.stateTransitions.forEach(function (transAst) { return statements.push(transAst.visit(_this, context)); });
        // this check ensures that the animation factory always returns a player
        // so that the onDone callback can be used for tracking
        statements.push(new o.IfStmt(_ANIMATION_PLAYER_VAR.equals(o.NULL_EXPR), [_ANIMATION_PLAYER_VAR.set(o.importExpr(identifiers_1.Identifiers.NoOpAnimationPlayer).instantiate([]))
                .toStmt()]));
        // once complete we want to apply the styles on the element
        // since the destination state's values should persist once
        // the animation sequence has completed.
        statements.push(_ANIMATION_PLAYER_VAR
            .callMethod('onDone', [o.fn([], [RENDER_STYLES_FN
                    .callFn([
                    _ANIMATION_FACTORY_ELEMENT_VAR, _ANIMATION_FACTORY_RENDERER_VAR,
                    o.importExpr(identifiers_1.Identifiers.prepareFinalAnimationStyles).callFn([
                        _ANIMATION_START_STATE_STYLES_VAR, _ANIMATION_END_STATE_STYLES_VAR
                    ])
                ])
                    .toStmt()])])
            .toStmt());
        statements.push(_ANIMATION_FACTORY_VIEW_VAR
            .callMethod('registerAndStartAnimation', [
            _ANIMATION_FACTORY_ELEMENT_VAR, o.literal(this.animationName),
            _ANIMATION_PLAYER_VAR
        ])
            .toStmt());
        return o.fn([
            new o.FnParam(_ANIMATION_FACTORY_VIEW_VAR.name, o.importType(identifiers_1.Identifiers.AppView, [o.DYNAMIC_TYPE])),
            new o.FnParam(_ANIMATION_FACTORY_ELEMENT_VAR.name, o.DYNAMIC_TYPE),
            new o.FnParam(_ANIMATION_CURRENT_STATE_VAR.name, o.DYNAMIC_TYPE),
            new o.FnParam(_ANIMATION_NEXT_STATE_VAR.name, o.DYNAMIC_TYPE)
        ], statements);
    };
    _AnimationBuilder.prototype.build = function (ast) {
        var context = new _AnimationBuilderContext();
        var fnStatement = ast.visit(this, context).toDeclStmt(this._fnVarName);
        var fnVariable = o.variable(this._fnVarName);
        var lookupMap = [];
        collection_1.StringMapWrapper.forEach(context.stateMap.states, function (value, stateName) {
            var variableValue = EMPTY_MAP;
            if (lang_1.isPresent(value)) {
                var styleMap_1 = [];
                collection_1.StringMapWrapper.forEach(value, function (value, key) {
                    styleMap_1.push([key, o.literal(value)]);
                });
                variableValue = o.literalMap(styleMap_1);
            }
            lookupMap.push([stateName, variableValue]);
        });
        var compiledStatesMapExpr = this._statesMapVar.set(o.literalMap(lookupMap)).toDeclStmt();
        return new CompiledAnimation(this.animationName, compiledStatesMapExpr, this._statesMapVarName, fnStatement, fnVariable);
    };
    return _AnimationBuilder;
}());
var _AnimationBuilderContext = (function () {
    function _AnimationBuilderContext() {
        this.stateMap = new _AnimationBuilderStateMap();
        this.endStateAnimateStep = null;
        this.isExpectingFirstStyleStep = false;
    }
    return _AnimationBuilderContext;
}());
var _AnimationBuilderStateMap = (function () {
    function _AnimationBuilderStateMap() {
        this._states = {};
    }
    Object.defineProperty(_AnimationBuilderStateMap.prototype, "states", {
        get: function () { return this._states; },
        enumerable: true,
        configurable: true
    });
    _AnimationBuilderStateMap.prototype.registerState = function (name, value) {
        if (value === void 0) { value = null; }
        var existingEntry = this._states[name];
        if (lang_1.isBlank(existingEntry)) {
            this._states[name] = value;
        }
    };
    return _AnimationBuilderStateMap;
}());
function _compareToAnimationStateExpr(value, animationState) {
    var emptyStateLiteral = o.literal(core_private_1.EMPTY_STATE);
    switch (animationState) {
        case core_private_1.EMPTY_STATE:
            return value.equals(emptyStateLiteral);
        case core_private_1.ANY_STATE:
            return o.literal(true);
        default:
            return value.equals(o.literal(animationState));
    }
}
function _isEndStateAnimateStep(step) {
    // the final animation step is characterized by having only TWO
    // keyframe values and it must have zero styles for both keyframes
    if (step instanceof animation_ast_1.AnimationStepAst && step.duration > 0 && step.keyframes.length == 2) {
        var styles1 = _getStylesArray(step.keyframes[0])[0];
        var styles2 = _getStylesArray(step.keyframes[1])[0];
        return collection_1.StringMapWrapper.isEmpty(styles1) && collection_1.StringMapWrapper.isEmpty(styles2);
    }
    return false;
}
function _getStylesArray(obj) {
    return obj.styles.styles;
}
//# sourceMappingURL=animation_compiler.js.map