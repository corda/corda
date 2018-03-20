/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.bridge.services.api

import rx.Observable

/**
 * Basic interface to represent the dynamic life cycles of services that may be running, but may have to await external dependencies,
 * or for HA master state.
 * Implementations of this should be implemented in a thread safe fashion.
 */
interface ServiceStateSupport {
    /**
     * Reads the current dynamic status of the service, which should only become true after the service has been started,
     * any dynamic resources have been started/registered and any network connections have been completed.
     * Failure to acquire a resource, or manual stop of the service, should return this to false.
     */
    val active: Boolean

    /**
     * This Observer signals changes in the [active] variable, it should not be triggered for events that don't flip the [active] state.
     */
    val activeChange: Observable<Boolean>
}

/**
 * Simple interface for generic start/stop service lifecycle and the [active] flag indicating runtime ready state.
 */
interface ServiceLifecycleSupport : ServiceStateSupport, AutoCloseable {
    /**
     * Manual call to allow the service to start the process towards becoming active.
     * Note wiring up service dependencies should happen in the constructor phase, unless this is to avoid a circular reference.
     * Also, resources allocated as a result of start should be cleaned up as much as possible by stop.
     * The [start] method should allow multiple reuse, assuming a [stop] call was made to clear the state.
     */
    fun start()

    /**
     * Release the resources created by [start] and drops the [active] state to false.
     */
    fun stop()

    override fun close() = stop()

}