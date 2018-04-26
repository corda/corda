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

import net.corda.core.utilities.minutes
import net.corda.core.utilities.seconds
import java.time.Duration

data class ServiceSettings(
        val timeout: Duration = 1.minutes,
        val startupDelay: Duration = 1.seconds,
        val startupTimeout: Duration = 15.seconds,
        val pollInterval: Duration = 1.seconds
)
