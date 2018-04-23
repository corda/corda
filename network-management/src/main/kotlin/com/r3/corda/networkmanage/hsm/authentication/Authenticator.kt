/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package com.r3.corda.networkmanage.hsm.authentication

import CryptoServerJCE.CryptoServerProvider
import com.r3.corda.networkmanage.common.signer.AuthenticationException
import java.io.ByteArrayOutputStream
import java.nio.file.Path
import kotlin.reflect.full.memberProperties

/**
 * Performs user authentication against the HSM
 */
class Authenticator(private val mode: AuthMode = AuthMode.PASSWORD,
                    private val autoUsername: String? = null,
                    private val authKeyFilePath: Path? = null,
                    private val authKeyFilePass: String? = null,
                    private val authStrengthThreshold: Int = 2,
                    inputReader: InputReader = ConsoleInputReader(),
                    private val provider: CryptoServerProvider) : InputReader by inputReader {

    /**
     * Interactively (using console) authenticates a user against the HSM. Once authentication is successful the
     * [block] is executed.
     * @param block to be executed once the authentication process succeeds. The block should take 3 parameters:
     * 1) [CryptoServerProvider] instance of the certificate provider
     * 2) List of strings that corresponds to user names authenticated against the HSM.
     */
    fun <T : Any> connectAndAuthenticate(block: (CryptoServerProvider, List<String>) -> T): T {
        return try {
            val authenticated = mutableListOf<String>()
            loop@ while (true) {
                val user = if (autoUsername.isNullOrEmpty()) {
                    print("Enter User Name (or Q to quit): ")
                    val input = readLine()
                    if (input != null && "q" == input.toLowerCase()) {
                        authenticated.clear()
                        break
                    }
                    input
                } else {
                    println("Authenticating using preconfigured user name: $autoUsername")
                    autoUsername
                }
                when (mode) {
                    AuthMode.CARD_READER -> {
                        println("Authenticating using card reader")
                        println("Accessing the certificate key group data...")
                        provider.loginSign(user, ":cs2:cyb:USB0", null)
                    }
                    AuthMode.KEY_FILE -> {
                        println("Authenticating using preconfigured key file $authKeyFilePath")
                        val password = if (authKeyFilePass == null) {
                            val input = readPassword("Enter key file password (or Q to quit): ")
                            if ("q" == input.toLowerCase().trim()) {
                                authenticated.clear()
                                break@loop
                            } else {
                                input
                            }
                        } else {
                            authKeyFilePass
                        }
                        println("Accessing the certificate key group data...")
                        provider.loginSign(user, authKeyFilePath.toString(), password)
                    }
                    AuthMode.PASSWORD -> {
                        println("Authenticating using password")
                        val password = readPassword("Enter password (or Q to quit): ")
                        if ("q" == password.toLowerCase()) {
                            authenticated.clear()
                            break@loop
                        }
                        println("Accessing the certificate key group data...")
                        provider.loginPassword(user, password)
                    }
                }
                authenticated.add(user!!)
                val auth = provider.cryptoServer.authState
                if ((auth and 0x0000000F) >= authStrengthThreshold) {
                    println("Authentication sufficient")
                    break
                } else {
                    println("Need more permissions. Add extra login")
                }
            }
            if (!authenticated.isEmpty()) {
                block(provider, authenticated)
            } else {
                throw AuthenticationException()
            }
        } finally {
            try {
                provider.logoff()
            } catch (throwable: Throwable) {
                println("WARNING Exception while logging off")
                throwable.printStackTrace(System.out)
            }
        }
    }
}

/*
 * Configuration class for [CryptoServerProvider]
 */
data class CryptoServerProviderConfig(
        val Device: String = "3001@127.0.0.1",
        val ConnectionTimeout: Int = 30000,
        val Timeout: Int = 60000,
        val EndSessionOnShutdown: Int = 1,
        val KeepSessionAlive: Int = 0,
        val KeyGroup: String = "*",
        val KeySpecifier: Int = -1,
        val StoreKeysExternal: Boolean = false
)

/**
 * Creates an instance of [CryptoServerProvider] that corresponds to the HSM.
 *
 * @param keyGroup HSM key group.
 * @param keySpecifier HSM key specifier.
 * @param device HSM device address.
 *
 * @return preconfigured instance of [CryptoServerProvider]
 */
fun createProvider(keyGroup: String, keySpecifier: Int, device: String): CryptoServerProvider {
    val config = CryptoServerProviderConfig(
            Device = device,
            KeyGroup = keyGroup,
            KeySpecifier = keySpecifier
    )
    return createProvider(config)
}

/**
 * Creates an instance of [CryptoServerProvider] configured accordingly to the passed configuration.
 *
 * @param config crypto server provider configuration.
 *
 * @return preconfigured instance of [CryptoServerProvider]
 */
fun createProvider(config: CryptoServerProviderConfig): CryptoServerProvider {
    val cfgBuffer = ByteArrayOutputStream()
    val writer = cfgBuffer.writer(Charsets.UTF_8)
    for (property in CryptoServerProviderConfig::class.memberProperties) {
        writer.write("${property.name} = ${property.get(config)}\n")
    }
    writer.close()
    val cfg = cfgBuffer.toByteArray().inputStream()
    return CryptoServerProvider(cfg)
}