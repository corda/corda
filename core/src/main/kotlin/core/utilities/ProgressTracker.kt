/*
 * Copyright 2015 Distributed Ledger Group LLC.  Distributed as Licensed Company IP to DLG Group Members
 * pursuant to the August 7, 2015 Advisory Services Agreement and subject to the Company IP License terms
 * set forth therein.
 *
 * All other rights reserved.
 */

package core.utilities

import core.TransientProperty
import rx.Observable
import rx.Subscription
import rx.lang.kotlin.BehaviourSubject
import rx.subjects.PublishSubject
import java.util.*

// TODO: Expose the concept of errors.
// TODO: It'd be helpful if this class was at least partly thread safe.

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
class ProgressTracker(vararg steps: Step) {
    sealed class Change {
        class Position(val newStep: Step) : Change()
        class Rendering(val ofStep: Step) : Change()
        class Structural(val parent: Step) : Change()
    }

    /** The superclass of all step objects. */
    open class Step(open val label: String) {
        open val changes: Observable<Change> = Observable.empty()
    }

    /** This class makes it easier to relabel a step on the fly, to provide transient information. */
    open class RelabelableStep(currentLabel: String) : Step(currentLabel) {
        override val changes = BehaviourSubject<Change>()

        var currentLabel: String = currentLabel
            set(value) {
                field = value
                changes.onNext(ProgressTracker.Change.Rendering(this))
            }

        override val label: String get() = currentLabel
    }

    // Sentinel objects. Overrides equals() to survive process restarts and serialization.
    object UNSTARTED : Step("Unstarted") {
        override fun equals(other: Any?) = other is UNSTARTED
    }
    object DONE : Step("Done") {
        override fun equals(other: Any?) = other is DONE
    }

    /** The steps in this tracker, same as the steps passed to the constructor but with UNSTARTED and DONE inserted. */
    val steps = arrayOf(UNSTARTED, *steps, DONE)

    /** The zero-based index of the current step in the [steps] array (i.e. with UNSTARTED and DONE) */
    var stepIndex: Int = 0
        private set

    /**
     * Reading returns the value of steps[stepIndex], writing moves the position of the current tracker. Once moved to
     * the [DONE] state, this tracker is finished and the current step cannot be moved again.
     */
    var currentStep: Step
        get() = steps[stepIndex]
        set(value) {
            if (currentStep != value) {
                check(currentStep != DONE) { "Cannot rewind a progress tracker once it reaches the done state" }

                val index = steps.indexOf(value)
                require(index != -1)

                if (index < stepIndex) {
                    // We are going backwards: unlink and unsubscribe from any child nodes that we're rolling back
                    // through, in preparation for moving through them again.
                    for (i in stepIndex downTo index) {
                        childrenFor.remove(steps[i])
                    }
                }

                curChangeSubscription?.unsubscribe()
                stepIndex = index
                _changes.onNext(Change.Position(steps[index]))
                curChangeSubscription = currentStep.changes.subscribe { _changes.onNext(it) }

                if (currentStep == DONE) _changes.onCompleted()
            }
        }

    /** Returns the current step, descending into children to find the deepest step we are up to. */
    val currentStepRecursive: Step
        get() = childrenFor[currentStep]?.currentStepRecursive ?: currentStep

    /**
     * Writable map that lets you insert child [ProgressTracker]s for particular steps. It's OK to edit this even
     * after a progress tracker has been started.
     */
    var childrenFor = object : HashMap<Step, ProgressTracker>() {
        override fun put(key: Step, value: ProgressTracker): ProgressTracker? {
            val r = super.put(key, value)
            childSubscriptions[value] = value.changes.subscribe({ _changes.onNext(it) }, { _changes.onError(it) })
            _changes.onNext(Change.Structural(key))
            return r
        }

        override fun remove(key: Step): ProgressTracker? {
            if (containsKey(key))
                childSubscriptions[this[key]]?.let { it.unsubscribe(); childSubscriptions.remove(this[key]) }
            _changes.onNext(Change.Structural(key))
            return super.remove(key)
        }
    }

    private val childSubscriptions = HashMap<ProgressTracker, Subscription>()

    private fun _allSteps(level: Int = 0): List<Pair<Int, Step>> {
        val result = ArrayList<Pair<Int, Step>>()
        for (step in steps) {
            if (step == UNSTARTED) continue
            if (level > 0 && step == DONE) continue
            result += Pair(level, step)
            childrenFor[step]?.let { result += it._allSteps(level + 1) }
        }
        return result
    }

    /**
     * A list of all steps in this ProgressTracker and the children, with the indent level provided starting at zero.
     * Note that UNSTARTED is never counted, and DONE is only counted at the calling level.
     */
    val allSteps: List<Pair<Int, Step>> get() = _allSteps()

    private var curChangeSubscription: Subscription? = null

    /**
     * Iterates the progress tracker. If the current step has a child, the child is iterated instead (recursively).
     * Returns the latest step at the bottom of the step tree.
     */
    fun nextStep(): Step {
        currentStep = steps[steps.indexOf(currentStep) + 1]
        return currentStep
    }

    // This field won't be serialized.
    private val _changes by TransientProperty { PublishSubject.create<Change>() }

    /**
     * An observable stream of changes: includes child steps, resets and any changes emitted by individual steps (e.g.
     * if a step changed its label or rendering).
     */
    val changes: Observable<Change> get() = _changes
}