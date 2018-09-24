package net.corda.tools.error.codes.server.application

internal data class CacheSize(val value: Long) {

    init {
        require(value > 0)
    }
}