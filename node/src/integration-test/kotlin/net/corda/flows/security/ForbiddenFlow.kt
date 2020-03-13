package net.corda.flows.security

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.StartableByRPC
import java.security.AccessController.doPrivileged
import java.security.PrivilegedAction

@StartableByRPC
class ForbiddenFlow : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        /**
         * Our [SecurityManager] should prevent a class
         * from reaching the application's [ClassLoader].
         * Nor should using [doPrivileged] change this,
         * because this flow should be running without
         * any privileges of its own.
         */
        doPrivileged(PrivilegedAction {
            var loader = this::class.java.classLoader
            while (true) {
                loader = loader.parent ?: break
            }
        })
    }
}
