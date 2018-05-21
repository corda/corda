/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.node.internal

import java.lang.reflect.InvocationHandler
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method

/**
 * Helps writing correct [InvocationHandler]s.
 */
internal interface InvocationHandlerTemplate : InvocationHandler {
    val delegate: Any

    override fun invoke(proxy: Any, method: Method, arguments: Array<out Any?>?): Any? {
        val args = arguments ?: emptyArray()
        return try {
            method.invoke(delegate, *args)
        } catch (e: InvocationTargetException) {
            throw e.targetException
        }
    }
}