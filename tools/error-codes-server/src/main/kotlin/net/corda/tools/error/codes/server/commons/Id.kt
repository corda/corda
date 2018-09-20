package net.corda.tools.error.codes.server.commons

import java.time.Instant
import java.time.Instant.now

open class Id<out VALUE : Any>(val value: VALUE, val entityType: String?, val timestamp: Instant) {

    companion object {

        @JvmStatic
        fun <V : Any> newInstance(value: V, entityType: String? = null, timestamp: Instant = now()) = Id(value, entityType, timestamp)
    }

    final override fun equals(other: Any?): Boolean {

        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Id<*>

        if (value != other.value) return false
        if (entityType != other.entityType) return false

        return true
    }

    final override fun hashCode(): Int {

        var result = value.hashCode()
        result = 31 * result + (entityType?.hashCode() ?: 0)
        return result
    }

    final override fun toString(): String {

        return "$value, timestamp: $timestamp" + (entityType?.let { ", entityType: $it" } ?: "")
    }
}