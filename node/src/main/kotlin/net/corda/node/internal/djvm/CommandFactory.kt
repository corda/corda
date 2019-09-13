package net.corda.node.internal.djvm

import net.corda.djvm.rewiring.SandboxClassLoader
import net.corda.node.djvm.CommandBuilder
import java.util.function.Function

class CommandFactory(
    private val classLoader: SandboxClassLoader,
    private val taskFactory: Function<in Any, out Function<in Any?, out Any?>>
) {
    fun toSandbox(signers: Any?, commands: Any?, partialMerkleLeafIndices: IntArray?): Any? {
        val builder = classLoader.createTaskFor(taskFactory, CommandBuilder::class.java)
        return builder.apply(arrayOf(
            signers,
            commands,
            partialMerkleLeafIndices
        ))
    }
}
