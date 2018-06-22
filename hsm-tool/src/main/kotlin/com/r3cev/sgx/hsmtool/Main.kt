/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package com.r3cev.sgx.hsmtool

import CryptoServerAPI.CryptoServerException
import CryptoServerCXI.CryptoServerCXI
import CryptoServerJCE.CryptoServerProvider
import com.r3cev.sgx.config.Mode
import com.r3cev.sgx.config.ToolConfig
import com.r3cev.sgx.utils.HsmErrors
import org.bouncycastle.openssl.jcajce.JcaPEMWriter
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.FileWriter
import java.math.BigInteger
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.security.*
import java.security.spec.X509EncodedKeySpec

fun sign(config: ToolConfig) {
    require(Files.exists(config.sourcePath)) { "Input ${config.sourcePath} not found" }
    connectAndAuthenticate(config) { provider ->
        val keyStore = KeyStore.getInstance("CryptoServer", provider)
        keyStore.load(null, null)
        val aliases = keyStore.aliases().toList()
        println(aliases)
        val sgxKey = keyStore.getKey(config.keyName, null) as PrivateKey
        val data = Files.readAllBytes(config.sourcePath)
        val signer = Signature.getInstance("SHA256WithRSA", provider)
        signer.initSign(sgxKey)
        signer.update(data)
        val signature = signer.sign()

        val javaFactory = KeyFactory.getInstance("RSA")
        val sgxPub = keyStore.getCertificate(config.keyName).publicKey
        val sgxPubReImport = javaFactory.generatePublic(X509EncodedKeySpec(sgxPub.encoded))
        val verify = Signature.getInstance("SHA256WithRSA")
        verify.initVerify(sgxPubReImport)
        verify.update(data)

        require(verify.verify(signature)) { "Signature didn't independently verify" }
        System.setProperty("line.separator", "\n") // Ensure UNIX line endings in PEM files
        println("Writing signature")
        saveSignatureAsFile(signature, config.signatureOutputPath!!)
        println("Writing public key")
        savePublicKeyAsPEMFile(sgxPubReImport, config.publicKeyOutputPath!!)
    }
}

fun generateSgxKey(config: ToolConfig) {
    val generateFlag = if (config.overwriteKey) {
        println("!!! WARNING: OVERWRITING KEY NAMED ${config.keyName} !!!")
        CryptoServerCXI.FLAG_OVERWRITE
    } else {
        0
    }
    connectAndAuthenticate(config) { provider ->
        val keyAttributes = CryptoServerCXI.KeyAttributes()
        keyAttributes.apply {
            algo = CryptoServerCXI.KEY_ALGO_RSA
            size = 3072
            setExponent(BigInteger.valueOf(0x03))
            group = config.keyGroup
            specifier = config.keySpecifier.toInt()
            export = 0 // deny export
            name = config.keyName

        }
        println("Generating key...")
        // This should be CryptoServerCXI.MECH_KEYGEN_FIPS186_4_PRIME, however FIPS doesn't support exponent 3
        val mechanismFlag = CryptoServerCXI.MECH_RND_REAL or CryptoServerCXI.MECH_KEYGEN_ANSI_PRIME
        provider.cryptoServer.generateKey(generateFlag, keyAttributes, mechanismFlag)
    }
}

fun connectAndAuthenticate(config: ToolConfig, block: (CryptoServerProvider) -> Unit) {
    println("Connect to ${config.device}")
    val provider = createProvider(config.device, config.keyGroup, config.keySpecifier)
    try {
        while (true) {
            print("Enter User Name: ")
            val user = readLine()
            println("Login user: $user")
            provider.loginSign(user, ":cs2:cyb:USB0", null)
            val auth = provider.cryptoServer.authState
            if ((auth and 0x0000000F) >= 0x00000002) {
                println("Auth Sufficient")
                break
            }
            println("Need more permissions. Add extra login")
        }
        block(provider)
    } finally {
        try {
            provider.logoff()
        } catch (throwable: Throwable) {
            println("WARNING Exception while logging off")
            throwable.printStackTrace(System.out)
        }
    }
}

fun main(args: Array<String>) {
    println("SGX Tool started")
    val config = ToolConfig(args)
    println(config)
    try {
        when (config.mode) {
            Mode.Sign -> sign(config)
            Mode.GenerateSgxKey -> generateSgxKey(config)
        }
        println("Done!")
    } catch (exception: Throwable) {
        // Try to decode the error code
        val crypto = exception as? CryptoServerException ?: exception.cause as? CryptoServerException
        if (crypto != null) {
            throw Exception(HsmErrors.errors[crypto.ErrorCode], exception)
        } else {
            throw exception
        }
    }
}


private fun saveSignatureAsFile(signature: ByteArray, filename: Path) {
    Files.write(filename, signature, StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)
}

private fun savePublicKeyAsPEMFile(key: PublicKey, filename: Path) {
    FileWriter(filename.toFile()).use {
        JcaPEMWriter(it).use {
            it.writeObject(key)
        }
    }
}

private fun createProvider(device: String, keyGroup: String, keySpecifier: String): CryptoServerProvider {
    val cfgBuffer = ByteArrayOutputStream()
    val writer = cfgBuffer.writer(Charsets.UTF_8)
    writer.write("Device = $device\n")
    writer.write("ConnectionTimeout = 30000\n")
    writer.write("Timeout = 60000\n")
    writer.write("EndSessionOnShutdown = 1\n")
    writer.write("KeepSessionAlive = 0\n")
    writer.write("KeyGroup = $keyGroup\n")
    writer.write("KeySpecifier = $keySpecifier\n")
    writer.write("StoreKeysExternal = false\n")
    writer.close()
    val cfg = ByteArrayInputStream(cfgBuffer.toByteArray())
    cfgBuffer.close()
    val provider = CryptoServerProvider(cfg)
    cfg.close()
    return provider
}

