package net.corda.testing.core.internal

import net.corda.core.crypto.sha256
import net.corda.core.identity.CordaX500Name
import net.corda.core.internal.JarSignatureCollector
import net.corda.core.internal.deleteRecursively
import net.corda.core.internal.div
import net.corda.core.internal.hash
import net.corda.nodeapi.internal.crypto.loadKeyStore
import java.io.Closeable
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.security.PublicKey
import java.util.jar.Attributes
import java.util.jar.JarInputStream
import java.util.jar.JarOutputStream
import java.util.jar.Manifest
import kotlin.test.assertEquals

/**
 * Class to create an automatically delete a temporary directory.
 */
class SelfCleaningDir : Closeable {
    val path: Path = Files.createTempDirectory(JarSignatureTestUtils::class.simpleName)
    override fun close() {
        path.deleteRecursively()
    }
}

object JarSignatureTestUtils {
    val bin = Paths.get(System.getProperty("java.home")).let { if (it.endsWith("jre")) it.parent else it } / "bin"

    fun Path.executeProcess(vararg command: String) {
        val shredder = (this / "_shredder").toFile() // No need to delete after each test.
        assertEquals(0, ProcessBuilder()
                .inheritIO()
                .redirectOutput(shredder)
                .redirectError(shredder)
                .directory(this.toFile())
                .command((bin / command[0]).toString(), *command.sliceArray(1 until command.size))
                .start()
                .waitFor())
    }

    val CODE_SIGNER = CordaX500Name("Test Code Signing Service", "London", "GB")

    fun Path.generateKey(alias: String = "Test", storePassword: String = "secret!", name: String = CODE_SIGNER.toString(), keyalg: String = "RSA", keyPassword: String = storePassword, storeName: String = "_teststore") : PublicKey {
        executeProcess("keytool", "-genkeypair", "-keystore", storeName, "-storepass", storePassword, "-keyalg", keyalg, "-alias", alias, "-keypass", keyPassword, "-dname", name)
        val ks = loadKeyStore(this.resolve("_teststore"), storePassword)
        return ks.getCertificate(alias).publicKey
    }

    fun Path.createJar(fileName: String, vararg contents: String) =
            executeProcess(*(arrayOf("jar", "cvf", fileName) + contents))

    fun Path.addIndexList(fileName: String) {
        executeProcess(*(arrayOf("jar", "i", fileName)))
    }

    fun Path.updateJar(fileName: String, vararg contents: String) =
            executeProcess(*(arrayOf("jar", "uvf", fileName) + contents))

    fun Path.signJar(fileName: String, alias: String, storePassword: String, keyPassword: String = storePassword): PublicKey {
        executeProcess("jarsigner", "-keystore", "_teststore", "-storepass", storePassword, "-keypass", keyPassword, fileName, alias)
        val ks = loadKeyStore(this.resolve("_teststore"), storePassword)
        return ks.getCertificate(alias).publicKey
    }

    fun Path.getPublicKey(alias: String, storeName: String, storePassword: String) : PublicKey {
        val ks = loadKeyStore(this.resolve(storeName), storePassword)
        return ks.getCertificate(alias).publicKey
    }

    fun Path.getPublicKey(alias: String, storePassword: String) = getPublicKey(alias, "_teststore", storePassword)

    fun Path.getJarSigners(fileName: String) =
            JarInputStream(FileInputStream((this / fileName).toFile())).use(JarSignatureCollector::collectSigners)

    fun Path.addManifest(fileName: String, vararg entry: Pair<Attributes.Name, String>) {
        JarInputStream(FileInputStream((this / fileName).toFile())).use { input ->
            val manifest = input.manifest ?: Manifest()
            entry.forEach { (attributeName, value) ->
                manifest.mainAttributes[attributeName] = value
            }
            val output = JarOutputStream(FileOutputStream((this / fileName).toFile()), manifest)
            var entry= input.nextEntry
            val buffer = ByteArray(1 shl 14)
            while (true) {
                output.putNextEntry(entry)
                var nr: Int
                while (true) {
                    nr = input.read(buffer)
                    if (nr < 0) break
                    output.write(buffer, 0, nr)
                }
                entry = input.nextEntry ?: break
            }
            output.close()
        }
    }

    fun printCodeSigningDetails() {
        // See signing options: https://github.com/corda/corda-gradle-plugins/blob/master/cordapp/src/main/kotlin/net/corda/plugins/SigningOptions.kt
        // Using Java keytool to display the PK:
        // -keystore /Users/josecoll/IdeaProjects/corda-gradle-plugins/cordapp/src/main/resources/certificates/cordadevcodesign.jks -storepass cordacadevpass
        val keyStoreDir = Paths.get("/Users/josecoll/IdeaProjects/corda-gradle-plugins/cordapp/src/main/resources/certificates/")
        val codeSigningPk = keyStoreDir.getPublicKey("cordacodesign", "cordadevcodesign.jks", "cordacadevpass")
        println("Public Key: $codeSigningPk")
        val pkHash = codeSigningPk.hash
        println("SecureHash: $pkHash")
        val sha256Hash = pkHash.sha256()
        println("SHA256    : $sha256Hash")

        // Output:
//        Public Key: Sun EC public key, 256 bits
//        public x coord: 1606301601488985262456987828510069198490398827685577289418991162593641911319
//        public y coord: 39305038020852387120148508817752828470229346772241518574141632445003972282481
//        parameters: secp256r1 [NIST P-256, X9.62 prime256v1] (1.2.840.10045.3.1.7)
//        SecureHash: AA59D829F2CA8FDDF5ABEA40D815F937E3E54E572B65B93B5C216AE6594E7D6B
//        SHA256    : 6F6696296C3F58B55FB6CA865A025A3A6CC27AD17C4AFABA1E8EF062E0A82739
    }
}

fun main(args: Array<String>) {
    JarSignatureTestUtils.printCodeSigningDetails()
}