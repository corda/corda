package net.corda.node.internal.djvm

import net.corda.node.djvm.CommandBuilder
import java.util.function.Function

class CommandFactory(
    private val taskFactory: Function<Class<out Function<*,*>>, out Function<in Any?, out Any?>>
) {
    fun toSandbox(signers: Any?, commands: Any?, partialMerkleLeafIndices: IntArray?): Any? {
        val builder = taskFactory.apply(CommandBuilder::class.java)
        return builder.apply(arrayOf(
            signers,
            commands,
            partialMerkleLeafIndices
        ))
    }
}
