package net.corda.tools.error.codes.server.commons.domain.identity

import java.time.Instant

abstract class AbstractId<out VALUE : Any>(val value: VALUE, val entityType: String?, val timestamp: Instant) {

    final override fun equals(other: Any?): Boolean {

        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as AbstractId<*>

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