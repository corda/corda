package net.corda.tools.error.codes.server.domain

import net.corda.tools.error.codes.server.commons.domain.identity.set
import org.apache.commons.lang3.builder.ToStringBuilder
import org.apache.commons.lang3.builder.ToStringStyle
import java.net.URI

sealed class ErrorDescriptionLocation {

    class External(val uri: URI) : ErrorDescriptionLocation() {

        override fun equals(other: Any?): Boolean {

            if (this === other) {
                return true
            }
            if (javaClass != other?.javaClass) {
                return false
            }
            other as External
            if (uri != other.uri) {
                return false
            }
            return true
        }

        override fun hashCode() = uri.hashCode()

        override fun toString(): String {

            val toString = ToStringBuilder(ToStringStyle.SHORT_PREFIX_STYLE)
            toString["uri"] = uri.toString()
            return toString.build()
        }
    }
}
