/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.node.utilities

import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory
import java.util.concurrent.atomic.AtomicInteger


/**
 * Utility class that allows to give threads arbitrary name prefixes when they are created
 * via an executor. It will use an underlying thread factory to create the actual thread
 * and then override the thread name with the prefix and an ever increasing number
 */
class NamedThreadFactory(private val name:String, private val underlyingFactory: ThreadFactory) : ThreadFactory{
    val threadNumber = AtomicInteger(1)
    override fun newThread(runnable: Runnable?): Thread {
        val thread = underlyingFactory.newThread(runnable)
        thread.name = name + "-" + threadNumber.getAndIncrement()
        return thread
    }
}

/**
 * Create a single thread executor with a NamedThreadFactory based on the default thread factory
 * defined in java.util.concurrent.Executors
 */
fun newNamedSingleThreadExecutor(name: String): ExecutorService {
    return Executors.newSingleThreadExecutor(NamedThreadFactory(name, Executors.defaultThreadFactory()))
}
