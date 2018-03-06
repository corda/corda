/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.node

import net.corda.core.internal.DeclaredField
import net.corda.core.internal.staticField
import net.corda.node.internal.Node
import java.lang.reflect.Method
import java.lang.reflect.Proxy

internal object SerialFilter {
    private val filterInterface: Class<*>
    private val serialClassGetter: Method
    private val undecided: Any
    private val rejected: Any
    private val serialFilterLock: Any
    private val serialFilterField: DeclaredField<Any>

    init {
        // ObjectInputFilter and friends are in java.io in Java 9 but sun.misc in backports:
        fun getFilterInterface(packageName: String): Class<*>? {
            return try {
                Class.forName("$packageName.ObjectInputFilter")
            } catch (e: ClassNotFoundException) {
                null
            }
        }
        // JDK 8u121 is the earliest JDK8 JVM that supports this functionality.
        filterInterface = getFilterInterface("java.io")
                ?: getFilterInterface("sun.misc")
                ?: Node.failStartUp("Corda forbids Java deserialisation. Please upgrade to at least JDK 8u121.")
        serialClassGetter = Class.forName("${filterInterface.name}\$FilterInfo").getMethod("serialClass")
        val statusEnum = Class.forName("${filterInterface.name}\$Status")
        undecided = statusEnum.getField("UNDECIDED").get(null)
        rejected = statusEnum.getField("REJECTED").get(null)
        val configClass = Class.forName("${filterInterface.name}\$Config")
        serialFilterLock = configClass.staticField<Any>("serialFilterLock").value
        serialFilterField = configClass.staticField("serialFilter")
    }

    internal fun install(acceptClass: (Class<*>) -> Boolean) {
        val filter = Proxy.newProxyInstance(javaClass.classLoader, arrayOf(filterInterface)) { _, _, args ->
            val serialClass = serialClassGetter.invoke(args[0]) as Class<*>?
            if (applyPredicate(acceptClass, serialClass)) {
                undecided
            } else {
                rejected
            }
        }
        // Can't simply use the setter as in non-trampoline mode Capsule has inited the filter in premain:
        synchronized(serialFilterLock) {
            serialFilterField.value = filter
        }
    }

    internal fun applyPredicate(acceptClass: (Class<*>) -> Boolean, serialClass: Class<*>?): Boolean {
        // Similar logic to jdk.serialFilter, our concern is side-effects at deserialisation time:
        if (null == serialClass) return true
        var componentType: Class<*> = serialClass
        while (componentType.isArray) componentType = componentType.componentType
        if (componentType.isPrimitive) return true
        return acceptClass(componentType)
    }
}

internal fun defaultSerialFilter(@Suppress("UNUSED_PARAMETER") clazz: Class<*>) = false
