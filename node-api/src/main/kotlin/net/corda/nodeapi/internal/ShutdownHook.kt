package net.corda.nodeapi.internal

interface ShutdownHook {
    /**
     * Safe to call from the block passed into [addShutdownHook].
     */
    fun cancel()
}

/**
 * The given block will run on most kinds of termination including SIGTERM, but not on SIGKILL.
 * @return An object via which you can cancel the hook.
 */
fun addShutdownHook(block: () -> Unit): ShutdownHook {
    val hook = Thread { block() }
    val runtime = Runtime.getRuntime()
    runtime.addShutdownHook(hook)
    return object : ShutdownHook {
        override fun cancel() {
            // Allow the block to call cancel without causing IllegalStateException in the shutdown case:
            if (Thread.currentThread() != hook) {
                runtime.removeShutdownHook(hook)
            }
        }
    }
}
