package com.r3cev.sgx.config

import com.r3cev.sgx.utils.getValue
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import joptsimple.OptionParser
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.system.exitProcess

data class ToolConfig(val config: Config) {
    constructor(args: Array<String>) : this({
        val parser = OptionParser()
        val sourcePathArg = parser.accepts("source").withRequiredArg().required()
        val configPathArg = parser.accepts("config").withRequiredArg()
        val profileArg = parser.accepts("profile").withRequiredArg().defaultsTo("dev")
        val publicKeyOutputPathArg = parser.accepts("pubkey").withRequiredArg().defaultsTo("./pubkey.pem")
        val signatureOutputPathArg = parser.accepts("signature").withRequiredArg().defaultsTo("./signature.sha256")
        val deviceArg = parser.accepts("device").withRequiredArg()
        val keyNameArg = parser.accepts("keyName").withRequiredArg()
        val keyGroupArg = parser.accepts("keyGroup").withRequiredArg()
        val keySpecifierArg = parser.accepts("keySpecifier").withRequiredArg()
        val options = try {
            parser.parse(*args)
        } catch (e: Exception) {
            println(e.message)
            parser.printHelpOn(System.out)
            exitProcess(1)
        }

        val sourcePath = options.valueOf(sourcePathArg)!!
        val publicKeyOutputPath = options.valueOf(publicKeyOutputPathArg)!!
        val signatureOutputPath = options.valueOf(signatureOutputPathArg)!!
        val baseConfig = if (options.hasArgument(configPathArg)) {
            val configPath = Paths.get(options.valueOf(configPathArg)!!)
            require(Files.exists(configPath)) { "Config file $sourcePath not found" }
            ConfigFactory.parseFile(configPath.toFile())
        } else {
            ConfigFactory.parseResources(ToolConfig::class.java, "sgxtool.cfg")
        }

        val profile = options.valueOf(profileArg)!!.toLowerCase()
        val overrideMap = mutableMapOf("profile" to profile, "sourcePath" to sourcePath,
                "publicKeyOutputPath" to publicKeyOutputPath,
                "signatureOutputPath" to signatureOutputPath)
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

    val profile: String by config
    val profileConfig: Config get() = config.getConfig(profile)
    val device: String by profileConfig
    val keyName: String by profileConfig
    val keyGroup: String by profileConfig
    val keySpecifier: String by profileConfig
    val sourcePath: Path by config
    val publicKeyOutputPath: Path by config
    val signatureOutputPath: Path by config

    override fun toString(): String {
        val sb = StringBuilder()
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
