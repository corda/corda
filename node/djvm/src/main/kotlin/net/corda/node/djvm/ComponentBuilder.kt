package net.corda.node.djvm

import net.corda.core.contracts.ComponentGroupEnum
import net.corda.core.internal.TransactionDeserialisationException
import net.corda.core.internal.lazyMapped
import net.corda.core.utilities.OpaqueBytes
import java.util.function.Function
import java.util.function.Supplier

class ComponentBuilder : Function<Array<Any?>, Supplier<List<*>>> {
    @Suppress("unchecked_cast", "TooGenericExceptionCaught")
    override fun apply(inputs: Array<Any?>): Supplier<List<*>> {
        val deserializer = inputs[0] as Function<in Any?, out Any?>
        val groupType = inputs[1] as ComponentGroupEnum
        val components = (inputs[2] as Array<ByteArray>).map(::OpaqueBytes)

        return Supplier {
            components.lazyMapped { component, index ->
                try {
                    deserializer.apply(component.bytes)
                } catch (e: Exception) {
                    throw TransactionDeserialisationException(groupType, index, e)
                }
            }
        }
    }
}
