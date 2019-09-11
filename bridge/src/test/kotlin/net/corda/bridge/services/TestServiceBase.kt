package net.corda.bridge.services

import net.corda.nodeapi.internal.lifecycle.ServiceLifecycleSupport
import net.corda.nodeapi.internal.lifecycle.ServiceStateHelper
import org.slf4j.helpers.NOPLogger
import rx.Observable

open class TestServiceBase() : ServiceLifecycleSupport {
    private val stateHelper: ServiceStateHelper = ServiceStateHelper(NOPLogger.NOP_LOGGER)

    override val active: Boolean
        get() = stateHelper.active

    override val activeChange: Observable<Boolean>
        get() = stateHelper.activeChange

    override fun start() {
        stateHelper.active = true
    }

    override fun stop() {
        stateHelper.active = false
    }
}