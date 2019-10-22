package net.corda.serialization.djvm.deserializers

import net.corda.core.utilities.OpaqueBytes
import net.corda.core.utilities.OpaqueBytesSubSequence
import java.util.function.Function

class OpaqueBytesSubSequenceDeserializer : Function<OpaqueBytes, OpaqueBytesSubSequence> {
    override fun apply(proxy: OpaqueBytes): OpaqueBytesSubSequence {
        return OpaqueBytesSubSequence(proxy.bytes, proxy.offset, proxy.size)
    }
}
