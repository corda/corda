package net.corda.node.utilities

import com.google.common.util.concurrent.SettableFuture
import com.google.common.util.concurrent.Uninterruptibles
import net.corda.core.utilities.loggerFor
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

    /** Throws an IllegalStateException if the current thread is not one of the threads this executor is backed by. */
    fun checkOnThread() {
        if (!isOnThread)
            throw IllegalStateException("On wrong thread: " + Thread.currentThread())
    }

    /** If isOnThread() then runnable is invoked immediately, otherwise the closure is queued onto the backing thread. */
    fun executeASAP(runnable: () -> Unit) {
        if (isOnThread)
            runnable()
        else
            execute(runnable)
    }

    // TODO: Rename this to executeWithResult
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

    /**
     * Run the executor until there are no tasks pending and none executing.
     */
    fun flush()

    /**
     * An executor backed by thread pool (which may often have a single thread) which makes it easy to schedule
     * tasks in the future and verify code is running on the executor.
     */
    open class ServiceAffinityExecutor(threadName: String, numThreads: Int) : AffinityExecutor,
            ThreadPoolExecutor(numThreads, numThreads, 0L, TimeUnit.MILLISECONDS, LinkedBlockingQueue<Runnable>()) {
        companion object {
            val logger = loggerFor<ServiceAffinityExecutor>()
        }

        private val threads = Collections.synchronizedSet(HashSet<Thread>())
        private val uncaughtExceptionHandler = Thread.currentThread().uncaughtExceptionHandler

        init {
            setThreadFactory(fun(runnable: Runnable): Thread {
                val thread = object : Thread() {
                    override fun run() {
                        try {
                            runnable.run()
                        } finally {
                            threads -= this
                        }
                    }
                }
                thread.isDaemon = true
                thread.name = threadName
                threads += thread
                return thread
            })
        }

        override fun afterExecute(r: Runnable, t: Throwable?) {
            if (t != null)
                uncaughtExceptionHandler.uncaughtException(Thread.currentThread(), t)
        }

        override val isOnThread: Boolean get() = Thread.currentThread() in threads

        override fun flush() {
            do {
                val f = SettableFuture.create<Boolean>()
                execute { f.set(queue.isEmpty() && activeCount == 1) }
            } while (!f.get())
        }
    }

    /**
     * An executor useful for unit tests: allows the current thread to block until a command arrives from another
     * thread, which is then executed. Inbound closures/commands stack up until they are cleared by looping.
     *
     * @param alwaysQueue If true, executeASAP will never short-circuit and will always queue up.
     */
    class Gate(private val alwaysQueue: Boolean = false) : AffinityExecutor {
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

        override fun flush() {
            throw UnsupportedOperationException()
        }
    }
}
