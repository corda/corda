package net.corda.core

interface ShutdownHook {
    fun cancel()
}

fun atexit(block: () -> Unit): ShutdownHook {
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
