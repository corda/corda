package net.corda.tools.error.codes.server.domain

sealed class PlatformEdition(val description: String) {

    object OpenSource : PlatformEdition("OS")

    object Enterprise : PlatformEdition("ENT")
}