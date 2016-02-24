/*
 * Copyright 2015 Distributed Ledger Group LLC.  Distributed as Licensed Company IP to DLG Group Members
 * pursuant to the August 7, 2015 Advisory Services Agreement and subject to the Company IP License terms
 * set forth therein.
 *
 * All other rights reserved.
 */

package core.utilities

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

    @Before
    fun before() {
        pt = SimpleSteps.tracker()
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
        assertFails { pt.nextStep() }
    }

    @Test
    fun `nested children are stepped correctly`() {
        val pt2 = ChildSteps.tracker()

        val stepNotification = LinkedList<ProgressTracker.Change>()
        pt.changes.subscribe {
            stepNotification += it
        }

        pt.currentStep = SimpleSteps.ONE
        pt.childrenFor[SimpleSteps.TWO] = pt2
        pt.nextStep()

        assertEquals(ChildSteps.AYY, pt.nextStep())
        assertEquals(ChildSteps.AYY, (stepNotification.pollFirst() as ProgressTracker.Change.Position).newStep)
        assertEquals(SimpleSteps.TWO, (stepNotification.pollFirst() as ProgressTracker.Change.Structural).parent)
        assertEquals(ChildSteps.BEE, pt2.nextStep())
    }

    @Test
    fun `can be rewound`() {
        val pt2 = ChildSteps.tracker()
        pt.childrenFor[SimpleSteps.TWO] = pt2
        repeat(4) { pt.nextStep() }
        pt.currentStep = SimpleSteps.ONE
        assertEquals(SimpleSteps.TWO, pt.nextStep())
    }
}