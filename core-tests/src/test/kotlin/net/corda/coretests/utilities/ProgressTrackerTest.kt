package net.corda.coretests.utilities

import net.corda.core.serialization.internal.checkpointDeserialize
import net.corda.core.serialization.internal.checkpointSerialize
import net.corda.core.utilities.ProgressTracker
import net.corda.coretests.utilities.ProgressTrackerTest.NonSingletonSteps.first
import net.corda.coretests.utilities.ProgressTrackerTest.NonSingletonSteps.first2
import net.corda.testing.core.internal.CheckpointSerializationEnvironmentRule
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertNotEquals

class ProgressTrackerTest {

    @Rule
    @JvmField
    val testCheckpointSerialization = CheckpointSerializationEnvironmentRule()

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
    lateinit var pt4: ProgressTracker

    @Before
    fun before() {
        pt = SimpleSteps.tracker()
        pt2 = ChildSteps.tracker()
        pt3 = BabySteps.tracker()
        pt4 = ChildSteps.tracker()
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
        assertCurrentStepsTree(4, ChildSteps.BEE)

        pt.currentStep = SimpleSteps.THREE
        assertCurrentStepsTree(6, SimpleSteps.THREE)

        // Assert no structure changes and proper steps propagation.
        assertThat(stepsIndexNotifications).containsExactlyElementsOf(listOf(0, 1, 2, 4, 6))
        assertThat(stepsTreeNotification).hasSize(2) // The initial tree state, plus one per tree update
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
        assertCurrentStepsTree(7, ChildSteps.SEA)

        // Assert no structure changes and proper steps propagation.
        assertThat(stepsIndexNotifications).containsExactlyElementsOf(listOf(0, 1, 4, 7))
        assertThat(stepsTreeNotification).hasSize(3) // The initial tree state, plus one per update
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
        val stepsTreeNotification = mutableListOf<List<Pair<Int, String>>>()
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
        assertCurrentStepsTree(7, SimpleSteps.FOUR)


        pt.setChildProgressTracker(SimpleSteps.THREE, pt3)

        assertCurrentStepsTree(10, SimpleSteps.FOUR)

        // Assert no structure changes and proper steps propagation.
        assertThat(stepsIndexNotifications).containsExactlyElementsOf(listOf(0, 2, 7, 10))
        assertThat(stepsTreeNotification).hasSize(3) // The initial tree state, plus one per update.
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
        val stepsTreeNotification = mutableListOf<List<Pair<Int, String>>>()
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
        assertCurrentStepsTree(5, ChildSteps.SEA)

        pt.setChildProgressTracker(SimpleSteps.TWO, pt3)

        assertCurrentStepsTree(3, BabySteps.UNOS)

        // Assert no structure changes and proper steps propagation.
        assertThat(stepsIndexNotifications).containsExactlyElementsOf(listOf(0, 2, 5, 3))
        assertThat(stepsTreeNotification).hasSize(3) // The initial tree state, plus one per update
    }

    @Test
    fun `can be rewound`() {
        pt.setChildProgressTracker(SimpleSteps.TWO, pt2)
        repeat(4) { pt.nextStep() }
        pt.currentStep = SimpleSteps.ONE
        assertEquals(SimpleSteps.TWO, pt.nextStep())
    }

    @Test
    fun `all index changes seen if subscribed mid flow`() {
        pt.setChildProgressTracker(SimpleSteps.TWO, pt2)

        pt.currentStep = SimpleSteps.ONE
        pt.currentStep = SimpleSteps.TWO

        val stepsIndexNotifications = LinkedList<Int>()
        pt.stepsTreeIndexChanges.subscribe {
            stepsIndexNotifications += it
        }

        pt2.currentStep = ChildSteps.AYY

        assertThat(stepsIndexNotifications).containsExactlyElementsOf(listOf(0, 1, 2, 3))
    }

    @Test
    fun `all step changes seen if subscribed mid flow`() {
        val steps = mutableListOf<String>()
        pt.nextStep()
        pt.nextStep()
        pt.nextStep()
        pt.changes.subscribe { steps.add(it.toString()) }
        pt.nextStep()
        pt.nextStep()
        pt.nextStep()
        assertEquals(listOf("Starting", "one", "two", "three", "four", "Done"), steps)
    }

    @Test
    fun `all tree changes seen if subscribed mid flow`() {
        val stepTreeNotifications = mutableListOf<List<Pair<Int, String>>>()
        val firstStepLabels = pt.allStepsLabels

        pt.setChildProgressTracker(SimpleSteps.TWO, pt2)
        val secondStepLabels = pt.allStepsLabels

        pt.setChildProgressTracker(SimpleSteps.TWO, pt3)
        val thirdStepLabels = pt.allStepsLabels
        pt.stepsTreeChanges.subscribe { stepTreeNotifications.add(it) }

        // Should have one notification for original tree, then one for each time it changed.
        assertEquals(3, stepTreeNotifications.size)
        assertEquals(listOf(firstStepLabels, secondStepLabels, thirdStepLabels), stepTreeNotifications)
    }

    @Test
    fun `trees with child trackers with duplicate steps reported correctly`() {
        val stepTreeNotifications = mutableListOf<List<Pair<Int, String>>>()
        val stepIndexNotifications = mutableListOf<Int>()
        pt.stepsTreeChanges.subscribe { stepTreeNotifications += it }
        pt.stepsTreeIndexChanges.subscribe { stepIndexNotifications += it }
        pt.setChildProgressTracker(SimpleSteps.ONE, pt2)
        pt.setChildProgressTracker(SimpleSteps.TWO, pt4)

        pt.currentStep = SimpleSteps.ONE
        pt2.currentStep = ChildSteps.AYY
        pt2.nextStep()
        pt2.nextStep()
        pt.nextStep()
        pt4.currentStep = ChildSteps.AYY

        assertEquals(listOf(0, 1, 2, 3, 4, 5, 6), stepIndexNotifications)
    }

    @Test
    fun `cannot assign step not belonging to this progress tracker`() {
        assertFails { pt.currentStep = BabySteps.UNOS }
    }

    object NonSingletonSteps {
        val first = ProgressTracker.Step("first")
        val second = ProgressTracker.Step("second")
        val first2 = ProgressTracker.Step("first")
        fun tracker() = ProgressTracker(first, second, first2)
    }

    @Test
    fun `Serializing and deserializing a tracker maintains equality`() {
        val step = NonSingletonSteps.first
        val recreatedStep = step
                .checkpointSerialize(testCheckpointSerialization.checkpointSerializationContext)
                .checkpointDeserialize(testCheckpointSerialization.checkpointSerializationContext)
        assertEquals(step, recreatedStep)
    }

    @Test
    fun `can assign a recreated equal step`() {
        val tracker = NonSingletonSteps.tracker()
        val recreatedStep = first
                .checkpointSerialize(testCheckpointSerialization.checkpointSerializationContext)
                .checkpointDeserialize(testCheckpointSerialization.checkpointSerializationContext)
        tracker.currentStep = recreatedStep
    }

    @Test
    fun `Steps with the same label defined in different places are not equal`() {
        val one = ProgressTracker.Step("one")
        assertNotEquals(one, SimpleSteps.ONE)
    }

    @Test
    fun `Steps with the same label defined in the same place are also not equal`() {
        assertNotEquals(first, first2)
    }
}
