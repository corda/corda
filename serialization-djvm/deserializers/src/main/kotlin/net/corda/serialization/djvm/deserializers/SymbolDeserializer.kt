package net.corda.serialization.djvm.deserializers

import org.apache.qpid.proton.amqp.Symbol
import java.util.function.Function

class SymbolDeserializer : Function<String, Symbol> {
    override fun apply(value: String): Symbol {
        return Symbol.valueOf(value)
    }
}
