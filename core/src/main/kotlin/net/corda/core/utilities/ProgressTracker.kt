package net.corda.core.utilities

import net.corda.core.TransientProperty
import net.corda.core.serialization.CordaSerializable
import rx.Observable
import rx.Subscription
import rx.subjects.BehaviorSubject
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
@CordaSerializable
class ProgressTracker(vararg steps: Step) {
    @CordaSerializable
    sealed class Change {
        data class Position(val tracker: ProgressTracker, val newStep: Step) : Change() {
            override fun toString() = newStep.label
        }

        data class Rendering(val tracker: ProgressTracker, val ofStep: Step) : Change() {
            override fun toString() = ofStep.label
        }

        data class Structural(val tracker: ProgressTracker, val parent: Step) : Change() {
            override fun toString() = "Structural step change in child of ${parent.label}"
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
        override fun equals(other: Any?) = other is UNSTARTED
    }

    object DONE : Step("Done") {
        override fun equals(other: Any?) = other is DONE
    }

    /** The steps in this tracker, same as the steps passed to the constructor but with UNSTARTED and DONE inserted. */
    val steps = arrayOf(UNSTARTED, *steps, DONE)

    // This field won't be serialized.
    private val _changes by TransientProperty { PublishSubject.create<Change>() }

    @CordaSerializable
    private data class Child(val tracker: ProgressTracker, @Transient val subscription: Subscription?)

    private val childProgressTrackers = mutableMapOf<Step, Child>()

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
        private set

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
            require(index != -1)

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
            curChangeSubscription = currentStep.changes.subscribe({ _changes.onNext(it) }, { _changes.onError(it) })

            if (currentStep == DONE) _changes.onCompleted()
        }

    /** Returns the current step, descending into children to find the deepest step we are up to. */
    val currentStepRecursive: Step
        get() = getChildProgressTracker(currentStep)?.currentStepRecursive ?: currentStep

    fun getChildProgressTracker(step: Step): ProgressTracker? = childProgressTrackers[step]?.tracker

    fun setChildProgressTracker(step: ProgressTracker.Step, childProgressTracker: ProgressTracker) {
        val subscription = childProgressTracker.changes.subscribe({ _changes.onNext(it) }, { _changes.onError(it) })
        childProgressTrackers[step] = Child(childProgressTracker, subscription)
        childProgressTracker.parent = this
        _changes.onNext(Change.Structural(this, step))
    }

    private fun removeChildProgressTracker(step: ProgressTracker.Step) {
        childProgressTrackers.remove(step)?.let {
            it.tracker.parent = null
            it.subscription?.unsubscribe()
        }
        _changes.onNext(Change.Structural(this, step))
    }

    /**
     * Ends the progress tracker with the given error, bypassing any remaining steps. [changes] will emit the exception
     * as an error.
     */
    fun endWithError(error: Throwable) {
        check(!hasEnded) { "Progress tracker has already ended" }
        _changes.onError(error)
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

    /**
     * An observable stream of changes: includes child steps, resets and any changes emitted by individual steps (e.g.
     * if a step changed its label or rendering).
     */
    val changes: Observable<Change> get() = _changes

    /** Returns true if the progress tracker has ended, either by reaching the [DONE] step or prematurely with an error */
    val hasEnded: Boolean get() = _changes.hasCompleted() || _changes.hasThrowable()
}


