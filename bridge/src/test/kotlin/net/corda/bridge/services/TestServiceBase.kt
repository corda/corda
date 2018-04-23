/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.bridge.services

import net.corda.bridge.services.api.ServiceLifecycleSupport
import net.corda.bridge.services.util.ServiceStateHelper
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