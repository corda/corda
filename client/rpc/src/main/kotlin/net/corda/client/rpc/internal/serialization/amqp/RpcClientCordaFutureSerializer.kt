package net.corda.client.rpc.internal.serialization.amqp

import net.corda.core.concurrent.CordaFuture
import net.corda.core.toFuture
import net.corda.core.toObservable
import net.corda.nodeapi.internal.serialization.amqp.CustomSerializer
import net.corda.nodeapi.internal.serialization.amqp.SerializerFactory
import rx.Observable

class RpcClientCordaFutureSerializer (factory: SerializerFactory)
    : CustomSerializer.Proxy<CordaFuture<*>, Observable<*>>(
        CordaFuture::class.java, Observable::class.java, factory
) {
    override fun fromProxy(proxy: Observable<*>): CordaFuture<*> {
        return proxy.toFuture()
    }

    override fun toProxy(obj: CordaFuture<*>): Observable<*> {
        throw UnsupportedOperationException()
    }
}