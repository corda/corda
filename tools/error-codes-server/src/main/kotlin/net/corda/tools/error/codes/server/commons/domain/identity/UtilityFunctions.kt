package net.corda.tools.error.codes.server.commons.domain.identity

import org.apache.commons.lang3.builder.ToStringBuilder

operator fun ToStringBuilder.set(key: String, value: Any?) {

    append(key, value)
}