package net.corda.common.configuration.parsing.internal

import net.corda.common.validation.internal.Validated

data class Address(val host: String, val port: Int) {

    init {
        require(host.isNotBlank())
        require(port > 0)
    }

    companion object {

        fun <ERROR> validFromRawValue(rawValue: String, mapError: (String) -> ERROR): Validated<Address, ERROR> {

            val parts = rawValue.split(":")
            if (parts.size != 2 || parts[0].isBlank() || parts[1].isBlank() || parts[1].toIntOrNull() == null) {
                return Validated.invalid(sequenceOf("Value format is \"<host(String)>:<port:(Int)>\"").map(mapError).toSet())
            }
            val host = parts[0]
            val port = parts[1].toInt()
            if (port <= 0) {
                return Validated.invalid(sequenceOf("Port value must be greater than zero").map(mapError).toSet())
            }

            return Validated.valid(Address(host, port))
        }
    }
}