package net.corda.core.internal.cordapp

import net.corda.core.internal.cordapp.CordappImpl.Companion.MIN_PLATFORM_VERSION
import net.corda.core.internal.cordapp.CordappImpl.Companion.TARGET_PLATFORM_VERSION
import java.util.jar.Attributes
import java.util.jar.Manifest

operator fun Manifest.set(key: String, value: String): String? = mainAttributes.putValue(key, value)

operator fun Manifest.set(key: Attributes.Name, value: String): Any? = mainAttributes.put(key, value)

operator fun Manifest.get(key: String): String? = mainAttributes.getValue(key)

val Manifest.targetPlatformVersion: Int
    get() {
        val minPlatformVersion = this[MIN_PLATFORM_VERSION]?.toIntOrNull() ?: 1
        return this[TARGET_PLATFORM_VERSION]?.toIntOrNull() ?: minPlatformVersion
    }
