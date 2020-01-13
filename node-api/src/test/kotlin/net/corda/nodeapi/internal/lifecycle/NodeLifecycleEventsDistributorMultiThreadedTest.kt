package net.corda.nodeapi.internal.lifecycle

import com.nhaarman.mockito_kotlin.mock
import net.corda.core.internal.stream
import net.corda.core.utilities.Try
import net.corda.core.utilities.contextLogger
import org.junit.Test
import net.corda.nodeapi.internal.lifecycle.NodeLifecycleObserver.Companion.reportSuccess
import java.util.concurrent.atomic.AtomicLong
import kotlin.test.assertTrue

internal class NodeLifecycleEventsDistributorMultiThreadedTest {

    companion object {
        private val logger = contextLogger()
    }

    private val instance = NodeLifecycleEventsDistributor()

    private val addedCounter = AtomicLong()

    private val eventsDeliveredCounter = AtomicLong()

    @Test
    fun addAndDistributeConcurrently() {

        val initialObserversCount = 10
        repeat(initialObserversCount) { instance.add(MyObserver(it)) }

        val operationsCount = 100_000
        val event = NodeLifecycleEvent.BeforeStart(mock())
        val additionFreq = 100
        (1..operationsCount).stream(true).forEach {
            if(it % additionFreq == 0) {
                logger.debug("Adding observer")
                instance.add(MyObserver(it))
                addedCounter.incrementAndGet()
            }
            logger.debug("Distributing event")
            instance.distributeEvent(event)
        }

        with(eventsDeliveredCounter.get()) {
            // Greater than original observers times events
            assertTrue("$this") { this > initialObserversCount.toLong() * operationsCount }
            // Less than ever added observers times events
            assertTrue("$this") { this < (initialObserversCount.toLong() + addedCounter.get()) * operationsCount }
        }
    }

    inner class MyObserver(seqNum: Int) : NodeLifecycleObserver {
        override val priority: Int = seqNum % 10

        override fun update(nodeLifecycleEvent: NodeLifecycleEvent): Try<String> = Try.on {
            eventsDeliveredCounter.incrementAndGet()
            reportSuccess(nodeLifecycleEvent)
        }
    }
}