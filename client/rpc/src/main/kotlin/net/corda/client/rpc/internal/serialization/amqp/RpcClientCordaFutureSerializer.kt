package net.corda.client.rpc.internal.serialization.amqp

import net.corda.core.concurrent.CordaFuture
import net.corda.core.toFuture
import net.corda.core.toObservable
import net.corda.nodeapi.internal.serialization.amqp.CustomSerializer
import net.corda.nodeapi.internal.serialization.amqp.SerializerFactory
import rx.Observable
import java.io.NotSerializableException

class RpcClientCordaFutureSerializer (factory: SerializerFactory)
    : CustomSerializer.Proxy<CordaFuture<*>, RpcClientCordaFutureSerializer.FutureProxy>(
        CordaFuture::class.java,
        RpcClientCordaFutureSerializer.FutureProxy::class.java, factory
) {
    override fun fromProxy(proxy: FutureProxy): CordaFuture<*> {
        try {
            return proxy.observable.toFuture()
        } catch (e: NotSerializableException) {
            throw (NotSerializableException("Failed to deserialize Future from proxy Observable - ${e.message}"))
        }
    }

    override fun toProxy(obj: CordaFuture<*>): FutureProxy {
        throw UnsupportedOperationException()
    }

    data class FutureProxy(val observable: Observable<*>)
}