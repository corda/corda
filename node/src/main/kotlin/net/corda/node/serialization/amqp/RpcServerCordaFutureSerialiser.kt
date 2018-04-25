package net.corda.node.serialization.amqp

import net.corda.core.concurrent.CordaFuture
import net.corda.core.toObservable
import net.corda.nodeapi.internal.serialization.amqp.CustomSerializer
import net.corda.nodeapi.internal.serialization.amqp.SerializerFactory
import rx.Observable

class RpcServerCordaFutureSerialiser (factory: SerializerFactory)
    : CustomSerializer.Proxy<CordaFuture<*>,Observable<*>>(
        CordaFuture::class.java, Observable::class.java, factory
) {
    override fun fromProxy(proxy: Observable<*>): CordaFuture<*> {
        throw UnsupportedOperationException()
    }

    override fun toProxy(obj: CordaFuture<*>): Observable<*> {
        return obj.toObservable()
    }
}



