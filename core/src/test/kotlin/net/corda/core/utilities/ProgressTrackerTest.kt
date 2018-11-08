package net.corda.core.utilities

import org.assertj.core.api.Assertions.assertThat
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

    object BabySteps {
        object UNOS : ProgressTracker.Step("unos")
        object DOES : ProgressTracker.Step("does")
        object TRES : ProgressTracker.Step("tres")

        fun tracker() = ProgressTracker(UNOS, DOES, TRES)
    }

    lateinit var pt: ProgressTracker
    lateinit var pt2: ProgressTracker
    lateinit var pt3: ProgressTracker

    @Before
    fun before() {
        pt = SimpleSteps.tracker()
        pt2 = ChildSteps.tracker()
        pt3 = BabySteps.tracker()
    }

    @Test
    fun `check basic steps`() {
        assertEquals(ProgressTracker.UNSTARTED, pt.currentStep)
        assertEquals(0, pt.stepIndex)
        var stepNotification: ProgressTracker.Step? = null
        pt.changes.subscribe { stepNotification = (it as? ProgressTracker.Change.Position)?.newStep }
        assertEquals(ProgressTracker.UNSTARTED, pt.currentStep)
        assertEquals(ProgressTracker.STARTING, pt.nextStep())
        assertEquals(SimpleSteps.ONE, pt.nextStep())
        assertEquals(2, pt.stepIndex)
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

        assertEquals(pt2.currentStep, ProgressTracker.UNSTARTED)
        assertEquals(ProgressTracker.STARTING, pt2.nextStep())
        assertEquals(ChildSteps.AYY, pt2.nextStep())
        assertEquals((stepNotification.last as ProgressTracker.Change.Position).newStep, ChildSteps.AYY)
        assertEquals(ChildSteps.BEE, pt2.nextStep())
    }

    @Test
    fun `steps tree index counts children steps`() {
        pt.setChildProgressTracker(SimpleSteps.TWO, pt2)

        val allSteps = pt.allSteps

        // Capture notifications.
        val stepsIndexNotifications = LinkedList<Int>()
        pt.stepsTreeIndexChanges.subscribe {
            stepsIndexNotifications += it
        }
        val stepsTreeNotification = LinkedList<List<Pair<Int, String>>>()
        pt.stepsTreeChanges.subscribe {
            stepsTreeNotification += it
        }

        fun assertCurrentStepsTree(index: Int, step: ProgressTracker.Step) {
            assertEquals(index, pt.stepsTreeIndex)
            assertEquals(step, allSteps[pt.stepsTreeIndex].second)
        }

        // Travel tree.
        pt.currentStep = SimpleSteps.ONE
        assertCurrentStepsTree(1, SimpleSteps.ONE)

        pt.currentStep = SimpleSteps.TWO
        assertCurrentStepsTree(2, SimpleSteps.TWO)

        pt2.currentStep = ChildSteps.BEE
        assertCurrentStepsTree(5, ChildSteps.BEE)

        pt.currentStep = SimpleSteps.THREE
        assertCurrentStepsTree(7, SimpleSteps.THREE)

        // Assert no structure changes and proper steps propagation.
        assertThat(stepsIndexNotifications).containsExactlyElementsOf(listOf(1, 2, 5, 7))
        assertThat(stepsTreeNotification).isEmpty()
    }

    @Test
    fun `steps tree index counts two levels of children steps`() {
        pt.setChildProgressTracker(SimpleSteps.FOUR, pt2)
        pt2.setChildProgressTracker(ChildSteps.SEA, pt3)
        val allSteps = pt.allSteps

        // Capture notifications.
        val stepsIndexNotifications = LinkedList<Int>()
        pt.stepsTreeIndexChanges.subscribe {
            stepsIndexNotifications += it
        }
        val stepsTreeNotification = LinkedList<List<Pair<Int, String>>>()
        pt.stepsTreeChanges.subscribe {
            stepsTreeNotification += it
        }

        fun assertCurrentStepsTree(index: Int, step: ProgressTracker.Step) {
            assertEquals(index, pt.stepsTreeIndex)
            assertEquals(step, allSteps[pt.stepsTreeIndex].second)
        }

        pt.currentStep = SimpleSteps.ONE
        assertCurrentStepsTree(1, SimpleSteps.ONE)

        pt.currentStep = SimpleSteps.FOUR
        assertCurrentStepsTree(4, SimpleSteps.FOUR)

        pt2.currentStep = ChildSteps.SEA
        assertCurrentStepsTree(8, ChildSteps.SEA)

        // Assert no structure changes and proper steps propagation.
        assertThat(stepsIndexNotifications).containsExactlyElementsOf(listOf(1, 4, 8))
        assertThat(stepsTreeNotification).isEmpty()
    }

    @Test
    fun `structure changes are pushed down when progress trackers are added`() {
        pt.setChildProgressTracker(SimpleSteps.TWO, pt2)

        // Capture notifications.
        val stepsIndexNotifications = LinkedList<Int>()
        pt.stepsTreeIndexChanges.subscribe {
            stepsIndexNotifications += it
        }

        // Put current state as a first change for simplicity when asserting.
        val stepsTreeNotification = mutableListOf(pt.allStepsLabels)
        println(pt.allStepsLabels)
        pt.stepsTreeChanges.subscribe {
            stepsTreeNotification += it
        }

        fun assertCurrentStepsTree(index: Int, step: ProgressTracker.Step) {
            assertEquals(index, pt.stepsTreeIndex)
            assertEquals(step.label, stepsTreeNotification.last()[pt.stepsTreeIndex].second)
        }

        pt.currentStep = SimpleSteps.TWO
        assertCurrentStepsTree(2, SimpleSteps.TWO)

        pt.currentStep = SimpleSteps.FOUR
        assertCurrentStepsTree(8, SimpleSteps.FOUR)


        pt.setChildProgressTracker(SimpleSteps.THREE, pt3)

        assertCurrentStepsTree(12, SimpleSteps.FOUR)

        // Assert no structure changes and proper steps propagation.
        assertThat(stepsIndexNotifications).containsExactlyElementsOf(listOf(2, 8, 12))
        assertThat(stepsTreeNotification).hasSize(2) // 1 change + 1 our initial state
    }

    @Test
    fun `structure changes are pushed down when progress trackers are removed`() {
        pt.setChildProgressTracker(SimpleSteps.TWO, pt2)

        // Capture notifications.
        val stepsIndexNotifications = LinkedList<Int>()
        pt.stepsTreeIndexChanges.subscribe {
            stepsIndexNotifications += it
        }

        // Put current state as a first change for simplicity when asserting.
        val stepsTreeNotification = mutableListOf(pt.allStepsLabels)
        pt.stepsTreeChanges.subscribe {
            stepsTreeNotification += it
        }

        fun assertCurrentStepsTree(index: Int, step: ProgressTracker.Step) {
            assertEquals(index, pt.stepsTreeIndex)
            assertEquals(step.label, stepsTreeNotification.last()[pt.stepsTreeIndex].second)
        }

        pt.currentStep = SimpleSteps.TWO
        pt2.currentStep = ChildSteps.SEA
        pt3.currentStep = BabySteps.UNOS
        assertCurrentStepsTree(6, ChildSteps.SEA)

        pt.setChildProgressTracker(SimpleSteps.TWO, pt3)

        assertCurrentStepsTree(4, BabySteps.UNOS)

        // Assert no structure changes and proper steps propagation.
        assertThat(stepsIndexNotifications).containsExactlyElementsOf(listOf(2, 6, 4))
        assertThat(stepsTreeNotification).hasSize(2) // 1 change + 1 our initial state.
    }

    @Test
    fun `can be rewound`() {
        pt.setChildProgressTracker(SimpleSteps.TWO, pt2)
        repeat(4) { pt.nextStep() }
        pt.currentStep = SimpleSteps.ONE
        assertEquals(SimpleSteps.TWO, pt.nextStep())
    }
}
