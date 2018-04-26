/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.behave.service

import net.corda.core.utilities.loggerFor
import java.io.Closeable

abstract class Service(
        val name: String,
        val port: Int,
        val settings: ServiceSettings = ServiceSettings()
) : Closeable {

    private var isRunning: Boolean = false

    protected val log = loggerFor<Service>()

    fun start(): Boolean {
        if (isRunning) {
            log.warn("{} is already running", this)
            return false
        }
        log.info("Starting {} ...", this)
        checkPrerequisites()
        if (!startService()) {
            log.warn("Failed to start {}", this)
            return false
        }
        isRunning = true
        Thread.sleep(settings.startupDelay.toMillis())
        return if (!waitUntilStarted()) {
            log.warn("Failed to start {}", this)
            stop()
            false
        } else if (!verify()) {
            log.warn("Failed to verify start-up of {}", this)
            stop()
            false
        } else {
            log.info("{} started and available", this)
            true
        }
    }

    fun stop() {
        if (!isRunning) {
            return
        }
        log.info("Stopping {} ...", this)
        if (stopService()) {
            log.info("{} stopped", this)
            isRunning = false
        } else {
            log.warn("Failed to stop {}", this)
        }
    }

    override fun close() {
        stop()
    }

    override fun toString() = "Service(name = $name, port = $port)"

    protected open fun checkPrerequisites() { }

    protected open fun startService() = true

    protected open fun stopService() = true

    protected open fun verify() = true

    protected open fun waitUntilStarted() = true

}