/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.core.utilities

import net.corda.core.internal.STRUCTURAL_STEP_PREFIX
import net.corda.core.serialization.CordaSerializable
import rx.Observable
import rx.Subscription
import rx.subjects.PublishSubject
import java.util.*

/**
 * A progress tracker helps surface information about the progress of an operation to a user interface or API of some
 * kind. It lets you define a set of _steps_ that represent an operation. A step is represented by an object (typically
 * a singleton).
 *
 * Steps may logically be children of other steps, which models the case where a large top level operation involves
 * sub-operations which may also have a notion of progress. If a step has children, then the tracker will report the
 * steps children as the "next step" after the parent. In other words, a parent step is considered to involve actual
 * reportable work and is a thing. If the parent step simply groups other steps, then you'll have to step over it
 * manually.
 *
 * Each step has a label. It is assumed by default that the label does not change. If you want a label to change, then
 * you can emit a [ProgressTracker.Change.Rendering] object on the [ProgressTracker.Step.changes] observable stream
 * after it changes. That object will propagate through to the top level trackers [changes] stream, which renderers can
 * subscribe to in order to learn about progress.
 *
 * An operation can move both forwards and backwards through steps, thus, a [ProgressTracker] can represent operations
 * that include loops.
 *
 * A progress tracker is *not* thread safe. You may move events from the thread making progress to another thread by
 * using the [Observable] subscribeOn call.
 */
@CordaSerializable
class ProgressTracker(vararg steps: Step) {
    @CordaSerializable
    sealed class Change(val progressTracker: ProgressTracker) {
        data class Position(val tracker: ProgressTracker,  val newStep: Step) : Change(tracker) {
            override fun toString() = newStep.label
        }

        data class Rendering(val tracker: ProgressTracker, val ofStep: Step) : Change(tracker) {
            override fun toString() = ofStep.label
        }

        data class Structural(val tracker: ProgressTracker, val parent: Step) : Change(tracker) {
            override fun toString() = STRUCTURAL_STEP_PREFIX + parent.label
        }
    }

    /** The superclass of all step objects. */
    @CordaSerializable
    open class Step(open val label: String) {
        open val changes: Observable<Change> get() = Observable.empty()
        open fun childProgressTracker(): ProgressTracker? = null
        /**
         * A flow may populate this property with flow specific context data.
         * The extra data will be recorded to the audit logs when the flow progresses.
         * Even if empty the basic details (i.e. label) of the step will be recorded for audit purposes.
         */
        open val extraAuditData: Map<String, String> get() = emptyMap()
    }

    // Sentinel objects. Overrides equals() to survive process restarts and serialization.
    object UNSTARTED : Step("Unstarted") {
        override fun equals(other: Any?) = other === UNSTARTED
    }

    object DONE : Step("Done") {
        override fun equals(other: Any?) = other === DONE
    }

    @CordaSerializable
    private data class Child(val tracker: ProgressTracker, @Transient val subscription: Subscription?)

    private val childProgressTrackers = mutableMapOf<Step, Child>()

    /** The steps in this tracker, same as the steps passed to the constructor but with UNSTARTED and DONE inserted. */
    val steps = arrayOf(UNSTARTED, *steps, DONE)

    private var _allStepsCache: List<Pair<Int, Step>> = _allSteps()

    // This field won't be serialized.
    private val _changes by transient { PublishSubject.create<Change>() }
    private val _stepsTreeChanges by transient { PublishSubject.create<List<Pair<Int, String>>>() }
    private val _stepsTreeIndexChanges by transient { PublishSubject.create<Int>() }



    init {
        steps.forEach {
            val childTracker = it.childProgressTracker()
            if (childTracker != null) {
                setChildProgressTracker(it, childTracker)
            }
        }
    }

    /** The zero-based index of the current step in the [steps] array (i.e. with UNSTARTED and DONE) */
    var stepIndex: Int = 0
        private set(value) {
            field = value
        }

    /** The zero-bases index of the current step in a [allStepsLabels] list */
    var stepsTreeIndex: Int = -1
        private set(value) {
            field = value
            _stepsTreeIndexChanges.onNext(value)
        }

    /**
     * Reading returns the value of steps[stepIndex], writing moves the position of the current tracker. Once moved to
     * the [DONE] state, this tracker is finished and the current step cannot be moved again.
     */
    var currentStep: Step
        get() = steps[stepIndex]
        set(value) {
            check(!hasEnded) { "Cannot rewind a progress tracker once it has ended" }
            if (currentStep == value) return

            val index = steps.indexOf(value)
            require(index != -1, { "Step ${value.label} not found in progress tracker." })

            if (index < stepIndex) {
                // We are going backwards: unlink and unsubscribe from any child nodes that we're rolling back
                // through, in preparation for moving through them again.
                for (i in stepIndex downTo index) {
                    removeChildProgressTracker(steps[i])
                }
            }

            curChangeSubscription?.unsubscribe()
            stepIndex = index
            _changes.onNext(Change.Position(this, steps[index]))
            recalculateStepsTreeIndex()
            curChangeSubscription = currentStep.changes.subscribe({
                _changes.onNext(it)
                if (it is Change.Structural || it is Change.Rendering) rebuildStepsTree() else recalculateStepsTreeIndex()
            }, { _changes.onError(it) })

            if (currentStep == DONE) {
                _changes.onCompleted()
                _stepsTreeIndexChanges.onCompleted()
                _stepsTreeChanges.onCompleted()
            }
        }

    /** Returns the current step, descending into children to find the deepest step we are up to. */
    val currentStepRecursive: Step
        get() = getChildProgressTracker(currentStep)?.currentStepRecursive ?: currentStep

    /** Returns the current step, descending into children to find the deepest started step we are up to. */
    private val currentStartedStepRecursive: Step
        get() {
            val step = getChildProgressTracker(currentStep)?.currentStartedStepRecursive ?: currentStep
            return if (step == UNSTARTED) currentStep else step
        }

    private fun currentStepRecursiveWithoutUnstarted(): Step {
        val stepRecursive = getChildProgressTracker(currentStep)?.currentStartedStepRecursive
        return if (stepRecursive == null || stepRecursive == UNSTARTED) currentStep else stepRecursive
    }

    fun getChildProgressTracker(step: Step): ProgressTracker? = childProgressTrackers[step]?.tracker

    fun setChildProgressTracker(step: ProgressTracker.Step, childProgressTracker: ProgressTracker) {
        val subscription = childProgressTracker.changes.subscribe({
            _changes.onNext(it)
            if (it is Change.Structural || it is Change.Rendering) rebuildStepsTree() else recalculateStepsTreeIndex()
        }, { _changes.onError(it) })
        childProgressTrackers[step] = Child(childProgressTracker, subscription)
        childProgressTracker.parent = this
        _changes.onNext(Change.Structural(this, step))
        rebuildStepsTree()
    }

    private fun removeChildProgressTracker(step: ProgressTracker.Step) {
        childProgressTrackers.remove(step)?.let {
            it.tracker.parent = null
            it.subscription?.unsubscribe()
        }
        _changes.onNext(Change.Structural(this, step))
        rebuildStepsTree()
    }

    /**
     * Ends the progress tracker with the given error, bypassing any remaining steps. [changes] will emit the exception
     * as an error.
     */
    fun endWithError(error: Throwable) {
        check(!hasEnded) { "Progress tracker has already ended" }
        _changes.onError(error)
        _stepsTreeIndexChanges.onError(error)
        _stepsTreeChanges.onError(error)
    }

    /** The parent of this tracker: set automatically by the parent when a tracker is added as a child */
    var parent: ProgressTracker? = null
        private set

    /** Walks up the tree to find the top level tracker. If this is the top level tracker, returns 'this' */
    @Suppress("unused") // TODO: Review by EOY2016 if this property is useful anywhere.
    val topLevelTracker: ProgressTracker
        get() {
            var cursor: ProgressTracker = this
            while (cursor.parent != null) cursor = cursor.parent!!
            return cursor
        }

    private fun rebuildStepsTree() {
        _allStepsCache = _allSteps()
        _stepsTreeChanges.onNext(allStepsLabels)

        recalculateStepsTreeIndex()
    }

    private fun recalculateStepsTreeIndex() {
        val step = currentStepRecursiveWithoutUnstarted()
        stepsTreeIndex = _allStepsCache.indexOfFirst { it.second == step }
    }

    private fun _allSteps(level: Int = 0): List<Pair<Int, Step>> {
        val result = ArrayList<Pair<Int, Step>>()
        for (step in steps) {
            if (step == UNSTARTED) continue
            if (level > 0 && step == DONE) continue
            result += Pair(level, step)
            getChildProgressTracker(step)?.let { result += it._allSteps(level + 1) }
        }
        return result
    }

    private fun _allStepsLabels(level: Int = 0): List<Pair<Int, String>> = _allSteps(level).map { Pair(it.first, it.second.label) }

    /**
     * A list of all steps in this ProgressTracker and the children, with the indent level provided starting at zero.
     * Note that UNSTARTED is never counted, and DONE is only counted at the calling level.
     */
    val allSteps: List<Pair<Int, Step>> get() = _allStepsCache

    /**
     * A list of all steps label in this ProgressTracker and the children, with the indent level provided starting at zero.
     * Note that UNSTARTED is never counted, and DONE is only counted at the calling level.
     */
    val allStepsLabels: List<Pair<Int, String>> get() = _allStepsLabels()

    private var curChangeSubscription: Subscription? = null

    /**
     * Iterates the progress tracker. If the current step has a child, the child is iterated instead (recursively).
     * Returns the latest step at the bottom of the step tree.
     */
    fun nextStep(): Step {
        currentStep = steps[steps.indexOf(currentStep) + 1]
        return currentStep
    }

    /**
     * An observable stream of changes: includes child steps, resets and any changes emitted by individual steps (e.g.
     * if a step changed its label or rendering).
     */
    val changes: Observable<Change> get() = _changes

    /**
     * An observable stream of changes to the [allStepsLabels]
     */
    val stepsTreeChanges: Observable<List<Pair<Int,String>>> get() = _stepsTreeChanges

    /**
     * An observable stream of changes to the [stepsTreeIndex]
     */
    val stepsTreeIndexChanges: Observable<Int> get() = _stepsTreeIndexChanges

    /** Returns true if the progress tracker has ended, either by reaching the [DONE] step or prematurely with an error */
    val hasEnded: Boolean get() = _changes.hasCompleted() || _changes.hasThrowable()
}
// TODO: Expose the concept of errors.
// TODO: It'd be helpful if this class was at least partly thread safe.



