package net.corda.node.serialization.amqp

import net.corda.core.concurrent.CordaFuture
import net.corda.core.toObservable
import net.corda.serialization.internal.amqp.CustomSerializer
import net.corda.serialization.internal.amqp.SerializerFactory
import rx.Observable
import java.io.NotSerializableException

/**
 * Serializer for [CordaFuture] objects where Futures are converted to Observables and
 * are thus dealt with by the [RpcServerObservableSerializer]
 */
class RpcServerCordaFutureSerializer(factory: SerializerFactory)
    : CustomSerializer.Proxy<CordaFuture<*>,
        RpcServerCordaFutureSerializer.FutureProxy>(
        CordaFuture::class.java, RpcServerCordaFutureSerializer.FutureProxy::class.java, factory
) {
    override fun fromProxy(proxy: RpcServerCordaFutureSerializer.FutureProxy): CordaFuture<*> {
        throw UnsupportedOperationException()
    }

    override fun toProxy(obj: CordaFuture<*>): RpcServerCordaFutureSerializer.FutureProxy {
        try {
            return FutureProxy(obj.toObservable())
        } catch (e: NotSerializableException) {
            throw (NotSerializableException("Failed to serialize Future as proxy Observable - ${e.message}"))
        }
    }

    data class FutureProxy(val observable: Observable<*>)
}



