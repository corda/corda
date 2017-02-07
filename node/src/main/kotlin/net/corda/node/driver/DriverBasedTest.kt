package net.corda.node.driver

import org.junit.After
import org.junit.Before
import java.util.concurrent.CountDownLatch
import kotlin.concurrent.thread

abstract class DriverBasedTest {
    private val stopDriver = CountDownLatch(1)
    private var driverThread: Thread? = null
    private lateinit var driverStarted: CountDownLatch

    protected sealed class RunTestToken {
        internal object Token : RunTestToken()
    }

    protected abstract fun setup(): RunTestToken

    protected fun DriverDSLExposedInterface.runTest(): RunTestToken {
        driverStarted.countDown()
        stopDriver.await()
        return RunTestToken.Token
    }

    @Before
    fun start() {
        driverStarted = CountDownLatch(1)
        driverThread = thread {
            setup()
        }
        driverStarted.await()
    }

    @After
    fun stop() {
        stopDriver.countDown()
        driverThread?.join()
    }
}