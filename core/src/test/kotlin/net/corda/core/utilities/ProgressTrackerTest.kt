package net.corda.core.utilities

import org.junit.Before
import org.junit.Test
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertFails

class ProgressTrackerTest {
    object SimpleSteps {
        object ONE : ProgressTracker.Step("one")
        object TWO : ProgressTracker.Step("two")
        object THREE : ProgressTracker.Step("three")
        object FOUR : ProgressTracker.Step("four")

        fun tracker() = ProgressTracker(ONE, TWO, THREE, FOUR)
    }

    object ChildSteps {
        object AYY : ProgressTracker.Step("ayy")
        object BEE : ProgressTracker.Step("bee")
        object SEA : ProgressTracker.Step("sea")

        fun tracker() = ProgressTracker(AYY, BEE, SEA)
    }

    lateinit var pt: ProgressTracker
    lateinit var pt2: ProgressTracker

    @Before
    fun before() {
        pt = SimpleSteps.tracker()
        pt2 = ChildSteps.tracker()
    }

    @Test
    fun `check basic steps`() {
        assertEquals(ProgressTracker.UNSTARTED, pt.currentStep)
        assertEquals(0, pt.stepIndex)
        var stepNotification: ProgressTracker.Step? = null
        pt.changes.subscribe { stepNotification = (it as? ProgressTracker.Change.Position)?.newStep }

        assertEquals(SimpleSteps.ONE, pt.nextStep())
        assertEquals(1, pt.stepIndex)
        assertEquals(SimpleSteps.ONE, stepNotification)

        assertEquals(SimpleSteps.TWO, pt.nextStep())
        assertEquals(SimpleSteps.THREE, pt.nextStep())
        assertEquals(SimpleSteps.FOUR, pt.nextStep())
        assertEquals(ProgressTracker.DONE, pt.nextStep())
    }

    @Test
    fun `cannot go beyond end`() {
        pt.currentStep = SimpleSteps.FOUR
        pt.nextStep()
        assertFails { pt.nextStep() }
    }

    @Test
    fun `nested children are stepped correctly`() {
        val stepNotification = LinkedList<ProgressTracker.Change>()
        pt.changes.subscribe {
            stepNotification += it
        }

        fun assertNextStep(step: ProgressTracker.Step) {
            assertEquals(step, (stepNotification.pollFirst() as ProgressTracker.Change.Position).newStep)
        }

        pt.currentStep = SimpleSteps.ONE
        assertNextStep(SimpleSteps.ONE)

        pt.setChildProgressTracker(SimpleSteps.TWO, pt2)
        pt.nextStep()
        assertEquals(SimpleSteps.TWO, (stepNotification.pollFirst() as ProgressTracker.Change.Structural).parent)
        assertNextStep(SimpleSteps.TWO)

        assertEquals(ChildSteps.AYY, pt2.nextStep())
        assertNextStep(ChildSteps.AYY)
        assertEquals(ChildSteps.BEE, pt2.nextStep())
    }

    @Test
    fun `can be rewound`() {
        pt.setChildProgressTracker(SimpleSteps.TWO, pt2)
        repeat(4) { pt.nextStep() }
        pt.currentStep = SimpleSteps.ONE
        assertEquals(SimpleSteps.TWO, pt.nextStep())
    }
}
