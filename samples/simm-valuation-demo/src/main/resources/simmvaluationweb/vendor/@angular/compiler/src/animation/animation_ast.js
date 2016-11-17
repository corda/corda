/**
 * @license
 * Copyright Google Inc. All Rights Reserved.
 *
 * Use of this source code is governed by an MIT-style license that can be
 * found in the LICENSE file at https://angular.io/license
 */
"use strict";
var __extends = (this && this.__extends) || function (d, b) {
    for (var p in b) if (b.hasOwnProperty(p)) d[p] = b[p];
    function __() { this.constructor = d; }
    d.prototype = b === null ? Object.create(b) : (__.prototype = b.prototype, new __());
};
var AnimationAst = (function () {
    function AnimationAst() {
        this.startTime = 0;
        this.playTime = 0;
    }
    return AnimationAst;
}());
exports.AnimationAst = AnimationAst;
var AnimationStateAst = (function (_super) {
    __extends(AnimationStateAst, _super);
    function AnimationStateAst() {
        _super.apply(this, arguments);
    }
    return AnimationStateAst;
}(AnimationAst));
exports.AnimationStateAst = AnimationStateAst;
var AnimationEntryAst = (function (_super) {
    __extends(AnimationEntryAst, _super);
    function AnimationEntryAst(name, stateDeclarations, stateTransitions) {
        _super.call(this);
        this.name = name;
        this.stateDeclarations = stateDeclarations;
        this.stateTransitions = stateTransitions;
    }
    AnimationEntryAst.prototype.visit = function (visitor, context) {
        return visitor.visitAnimationEntry(this, context);
    };
    return AnimationEntryAst;
}(AnimationAst));
exports.AnimationEntryAst = AnimationEntryAst;
var AnimationStateDeclarationAst = (function (_super) {
    __extends(AnimationStateDeclarationAst, _super);
    function AnimationStateDeclarationAst(stateName, styles) {
        _super.call(this);
        this.stateName = stateName;
        this.styles = styles;
    }
    AnimationStateDeclarationAst.prototype.visit = function (visitor, context) {
        return visitor.visitAnimationStateDeclaration(this, context);
    };
    return AnimationStateDeclarationAst;
}(AnimationStateAst));
exports.AnimationStateDeclarationAst = AnimationStateDeclarationAst;
var AnimationStateTransitionExpression = (function () {
    function AnimationStateTransitionExpression(fromState, toState) {
        this.fromState = fromState;
        this.toState = toState;
    }
    return AnimationStateTransitionExpression;
}());
exports.AnimationStateTransitionExpression = AnimationStateTransitionExpression;
var AnimationStateTransitionAst = (function (_super) {
    __extends(AnimationStateTransitionAst, _super);
    function AnimationStateTransitionAst(stateChanges, animation) {
        _super.call(this);
        this.stateChanges = stateChanges;
        this.animation = animation;
    }
    AnimationStateTransitionAst.prototype.visit = function (visitor, context) {
        return visitor.visitAnimationStateTransition(this, context);
    };
    return AnimationStateTransitionAst;
}(AnimationStateAst));
exports.AnimationStateTransitionAst = AnimationStateTransitionAst;
var AnimationStepAst = (function (_super) {
    __extends(AnimationStepAst, _super);
    function AnimationStepAst(startingStyles, keyframes, duration, delay, easing) {
        _super.call(this);
        this.startingStyles = startingStyles;
        this.keyframes = keyframes;
        this.duration = duration;
        this.delay = delay;
        this.easing = easing;
    }
    AnimationStepAst.prototype.visit = function (visitor, context) {
        return visitor.visitAnimationStep(this, context);
    };
    return AnimationStepAst;
}(AnimationAst));
exports.AnimationStepAst = AnimationStepAst;
var AnimationStylesAst = (function (_super) {
    __extends(AnimationStylesAst, _super);
    function AnimationStylesAst(styles) {
        _super.call(this);
        this.styles = styles;
    }
    AnimationStylesAst.prototype.visit = function (visitor, context) {
        return visitor.visitAnimationStyles(this, context);
    };
    return AnimationStylesAst;
}(AnimationAst));
exports.AnimationStylesAst = AnimationStylesAst;
var AnimationKeyframeAst = (function (_super) {
    __extends(AnimationKeyframeAst, _super);
    function AnimationKeyframeAst(offset, styles) {
        _super.call(this);
        this.offset = offset;
        this.styles = styles;
    }
    AnimationKeyframeAst.prototype.visit = function (visitor, context) {
        return visitor.visitAnimationKeyframe(this, context);
    };
    return AnimationKeyframeAst;
}(AnimationAst));
exports.AnimationKeyframeAst = AnimationKeyframeAst;
var AnimationWithStepsAst = (function (_super) {
    __extends(AnimationWithStepsAst, _super);
    function AnimationWithStepsAst(steps) {
        _super.call(this);
        this.steps = steps;
    }
    return AnimationWithStepsAst;
}(AnimationAst));
exports.AnimationWithStepsAst = AnimationWithStepsAst;
var AnimationGroupAst = (function (_super) {
    __extends(AnimationGroupAst, _super);
    function AnimationGroupAst(steps) {
        _super.call(this, steps);
    }
    AnimationGroupAst.prototype.visit = function (visitor, context) {
        return visitor.visitAnimationGroup(this, context);
    };
    return AnimationGroupAst;
}(AnimationWithStepsAst));
exports.AnimationGroupAst = AnimationGroupAst;
var AnimationSequenceAst = (function (_super) {
    __extends(AnimationSequenceAst, _super);
    function AnimationSequenceAst(steps) {
        _super.call(this, steps);
    }
    AnimationSequenceAst.prototype.visit = function (visitor, context) {
        return visitor.visitAnimationSequence(this, context);
    };
    return AnimationSequenceAst;
}(AnimationWithStepsAst));
exports.AnimationSequenceAst = AnimationSequenceAst;
//# sourceMappingURL=animation_ast.js.map