package net.corda.signing.authentication

import CryptoServerJCE.CryptoServerProvider
import net.corda.signing.configuration.Parameters
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.Console
import java.nio.file.Path
import kotlin.reflect.full.memberProperties

class Authenticator(private val provider: CryptoServerProvider,
                    private val mode: AuthMode = AuthMode.PASSWORD,
                    private val autoUsername: String? = null,
                    private val authKeyFilePath: Path? = null,
                    private val authKeyFilePass: String? = null,
                    private val authStrengthThreshold: Int = 2,
                    val console: Console? = System.console()) {

    /**
     * Interactively (using console) authenticates a user against the HSM. Once authentication is successful the
     * [block] is executed.
     * @param block to be executed once the authentication process succeeds. The block should take 2 parameters:
     * 1) [CryptoServerProvider] instance
     * 2) List of strings that corresponds to user names authenticated against the HSM.
     */
    fun connectAndAuthenticate(block: (CryptoServerProvider, List<String>) -> Unit) {
        try {
            val authenticated = mutableListOf<String>()
            loop@ while (true) {
                val user = if (autoUsername.isNullOrEmpty()) {
                    print("Enter User Name (or Q to quit): ")
                    val input = readConsoleLine(console)
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
                    AuthMode.CARD_READER -> provider.loginSign(user, ":cs2:cyb:USB0", null)
                    AuthMode.KEY_FILE -> {
                        println("Authenticating using preconfigured key file")
                        val password = if (authKeyFilePass == null) {
                            val input = readPassword("Enter key file password (or Q to quit): ", console)
                            if ("q" == input.toLowerCase()) {
                                authenticated.clear()
                                break@loop
                            } else {
                                input
                            }
                        } else {
                            authKeyFilePass
                        }
                        provider.loginSign(user, authKeyFilePath.toString(), password)
                    }
                    AuthMode.PASSWORD -> {
                        val password = readPassword("Enter password (or Q to quit): ", console)
                        if ("q" == password.toLowerCase()) {
                            authenticated.clear()
                            break@loop
                        }
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
internal data class CryptoServerProviderConfig(
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
 */
fun Parameters.createProvider(): CryptoServerProvider {
    val config = CryptoServerProviderConfig(
            Device = device,
            KeyGroup = keyGroup,
            KeySpecifier = keySpecifier
    )
    val cfgBuffer = ByteArrayOutputStream()
    val writer = cfgBuffer.writer(Charsets.UTF_8)
    for (property in CryptoServerProviderConfig::class.memberProperties) {
        writer.write("${property.name} = ${property.get(config)}\n")
    }
    writer.close()
    val cfg = ByteArrayInputStream(cfgBuffer.toByteArray())
    cfgBuffer.close()
    val provider = CryptoServerProvider(cfg)
    cfg.close()
    return provider
}

/** Read password from console, do a readLine instead if console is null (e.g. when debugging in IDE). */
internal fun readPassword(fmt: String, console: Console? = System.console()): String {
    return if (console != null) {
        String(console.readPassword(fmt))
    } else {
        print(fmt)
        readLine()!!
    }
}

/** Read console line */
internal fun readConsoleLine(console: Console?): String? {
    return if (console == null) {
        readLine()
    } else {
        console.readLine()
    }
}