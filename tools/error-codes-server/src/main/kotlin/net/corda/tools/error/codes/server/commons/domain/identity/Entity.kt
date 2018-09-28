package net.corda.tools.error.codes.server.commons.domain.identity

import org.apache.commons.lang3.builder.ToStringBuilder
import org.apache.commons.lang3.builder.ToStringStyle
import java.time.Instant

abstract class Entity<ID : AbstractId<*>>(val id: ID) {

    val createdAt: Instant = id.timestamp

    final override fun equals(other: Any?): Boolean {

        if (this === other) {
            return true
        }
        if (javaClass != other?.javaClass) {
            return false
        }

        other as Entity<*>

        if (id != other.id) {
            return false
        }

        return true
    }

    final override fun hashCode(): Int {

        return id.hashCode()
    }

    final override fun toString(): String {

        return ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE).also(::appendToStringElements).build()
    }

    protected open fun appendToStringElements(toString: ToStringBuilder) {

        toString["id"] = id.value
        toString["createdAt"] = createdAt
    }
}