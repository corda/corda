package net.corda.behave.service

import net.corda.core.utilities.minutes
import net.corda.core.utilities.seconds
import java.time.Duration

data class ServiceSettings(
        val timeout: Duration = 1.minutes,
        val startupDelay: Duration = 1.seconds,
        val startupTimeout: Duration = 15.seconds,
        val pollInterval: Duration = 1.seconds
)
