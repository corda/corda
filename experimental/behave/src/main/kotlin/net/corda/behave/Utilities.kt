/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.behave

import java.time.Duration
import java.util.concurrent.CountDownLatch

fun CountDownLatch.await(duration: Duration) =
        this.await(duration.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS)

fun Process.waitFor(duration: Duration) =
        this.waitFor(duration.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS)
