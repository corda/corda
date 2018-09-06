package net.corda.djvm.execution

import net.corda.djvm.SandboxConfiguration
import net.corda.djvm.source.ClassSource

/**
 * The executor is responsible for spinning up a deterministic, sandboxed environment and launching the referenced code
 * block inside it. The code will run on a separate thread for complete isolation, and to enable context-based costing
 * of said code. Any exceptions should be forwarded to the caller of [SandboxExecutor.run]. Similarly, the returned
 * output from the referenced code block should be returned to the caller.
 *
 * @param configuration The configuration of the sandbox.
 */
class DeterministicSandboxExecutor<TInput, TOutput>(
        configuration: SandboxConfiguration = SandboxConfiguration.DEFAULT
) : SandboxExecutor<TInput, TOutput>(configuration) {

    /**
     * Short-hand for running a [SandboxedRunnable] in a sandbox by its type reference.
     */
    inline fun <reified TRunnable : SandboxedRunnable<TInput, TOutput>> run(input: TInput):
            ExecutionSummaryWithResult<TOutput?> {
        return run(ClassSource.fromClassName(TRunnable::class.java.name), input)
    }

}
