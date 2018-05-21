package net.corda.nodeapi.internal.serialization.amqp.custom

import net.corda.nodeapi.internal.serialization.amqp.CustomSerializer
import net.corda.nodeapi.internal.serialization.amqp.SerializerFactory
import rx.Notification

class RxNotificationSerializer(
        factory: SerializerFactory
) : CustomSerializer.Proxy<rx.Notification<*>, RxNotificationSerializer.Proxy>(
        Notification::class.java,
        Proxy::class.java,
        factory
) {
    data class Proxy(
            val kind: Notification.Kind,
            val t: Throwable?,
            val value: Any?)

    override fun toProxy(obj: Notification<*>) = Proxy(obj.kind, obj.throwable, obj.value)

    override fun fromProxy(proxy: Proxy): Notification<*> {
        return when (proxy.kind) {
            Notification.Kind.OnCompleted -> Notification.createOnCompleted<Any>()
            Notification.Kind.OnError -> Notification.createOnError<Any>(proxy.t)
            Notification.Kind.OnNext -> Notification.createOnNext(proxy.value)
        }
    }
}