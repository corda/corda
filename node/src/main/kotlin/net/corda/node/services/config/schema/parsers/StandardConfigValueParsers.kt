package net.corda.node.services.config.schema.parsers

import com.typesafe.config.ConfigObject
import net.corda.core.identity.CordaX500Name
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.node.services.config.Valid
import java.net.URL
import java.nio.file.Path
import java.util.*
import javax.security.auth.x500.X500Principal

internal fun toLegalName(rawValue: String): Valid<CordaX500Name> {

    TODO("sollecitom not implemented")
}

internal fun toProperties(rawValue: ConfigObject): Properties {

    val properties = Properties()
    rawValue.entries.forEach { (key, value) ->
        properties[key] = value.unwrapped()
    }
    return properties
}

internal fun toUrl(rawValue: String): Valid<URL> {

    TODO("sollecitom not implemented")
}

internal fun toUuid(rawValue: String): Valid<UUID> {

    TODO("sollecitom not implemented")
}

internal fun toNetworkHostAndPort(rawValue: String): Valid<NetworkHostAndPort> {

    TODO("sollecitom not implemented")
}

internal fun toPrincipal(rawValue: String): Valid<X500Principal> {

    TODO("sollecitom not implemented")
}

internal fun toPath(rawValue: String): Valid<Path> {

    TODO("sollecitom not implemented")
}