/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package com.r3cev.sgx.config

import com.r3cev.sgx.utils.getValue
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import joptsimple.OptionParser
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.system.exitProcess

enum class Mode {
    GenerateSgxKey,
    Sign
}

data class ToolConfig(val config: Config) {
    constructor(args: Array<String>) : this({
        val parser = OptionParser()
        val modeArg = parser.accepts("mode").withRequiredArg().defaultsTo(Mode.Sign.name)
        val sourcePathArg = parser.accepts("source").withRequiredArg()
        val configPathArg = parser.accepts("config").withRequiredArg()
        val profileArg = parser.accepts("profile").withRequiredArg().defaultsTo("dev")
        val publicKeyOutputPathArg = parser.accepts("pubkey").withRequiredArg()
        val signatureOutputPathArg = parser.accepts("signature").withRequiredArg()
        val deviceArg = parser.accepts("device").withRequiredArg()
        val keyNameArg = parser.accepts("keyName").withRequiredArg()
        val keyGroupArg = parser.accepts("keyGroup").withRequiredArg()
        val keySpecifierArg = parser.accepts("keySpecifier").withRequiredArg()
        val overwriteArg = parser.accepts("overwriteKey").withOptionalArg()
        val options = try {
            parser.parse(*args)
        } catch (e: Exception) {
            println(e.message)
            parser.printHelpOn(System.out)
            printModeHelp()
            exitProcess(1)
        }

        val sourcePath = options.valueOf(sourcePathArg)
        val mode = options.valueOf(modeArg)
        val publicKeyOutputPath = options.valueOf(publicKeyOutputPathArg)
        val signatureOutputPath = options.valueOf(signatureOutputPathArg)
        val baseConfig = if (options.hasArgument(configPathArg)) {
            val configPath = Paths.get(options.valueOf(configPathArg)!!)
            require(Files.exists(configPath)) { "Config file $configPath not found" }
            ConfigFactory.parseFile(configPath.toFile())
        } else {
            ConfigFactory.parseResources(ToolConfig::class.java, "sgxtool.cfg")
        }

        val profile = options.valueOf(profileArg)!!.toLowerCase()
        val overrideMap = mutableMapOf(
                "mode" to mode,
                "profile" to profile
        )
        if (sourcePath != null) overrideMap["sourcePath"] = sourcePath
        if (publicKeyOutputPath != null) overrideMap["publicKeyOutputPath"] = publicKeyOutputPath
        if (signatureOutputPath != null) overrideMap["signatureOutputPath"] = signatureOutputPath
        overrideMap["overwriteKey"] = options.has(overwriteArg).toString()
        if (options.hasArgument(deviceArg)) {
            overrideMap["$profile.device"] = options.valueOf(deviceArg)
        }
        if (options.hasArgument(keyNameArg)) {
            overrideMap["$profile.keyName"] = options.valueOf(keyNameArg)
        }
        if (options.hasArgument(keyGroupArg)) {
            overrideMap["$profile.keyGroup"] = options.valueOf(keyGroupArg)
        }
        if (options.hasArgument(keySpecifierArg)) {
            overrideMap["$profile.keySpecifier"] = options.valueOf(keySpecifierArg)
        }
        val overrideConf = ConfigFactory.parseMap(overrideMap)
        val final = overrideConf.withFallback(baseConfig).resolve()
        final!!
    }())

    val mode: Mode by config
    val profile: String by config
    val profileConfig: Config get() = config.getConfig(profile)
    val device: String by profileConfig
    val keyName: String by profileConfig
    val keyGroup: String by profileConfig
    val keySpecifier: String by profileConfig
    val sourcePath: Path? by config
    val publicKeyOutputPath: Path? by config
    val signatureOutputPath: Path? by config
    val overwriteKey: Boolean by config

    init {
        when (mode) {
            Mode.Sign -> {
                requireNotNull(sourcePath)
                requireNotNull(signatureOutputPath)
                requireNotNull(publicKeyOutputPath)
            }
            Mode.GenerateSgxKey -> {
                require(sourcePath == null)
                require(signatureOutputPath == null)
                require(publicKeyOutputPath == null)
            }
        }
    }

    override fun toString(): String {
        val sb = StringBuilder()
        sb.append("mode: $mode\n")
        sb.append("profile: $profile\n")
        sb.append("device: $device\n")
        sb.append("keyName: $keyName\n")
        sb.append("keyGroup: $keyGroup\n")
        sb.append("keySpecifier: $keySpecifier\n")
        sb.append("sourcePath: $sourcePath\n")
        sb.append("publicKeyOutputPath: $publicKeyOutputPath\n")
        sb.append("signatureOutputPath: $signatureOutputPath\n")
        return sb.toString()
    }

}

fun printModeHelp() {
    val message = listOf(
            "This tool may be run in two modes, --mode=GenerateSgxKey and --mode=Sign.",
            "Both may take --profile as an argument to indicate what HSM profile to use (see sgxtool.cfg)",
            "--mode=Sign expects --sourcePath={path to blob to sign}, --signatureOutputPath={path to result signature} and --publicKeyOutputPath={path to output public key}.",
            "Providing any of these arguments in --mode=GenerateSgxKey results in an error."
    )
    println(message.joinToString("\n"))
}
