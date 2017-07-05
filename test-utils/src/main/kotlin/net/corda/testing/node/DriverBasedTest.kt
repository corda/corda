package net.corda.testing.node

import com.google.common.util.concurrent.SettableFuture
import net.corda.core.concurrent.getOrThrow
import net.corda.testing.driver.DriverDSLExposedInterface
import org.junit.After
import org.junit.Before
import java.util.concurrent.CountDownLatch
import kotlin.concurrent.thread

abstract class DriverBasedTest {
    private val stopDriver = CountDownLatch(1)
    private var driverThread: Thread? = null
    private lateinit var driverStarted: SettableFuture<Unit>

    protected sealed class RunTestToken {
        internal object Token : RunTestToken()
    }

    protected abstract fun setup(): RunTestToken

    protected fun DriverDSLExposedInterface.runTest(): RunTestToken {
        driverStarted.set(Unit)
        stopDriver.await()
        return RunTestToken.Token
    }

    @Before
    fun start() {
        driverStarted = SettableFuture.create()
        driverThread = thread {
            try {
                setup()
            } catch (t: Throwable) {
                driverStarted.setException(t)
            }
        }
        driverStarted.getOrThrow()
    }

    @After
    fun stop() {
        stopDriver.countDown()
        driverThread?.join()
    }
}