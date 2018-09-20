package net.corda.tools.error.codes.server.commons.web

data class Port(val value: Int) {

    init {
        require(value > 0)
    }
}