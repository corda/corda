/**
 * @license
 * Copyright Google Inc. All Rights Reserved.
 *
 * Use of this source code is governed by an MIT-style license that can be
 * found in the LICENSE file at https://angular.io/license
 */
export class AnimationAst {
    constructor() {
        this.startTime = 0;
        this.playTime = 0;
    }
}
export class AnimationStateAst extends AnimationAst {
}
export class AnimationEntryAst extends AnimationAst {
    constructor(name, stateDeclarations, stateTransitions) {
        super();
        this.name = name;
        this.stateDeclarations = stateDeclarations;
        this.stateTransitions = stateTransitions;
    }
    visit(visitor, context) {
        return visitor.visitAnimationEntry(this, context);
    }
}
export class AnimationStateDeclarationAst extends AnimationStateAst {
    constructor(stateName, styles) {
        super();
        this.stateName = stateName;
        this.styles = styles;
    }
    visit(visitor, context) {
        return visitor.visitAnimationStateDeclaration(this, context);
    }
}
export class AnimationStateTransitionExpression {
    constructor(fromState, toState) {
        this.fromState = fromState;
        this.toState = toState;
    }
}
export class AnimationStateTransitionAst extends AnimationStateAst {
    constructor(stateChanges, animation) {
        super();
        this.stateChanges = stateChanges;
        this.animation = animation;
    }
    visit(visitor, context) {
        return visitor.visitAnimationStateTransition(this, context);
    }
}
export class AnimationStepAst extends AnimationAst {
    constructor(startingStyles, keyframes, duration, delay, easing) {
        super();
        this.startingStyles = startingStyles;
        this.keyframes = keyframes;
        this.duration = duration;
        this.delay = delay;
        this.easing = easing;
    }
    visit(visitor, context) {
        return visitor.visitAnimationStep(this, context);
    }
}
export class AnimationStylesAst extends AnimationAst {
    constructor(styles) {
        super();
        this.styles = styles;
    }
    visit(visitor, context) {
        return visitor.visitAnimationStyles(this, context);
    }
}
export class AnimationKeyframeAst extends AnimationAst {
    constructor(offset, styles) {
        super();
        this.offset = offset;
        this.styles = styles;
    }
    visit(visitor, context) {
        return visitor.visitAnimationKeyframe(this, context);
    }
}
export class AnimationWithStepsAst extends AnimationAst {
    constructor(steps) {
        super();
        this.steps = steps;
    }
}
export class AnimationGroupAst extends AnimationWithStepsAst {
    constructor(steps) {
        super(steps);
    }
    visit(visitor, context) {
        return visitor.visitAnimationGroup(this, context);
    }
}
export class AnimationSequenceAst extends AnimationWithStepsAst {
    constructor(steps) {
        super(steps);
    }
    visit(visitor, context) {
        return visitor.visitAnimationSequence(this, context);
    }
}
//# sourceMappingURL=animation_ast.js.map