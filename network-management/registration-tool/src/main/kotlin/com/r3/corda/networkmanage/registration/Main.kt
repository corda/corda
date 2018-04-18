package com.r3.corda.networkmanage.registration

import com.r3.corda.networkmanage.registration.ToolOption.KeyCopierOption
import com.r3.corda.networkmanage.registration.ToolOption.RegistrationOption
import joptsimple.OptionParser
import joptsimple.OptionSpecBuilder
import joptsimple.util.PathConverter
import joptsimple.util.PathProperties
import net.corda.core.crypto.Crypto
import java.nio.file.Path
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    Crypto.registerProviders() // Required to register Providers first thing on boot.
    val options = try {
        parseOptions(*args)
    } catch (e: ShowHelpException) {
        e.errorMessage?.let(::println)
        e.parser.printHelpOn(System.out)
        exitProcess(0)
    }
    when (options) {
        is RegistrationOption -> options.runRegistration()
        is KeyCopierOption -> options.copyKeystore()
    }
}

fun parseOptions(vararg args: String): ToolOption {
    val optionParser = OptionParser()
    val helpOption = optionParser.acceptsAll(listOf("h", "help"), "show help").forHelp()

    val importKeyStoreArg = optionParser.accepts("importkeystore")

    val configFileArg = optionParser
            .accepts("config-file", "Path to the registration config file")
            .availableUnless(importKeyStoreArg)
            .requiredUnless(importKeyStoreArg)
            .withRequiredArg()
            .withValuesConvertedBy(PathConverter(PathProperties.FILE_EXISTING))

    // key copy tool args
    val destKeystorePathArg = optionParser.accepts("destkeystore", "Path to the destination keystore which the private key should be copied to")
            .requireOnlyIf(importKeyStoreArg)
            .withRequiredArg()
            .withValuesConvertedBy(PathConverter(PathProperties.FILE_EXISTING))

    val srcKeystorePathArg = optionParser.accepts("srckeystore", "Path to the source keystore containing the private key")
            .requireOnlyIf(importKeyStoreArg)
            .withRequiredArg()
            .withValuesConvertedBy(PathConverter(PathProperties.FILE_EXISTING))

    val destPasswordArg = optionParser.accepts("deststorepass", "Source keystore password. Read in from the console if not specified.")
            .availableIf(importKeyStoreArg)
            .withRequiredArg()

    val srcPasswordArg = optionParser.accepts("srcstorepass", "Destination keystore password. Read in from the console if not specified.")
            .availableIf(importKeyStoreArg)
            .withRequiredArg()

    val destAliasArg = optionParser.accepts("destalias", "The alias under which the private key will be stored in the destination key store. If not provided then [srcalias] is used.")
            .availableIf(importKeyStoreArg)
            .withRequiredArg()

    val srcAliasArg = optionParser.accepts("srcalias", "The alias under which the private key resides in the source key store")
            .requireOnlyIf(importKeyStoreArg)
            .withRequiredArg()

    val optionSet = optionParser.parse(*args)
    if (optionSet.has(helpOption)) {
        throw ShowHelpException(optionParser)
    }

    val isCopyKey = optionSet.has(importKeyStoreArg)
    return if (isCopyKey) {
        val srcKeystorePath = optionSet.valueOf(srcKeystorePathArg)
        val targetKeystorePath = optionSet.valueOf(destKeystorePathArg)
        val srcPassword = optionSet.valueOf(srcPasswordArg)
        val destPassword = optionSet.valueOf(destPasswordArg)
        val srcAlias = optionSet.valueOf(srcAliasArg)
        val destAlias = optionSet.valueOf(destAliasArg)
        KeyCopierOption(srcKeystorePath, targetKeystorePath, srcPassword, destPassword, srcAlias, destAlias)
    } else {
        val configFile = optionSet.valueOf(configFileArg)
        RegistrationOption(configFile)
    }
}

private fun OptionSpecBuilder.requireOnlyIf(option: OptionSpecBuilder): OptionSpecBuilder = requiredIf(option).availableIf(option)

sealed class ToolOption {
    data class RegistrationOption(val configFile: Path) : ToolOption()
    data class KeyCopierOption(val sourceFile: Path,
                               val destinationFile: Path,
                               val sourcePassword: String?,
                               val destinationPassword: String?,
                               val sourceAlias: String,
                               val destinationAlias: String?) : ToolOption()
}

class ShowHelpException(val parser: OptionParser, val errorMessage: String? = null) : Exception()

fun readPassword(fmt: String): String {
    return if (System.console() != null) {
        String(System.console().readPassword(fmt))
    } else {
        print(fmt)
        readLine() ?: ""
    }
}
