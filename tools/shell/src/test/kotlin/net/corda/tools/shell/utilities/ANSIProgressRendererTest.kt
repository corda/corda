package net.corda.tools.shell.utilities

import com.nhaarman.mockito_kotlin.argumentCaptor
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.verify
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

        private val STEP_1_SUCCESS_OUTPUT = if (SystemUtils.IS_OS_WINDOWS) """DONE: $STEP_1_LABEL""" else """✓ $STEP_1_LABEL"""
        private const val STEP_2_SKIPPED_OUTPUT = """  $INTENSITY_FAINT_ON_ASCII$STEP_2_LABEL$INTENSITY_OFF_ASCII"""
        private val STEP_3_ACTIVE_OUTPUT  = if (SystemUtils.IS_OS_WINDOWS) """CURRENT: $INTENSITY_BOLD_ON_ASCII$STEP_3_LABEL$INTENSITY_OFF_ASCII""" else """"▶︎$INTENSITY_BOLD_ON_ASCII$STEP_3_LABEL$INTENSITY_OFF_ASCII"""
    }

    lateinit var printWriter: RenderPrintWriter
    lateinit var progressRenderer: ANSIProgressRenderer

    @Before
    fun setup() {
        printWriter = mock()
        progressRenderer = CRaSHANSIProgressRenderer(printWriter)
    }

    @Test
    fun `test that steps are rendered appropriately depending on their status`() {
        val indexSubject = PublishSubject.create<Int>()
        val feedSubject = PublishSubject.create<List<Pair<Int, String>>>()
        val stepsTreeIndexFeed = DataFeed<Int, Int>(0, indexSubject)
        val stepsTreeFeed = DataFeed<List<Pair<Int, String>>, List<Pair<Int, String>>>(listOf(), feedSubject)
        val flowProgressHandle = FlowProgressHandleImpl(StateMachineRunId.createRandom(), openFuture<String>(), Observable.empty(), stepsTreeIndexFeed, stepsTreeFeed)

        progressRenderer.render(flowProgressHandle)
        // The flow is currently at step 3, while step 1 has been completed and step 2 has been skipped.
        indexSubject.onNext(2)
        feedSubject.onNext(listOf(Pair(0, STEP_1_LABEL), Pair(0, STEP_2_LABEL), Pair(0, STEP_3_LABEL)))

        val captor = argumentCaptor<Ansi>()
        verify(printWriter).print(captor.capture())
        assertThat(captor.firstValue.toString()).containsSequence(STEP_1_SUCCESS_OUTPUT, STEP_2_SKIPPED_OUTPUT, STEP_3_ACTIVE_OUTPUT)
        verify(printWriter).flush()
    }

}