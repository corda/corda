package net.corda.node.services.config.schema.parsers

import com.typesafe.config.Config
import com.typesafe.config.ConfigObject
import com.typesafe.config.ConfigUtil
import net.corda.common.configuration.parsing.internal.Configuration
import net.corda.common.validation.internal.Validated.Companion.invalid
import net.corda.common.validation.internal.Validated.Companion.valid
import net.corda.core.identity.CordaX500Name
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.node.services.config.Valid
import java.net.MalformedURLException
import java.net.URL
import java.nio.file.InvalidPathException
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*
import javax.security.auth.x500.X500Principal

internal fun toProperties(rawValue: ConfigObject): Properties = rawValue.toConfig().toProperties()

private fun Config.toProperties() = entrySet().associateByTo(Properties(), { ConfigUtil.splitPath(it.key).joinToString(".") }, { it.value.unwrapped().toString() })

internal fun toCordaX500Name(rawValue: String) = attempt<CordaX500Name, IllegalArgumentException> { CordaX500Name.parse(rawValue) }

internal fun toURL(rawValue: String) = attempt<URL, MalformedURLException> { URL(rawValue) }

internal fun toUUID(rawValue: String) = attempt<UUID, IllegalArgumentException> { UUID.fromString(rawValue) }

internal fun toNetworkHostAndPort(rawValue: String) = attempt<NetworkHostAndPort, IllegalArgumentException> { NetworkHostAndPort.parse(rawValue) }

internal fun toPrincipal(rawValue: String) = attempt<X500Principal, IllegalArgumentException> { X500Principal(rawValue) }

internal fun toPath(rawValue: String) = attempt<Path, InvalidPathException> { Paths.get(rawValue) }

private inline fun <RESULT, reified ERROR : Exception> attempt(action: () -> RESULT, message: (ERROR) -> String): Valid<RESULT> {
    return try {
        valid(action.invoke())
    } catch (e: Exception) {
        when (e) {
            is ERROR -> badValue(message.invoke(e))
            else -> throw e
        }
    }
}

internal inline fun <reified RESULT, reified ERROR : Exception> attempt(action: () -> RESULT) = attempt(action, { e: ERROR -> "value does not comply with ${RESULT::class.java.simpleName} specification (${e.message})" })

internal fun <RESULT> validValue(result: RESULT) = valid<RESULT, Configuration.Validation.Error>(result)

internal fun <RESULT> badValue(message: String) = invalid<RESULT, Configuration.Validation.Error>(Configuration.Validation.Error.BadValue.of(message))