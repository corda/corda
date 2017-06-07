package com.r3cev.sgx.signtool

import CryptoServerJCE.CryptoServerProvider
import com.r3cev.sgx.config.ToolConfig
import org.bouncycastle.openssl.jcajce.JcaPEMWriter
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.FileWriter
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.security.*
import java.security.spec.X509EncodedKeySpec

fun main(args: Array<String>) {
    println("SGX Tool started")
    val config = ToolConfig(args)
    require(Files.exists(config.sourcePath)) { "Input ${config.sourcePath} not found" }
    println(config)
    println("Connect to ${config.device}")
    val provider = createProvider(config.device, config.keyGroup, config.keySpecifier)
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
    saveSignatureAsFile(signature, config.signatureOutputPath)
    savePublicKeyAsPEMFile(sgxPubReImport, config.publicKeyOutputPath)

    provider.logoff()
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
    writer.write("ConnectionTimeout = 3000\n")
    writer.write("Timeout = 30000\n")
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

