package net.corda.node.utilities.artemis

import net.corda.core.utilities.getOrThrow
import org.apache.activemq.artemis.core.server.ActivateCallback
import org.apache.activemq.artemis.core.server.ActiveMQServer
import java.util.concurrent.CompletableFuture

fun ActiveMQServer.startSynchronously() {
    val startupFuture = CompletableFuture<Unit>()
    registerActivateCallback(object: ActivateCallback {
        override fun activationComplete() {
            startupFuture.complete(Unit)
        }
    })
    registerActivationFailureListener {
        startupFuture.completeExceptionally(it)
    }

    start()

    startupFuture.getOrThrow()
}