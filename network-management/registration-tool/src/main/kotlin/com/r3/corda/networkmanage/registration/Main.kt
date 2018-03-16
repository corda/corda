package com.r3.corda.networkmanage.registration

import com.r3.corda.networkmanage.registration.ToolOption.KeyCopierOption
import com.r3.corda.networkmanage.registration.ToolOption.RegistrationOption
import joptsimple.ArgumentAcceptingOptionSpec
import joptsimple.OptionParser
import joptsimple.OptionSet
import joptsimple.OptionSpecBuilder
import joptsimple.util.PathConverter
import joptsimple.util.PathProperties
import java.nio.file.Path

fun main(args: Array<String>) {
    val options = parseOptions(*args)
    when (options) {
        is RegistrationOption -> options.runRegistration()
        is KeyCopierOption -> options.copyKeystore()
    }
}

private const val importKeyFlag = "importkeystore"

internal fun parseOptions(vararg args: String): ToolOption {
    val optionParser = OptionParser()
    val isCopyKeyArg = optionParser.accepts(importKeyFlag)

    val configFileArg = optionParser
            .accepts("config-file", "The path to the registration config file")
            .availableUnless(importKeyFlag)
            .withRequiredArg()
            .withValuesConvertedBy(PathConverter(PathProperties.FILE_EXISTING))

    // key copy tool args
    val destKeystorePathArg = optionParser.accepts("destkeystore")
            .requireOnlyIf(importKeyFlag)
            .withRequiredArg()
            .withValuesConvertedBy(PathConverter(PathProperties.FILE_EXISTING))
    val srcKeystorePathArg = optionParser.accepts("srckeystore")
            .requireOnlyIf(importKeyFlag)
            .withRequiredArg()
            .withValuesConvertedBy(PathConverter(PathProperties.FILE_EXISTING))

    val destPasswordArg = optionParser.accepts("deststorepass")
            .requireOnlyIf(importKeyFlag)
            .withRequiredArg()
    val srcPasswordArg = optionParser.accepts("srcstorepass")
            .requireOnlyIf(importKeyFlag)
            .withRequiredArg()

    val destAliasArg = optionParser.accepts("destalias")
            .availableIf(importKeyFlag)
            .withRequiredArg()
    val srcAliasArg = optionParser.accepts("srcalias")
            .requireOnlyIf(importKeyFlag)
            .withRequiredArg()

    val optionSet = optionParser.parse(*args)
    val isCopyKey = optionSet.has(isCopyKeyArg)

    return if (isCopyKey) {
        val targetKeystorePath = optionSet.valueOf(destKeystorePathArg)
        val srcKeystorePath = optionSet.valueOf(srcKeystorePathArg)
        val destPassword = optionSet.valueOf(destPasswordArg)
        val srcPassword = optionSet.valueOf(srcPasswordArg)
        val destAlias = optionSet.getOrNull(destAliasArg)
        val srcAlias = optionSet.valueOf(srcAliasArg)

        KeyCopierOption(srcKeystorePath, targetKeystorePath, srcPassword, destPassword, srcAlias, destAlias)
    } else {
        val configFilePath = optionSet.valueOf(configFileArg)
        RegistrationOption(configFilePath)
    }
}

private fun <V : Any> OptionSet.getOrNull(opt: ArgumentAcceptingOptionSpec<V>): V? = if (has(opt)) valueOf(opt) else null

private fun OptionSpecBuilder.requireOnlyIf(optionName: String): OptionSpecBuilder = requiredIf(optionName).availableIf(optionName)

sealed class ToolOption {
    data class RegistrationOption(val configFilePath: Path) : ToolOption()
    data class KeyCopierOption(val srcPath: Path,
                               val destPath: Path,
                               val srcPass: String,
                               val destPass: String,
                               val srcAlias: String,
                               val destAlias: String?) : ToolOption()
}

fun readPassword(fmt: String): String {
    return if (System.console() != null) {
        String(System.console().readPassword(fmt))
    } else {
        print(fmt)
        readLine() ?: ""
    }
}
