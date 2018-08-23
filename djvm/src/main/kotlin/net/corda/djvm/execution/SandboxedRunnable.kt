package net.corda.djvm.execution

/**
 * Functionality runnable by a sandbox executor.
 */
interface SandboxedRunnable<in TInput, out TOutput> : DiscoverableRunnable {

    /**
     * The entry point of the sandboxed functionality to be run.
     *
     * @param input The input to pass in to the entry point.
     *
     * @returns The output to pass back to the caller after the sandboxed code has finished running.
     * @throws Exception The function can throw an exception, in which case the exception gets passed to the caller.
     */
    @Throws(Exception::class)
    fun run(input: TInput): TOutput?

}
