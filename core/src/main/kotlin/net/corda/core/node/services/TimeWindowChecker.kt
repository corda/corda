/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.core.node.services

import net.corda.core.DeleteForDJVM
import net.corda.core.contracts.TimeWindow
import java.time.Clock

/**
 * Checks if the current instant provided by the input clock falls within the provided time-window.
 */
@Deprecated("This class is no longer used")
@DeleteForDJVM
class TimeWindowChecker(val clock: Clock = Clock.systemUTC()) {
    fun isValid(timeWindow: TimeWindow): Boolean = clock.instant() in timeWindow
}
