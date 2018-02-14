package net.corda.behave.service

import net.corda.behave.minute
import net.corda.behave.second
import net.corda.behave.seconds
import java.time.Duration

data class ServiceSettings(
        val timeout: Duration = 1.minute,
        val startupDelay: Duration = 1.second,
        val startupTimeout: Duration = 15.seconds,
        val pollInterval: Duration = 1.second
)
