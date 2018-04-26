package net.corda.node.serialization.amqp

import net.corda.core.concurrent.CordaFuture
import net.corda.core.toObservable
import net.corda.nodeapi.internal.serialization.amqp.CustomSerializer
import net.corda.nodeapi.internal.serialization.amqp.SerializerFactory
import rx.Observable
import java.io.NotSerializableException

class RpcServerCordaFutureSerialiser (factory: SerializerFactory)
    : CustomSerializer.Proxy<CordaFuture<*>,
        RpcServerCordaFutureSerialiser.FutureProxy>(
        CordaFuture::class.java, RpcServerCordaFutureSerialiser.FutureProxy::class.java, factory
) {
    override fun fromProxy(proxy: RpcServerCordaFutureSerialiser.FutureProxy): CordaFuture<*> {
        throw UnsupportedOperationException()
    }

    override fun toProxy(obj: CordaFuture<*>): RpcServerCordaFutureSerialiser.FutureProxy {
        try {
            return FutureProxy(obj.toObservable())
        }
        catch (e: NotSerializableException) {
            throw (NotSerializableException("Failed to serialize Future as proxy Observable - ${e.message}"))
        }
    }

    data class FutureProxy(val observable: Observable<*>)
}



