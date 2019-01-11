package net.corda.tools.shell.utilities

import com.nhaarman.mockito_kotlin.*
import net.corda.core.flows.StateMachineRunId
import net.corda.core.internal.concurrent.openFuture
import net.corda.core.messaging.DataFeed
import net.corda.core.messaging.FlowProgressHandleImpl
import net.corda.tools.shell.utlities.ANSIProgressRenderer
import net.corda.tools.shell.utlities.CRaSHANSIProgressRenderer
import org.apache.commons.lang.SystemUtils
import org.assertj.core.api.Assertions.assertThat
import org.crsh.text.RenderPrintWriter
import org.junit.Test
import rx.Observable
import org.fusesource.jansi.Ansi
import org.junit.Before
import rx.subjects.PublishSubject

class ANSIProgressRendererTest {

    companion object {
        private const val INTENSITY_BOLD_ON_ASCII = "[1m"
        private const val INTENSITY_OFF_ASCII = "[22m"
        private const val INTENSITY_FAINT_ON_ASCII = "[2m"

        private const val STEP_1_LABEL = "Running step 1"
        private const val STEP_2_LABEL = "Running step 2"
        private const val STEP_3_LABEL = "Running step 3"
        private const val STEP_4_LABEL = "Running step 4"
        private const val STEP_5_LABEL = "Running step 5"

        fun stepSuccess(stepLabel: String): String {
            return if (SystemUtils.IS_OS_WINDOWS) """DONE: $stepLabel""" else """✓ $stepLabel"""
        }

        fun stepSkipped(stepLabel: String): String {
            return """  $INTENSITY_FAINT_ON_ASCII$stepLabel$INTENSITY_OFF_ASCII"""
        }

        fun stepActive(stepLabel: String): String {
            return if (SystemUtils.IS_OS_WINDOWS) """CURRENT: $INTENSITY_BOLD_ON_ASCII$stepLabel$INTENSITY_OFF_ASCII""" else """▶︎ $INTENSITY_BOLD_ON_ASCII$stepLabel$INTENSITY_OFF_ASCII"""
        }
    }

    lateinit var printWriter: RenderPrintWriter
    lateinit var progressRenderer: ANSIProgressRenderer
    lateinit var indexSubject: PublishSubject<Int>
    lateinit var feedSubject: PublishSubject<List<Pair<Int, String>>>
    lateinit var flowProgressHandle: FlowProgressHandleImpl<*>

    @Before
    fun setup() {
        printWriter = mock()
        progressRenderer = CRaSHANSIProgressRenderer(printWriter)
        indexSubject = PublishSubject.create<Int>()
        feedSubject = PublishSubject.create<List<Pair<Int, String>>>()
        val stepsTreeIndexFeed = DataFeed<Int, Int>(0, indexSubject)
        val stepsTreeFeed = DataFeed<List<Pair<Int, String>>, List<Pair<Int, String>>>(listOf(), feedSubject)
        flowProgressHandle = FlowProgressHandleImpl(StateMachineRunId.createRandom(), openFuture<String>(), Observable.empty(), stepsTreeIndexFeed, stepsTreeFeed)
    }

    @Test
    fun `test that steps are rendered appropriately depending on their status`() {
        progressRenderer.render(flowProgressHandle)
        feedSubject.onNext(listOf(Pair(0, STEP_1_LABEL), Pair(0, STEP_2_LABEL), Pair(0, STEP_3_LABEL)))
        // The flow is currently at step 3, while step 1 has been completed and step 2 has been skipped.
        indexSubject.onNext(2)

        val captor = argumentCaptor<Ansi>()
        verify(printWriter, times(2)).print(captor.capture())
        assertThat(captor.secondValue.toString()).containsSequence(stepSuccess(STEP_1_LABEL), stepSkipped(STEP_2_LABEL), stepActive(STEP_3_LABEL))
        verify(printWriter, times(2)).flush()
    }

    @Test
    fun `changing tree causes correct steps to be marked as done`() {
        progressRenderer.render(flowProgressHandle)
        feedSubject.onNext(listOf(Pair(0, STEP_1_LABEL), Pair(1, STEP_2_LABEL), Pair(1, STEP_3_LABEL), Pair(0, STEP_4_LABEL), Pair(0, STEP_5_LABEL)))
        indexSubject.onNext(1)
        indexSubject.onNext(2)

        val captor = argumentCaptor<Ansi>()
        verify(printWriter, times(3)).print(captor.capture())
        assertThat(captor.lastValue.toString()).containsSequence(stepSuccess(STEP_1_LABEL), stepSuccess(STEP_2_LABEL), stepActive(STEP_3_LABEL))
        verify(printWriter, times(3)).flush()

        feedSubject.onNext(listOf(Pair(0, STEP_1_LABEL), Pair(0, STEP_4_LABEL), Pair(0, STEP_5_LABEL)))
        verify(printWriter, times(4)).print(captor.capture())
        assertThat(captor.lastValue.toString()).containsSequence(stepActive(STEP_1_LABEL))
        assertThat(captor.lastValue.toString()).doesNotContain(stepActive(STEP_5_LABEL))
        verify(printWriter, times(4)).flush()
    }
}