package net.corda.nodeapi.internal.network

import com.typesafe.config.Config
import net.corda.common.configuration.parsing.internal.Configuration
import net.corda.common.configuration.parsing.internal.get
import net.corda.common.configuration.parsing.internal.mapValid
import net.corda.common.configuration.parsing.internal.nested
import net.corda.common.validation.internal.Validated
import net.corda.core.internal.noPackageOverlap
import net.corda.core.internal.requirePackageValid
import net.corda.nodeapi.internal.crypto.loadKeyStore
import java.io.IOException
import java.nio.file.InvalidPathException
import java.nio.file.Path
import java.nio.file.Paths
import java.security.KeyStoreException

typealias Valid<TARGET> = Validated<TARGET, Configuration.Validation.Error>

fun Config.parseAsNetworkParametersConfiguration(options: Configuration.Validation.Options = Configuration.Validation.Options(strict = false)):
        Valid<NetworkParametersOverrides> = NetworkParameterOverridesSpec.parse(this, options)

internal fun <T> badValue(msg: String): Valid<T> = Validated.invalid(sequenceOf(Configuration.Validation.Error.BadValue.of(msg)).toSet())
internal fun <T> valid(value: T): Valid<T> = Validated.valid(value)

internal object NetworkParameterOverridesSpec : Configuration.Specification<NetworkParametersOverrides>("DefaultNetworkParameters") {
    private val minimumPlatformVersion by int().mapValid(NetworkParameterOverridesSpec::parsePositiveInteger).optional()
    private val maxMessageSize by int().mapValid(NetworkParameterOverridesSpec::parsePositiveInteger).optional()
    private val maxTransactionSize by int().mapValid(NetworkParameterOverridesSpec::parsePositiveInteger).optional()
    private val packageOwnership by nested(PackageOwnershipSpec).list().optional()
    private val eventHorizon by duration().optional()

    internal object PackageOwnershipSpec : Configuration.Specification<PackageOwner>("PackageOwners") {
        private val packageName by string().mapValid(PackageOwnershipSpec::toPackageName)
        private val keystore by string().mapValid(PackageOwnershipSpec::toPath)
        private val keystorePassword by string()
        private val keystoreAlias by string()

        override fun parseValid(configuration: Config): Validated<PackageOwner, Configuration.Validation.Error> {
            val suppliedKeystorePath = configuration[keystore]
            val keystorePassword = configuration[keystorePassword]
            return try {
                val javaPackageName = configuration[packageName]
                val absoluteKeystorePath = if (suppliedKeystorePath.isAbsolute) {
                    suppliedKeystorePath
                } else {
                    //If a relative path is supplied, make it relative to the location of the config file
                    Paths.get(configuration.origin().filename()).resolveSibling(suppliedKeystorePath.toString())
                }.toAbsolutePath()
                val ks = loadKeyStore(absoluteKeystorePath, keystorePassword)
                return try {
                    val publicKey = ks.getCertificate(configuration[keystoreAlias]).publicKey
                    valid(PackageOwner(javaPackageName, publicKey))
                } catch (kse: KeyStoreException) {
                    badValue("Keystore has not been initialized for alias ${configuration[keystoreAlias]}.")
                }
            } catch (kse: KeyStoreException) {
                badValue("Password is incorrect or the key store is damaged for keyStoreFilePath: $suppliedKeystorePath.")
            } catch (e: IOException) {
                badValue("Error reading the key store from the file for keyStoreFilePath: $suppliedKeystorePath ${e.message}.")
            }
        }

        private fun toPackageName(rawValue: String): Validated<String, Configuration.Validation.Error> {
            return try {
                requirePackageValid(rawValue)
                valid(rawValue)
            } catch (e: Exception) {
                return badValue(e.message ?: e.toString())
            }
        }

        private fun toPath(rawValue: String): Validated<Path, Configuration.Validation.Error> {
            return try {
                valid(Paths.get(rawValue))
            } catch (e: InvalidPathException) {
                return badValue("Path $rawValue not found")
            }
        }
    }

    override fun parseValid(configuration: Config): Valid<NetworkParametersOverrides> {
        val packageOwnership = configuration[packageOwnership]
        if (packageOwnership != null && !noPackageOverlap(packageOwnership.map { it.javaPackageName })) {
            return Validated.invalid(sequenceOf(Configuration.Validation.Error.BadValue.of(
                    "Package namespaces must not overlap",
                    keyName = "packageOwnership",
                    containingPath = listOf()
            )).toSet())
        }
        return valid(NetworkParametersOverrides(
                minimumPlatformVersion = configuration[minimumPlatformVersion],
                maxMessageSize = configuration[maxMessageSize],
                maxTransactionSize = configuration[maxTransactionSize],
                packageOwnership = packageOwnership,
                eventHorizon = configuration[eventHorizon]
        ))
    }

    private fun parsePositiveInteger(rawValue: Int): Valid<Int> {
        if (rawValue > 0) return valid(rawValue)
        return badValue("The value must be at least 1")
    }
}