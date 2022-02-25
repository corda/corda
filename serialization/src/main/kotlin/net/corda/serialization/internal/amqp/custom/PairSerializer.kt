package net.corda.serialization.internal.amqp.custom

import net.corda.core.KeepForDJVM
import net.corda.serialization.internal.amqp.CustomSerializer
import net.corda.serialization.internal.amqp.SerializerFactory

class PairSerializer(
    factory: SerializerFactory
) : CustomSerializer.Proxy<Pair<*, *>, PairSerializer.PairProxy>(
    Pair::class.java,
    PairProxy::class.java,
    factory
) {

    override fun toProxy(obj: Pair<*, *>): PairProxy = PairProxy(obj.first, obj.second)

    override fun fromProxy(proxy: PairProxy): Pair<*, *> = Pair(proxy.first, proxy.second)

    @KeepForDJVM
    data class PairProxy(val first: Any?, val second: Any?)
}
