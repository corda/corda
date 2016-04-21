package core.utilities

import com.google.common.base.Preconditions.checkState
import com.google.common.util.concurrent.Uninterruptibles
import java.time.Duration
import java.util.*
import java.util.concurrent.*
import java.util.function.Supplier

/**
 * An extended executor interface that supports thread affinity assertions and short circuiting. This can be useful
 * for ensuring code runs on the right thread, and also for unit testing.
 */
interface AffinityExecutor : Executor {
    /** Returns true if the current thread is equal to the thread this executor is backed by. */
    val isOnThread: Boolean

    /** Throws an IllegalStateException if the current thread is equal to the thread this executor is backed by. */
    fun checkOnThread()

    /** If isOnThread() then runnable is invoked immediately, otherwise the closure is queued onto the backing thread. */
    fun executeASAP(runnable: () -> Unit)

    /** Terminates any backing thread (pool) without waiting for tasks to finish. */
    fun shutdownNow()

    /**
     * Runs the given function on the executor, blocking until the result is available. Be careful not to deadlock this
     * way! Make sure the executor can't possibly be waiting for the calling thread.
     */
    fun <T> fetchFrom(fetcher: () -> T): T {
        if (isOnThread)
            return fetcher()
        else
            return CompletableFuture.supplyAsync(Supplier { fetcher() }, this).get()
    }

    abstract class BaseAffinityExecutor protected constructor() : AffinityExecutor {
        protected val exceptionHandler: Thread.UncaughtExceptionHandler

        init {
            exceptionHandler = Thread.currentThread().uncaughtExceptionHandler
        }

        abstract override val isOnThread: Boolean

        override fun checkOnThread() {
            checkState(isOnThread, "On wrong thread: %s", Thread.currentThread())
        }

        override fun executeASAP(runnable: () -> Unit) {
            val command = {
                try {
                    runnable()
                } catch (throwable: Throwable) {
                    exceptionHandler.uncaughtException(Thread.currentThread(), throwable)
                }
            }
            if (isOnThread)
                command()
            else {
                execute(command)
            }
        }

        // Must comply with the Executor definition w.r.t. exceptions here.
        abstract override fun execute(command: Runnable)
    }

    /**
     * An executor backed by thread pool (which may often have a single thread) which makes it easy to schedule
     * tasks in the future and verify code is running on the executor.
     */
    class ServiceAffinityExecutor(threadName: String, numThreads: Int) : BaseAffinityExecutor() {
        protected val threads = Collections.synchronizedSet(HashSet<Thread>())

        private val handler = Thread.currentThread().uncaughtExceptionHandler
        val service: ScheduledThreadPoolExecutor

        init {
            val threadFactory = fun(runnable: Runnable): Thread {
                val thread = object : Thread() {
                    override fun run() {
                        runnable.run()
                        threads -= this
                    }
                }
                thread.isDaemon = true
                thread.name = threadName
                threads += thread
                return thread
            }
            // The scheduled variant of the JDK thread pool doesn't do automatic calibration of the thread pool size,
            // it always uses the 'core size'. So there is no point in allowing separate specification of core and max
            // numbers of threads.
            service = ScheduledThreadPoolExecutor(numThreads, threadFactory)
        }

        override val isOnThread: Boolean get() = Thread.currentThread() in threads

        override fun execute(command: Runnable) {
            service.execute {
                try {
                    command.run()
                } catch (e: Throwable) {
                    if (handler != null)
                        handler.uncaughtException(Thread.currentThread(), e)
                    else
                        e.printStackTrace()
                }
            }
        }

        fun <T> executeIn(time: Duration, command: () -> T): ScheduledFuture<T> {
            return service.schedule(Callable {
                try {
                    command()
                } catch (e: Throwable) {
                    if (handler != null)
                        handler.uncaughtException(Thread.currentThread(), e)
                    else
                        e.printStackTrace()
                    throw e
                }
            }, time.toMillis(), TimeUnit.MILLISECONDS)
        }

        override fun shutdownNow() {
            service.shutdownNow()
        }

        companion object {
            val logger = loggerFor<ServiceAffinityExecutor>()
        }
    }

    /**
     * An executor useful for unit tests: allows the current thread to block until a command arrives from another
     * thread, which is then executed. Inbound closures/commands stack up until they are cleared by looping.
     *
     * @param alwaysQueue If true, executeASAP will never short-circuit and will always queue up.
     */
    class Gate(private val alwaysQueue: Boolean = false) : BaseAffinityExecutor() {
        private val thisThread = Thread.currentThread()
        private val commandQ = LinkedBlockingQueue<Runnable>()

        override val isOnThread: Boolean
            get() = !alwaysQueue && Thread.currentThread() === thisThread

        override fun execute(command: Runnable) {
            Uninterruptibles.putUninterruptibly(commandQ, command)
        }

        fun waitAndRun() {
            val runnable = Uninterruptibles.takeUninterruptibly(commandQ)
            runnable.run()
        }

        val taskQueueSize: Int get() = commandQ.size

        override fun shutdownNow() {
        }
    }

    companion object {
        val SAME_THREAD: AffinityExecutor = object : BaseAffinityExecutor() {
            override val isOnThread: Boolean get() = true
            override fun execute(command: Runnable) = command.run()
            override fun shutdownNow() {
            }
        }
    }
}