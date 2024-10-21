package net.corda.testing.core.internal

import net.corda.core.identity.CordaX500Name
import net.corda.core.internal.JarSignatureCollector
import net.corda.core.internal.deleteRecursively
import net.corda.coretesting.internal.modifyJarManifest
import net.corda.coretesting.internal.useZipFile
import net.corda.nodeapi.internal.crypto.loadKeyStore
import java.io.Closeable
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.security.PublicKey
import java.util.jar.Attributes
import java.util.jar.JarInputStream
import java.util.jar.JarOutputStream
import java.util.jar.Manifest
import kotlin.io.path.deleteExisting
import kotlin.io.path.div
import kotlin.io.path.exists
import kotlin.io.path.listDirectoryEntries
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
    private val bin = Paths.get(System.getProperty("java.home")).let { if (it.endsWith("jre")) it.parent else it } / "bin"

    private fun Path.executeProcess(vararg command: String) {
        val shredder = (this / "_shredder").toFile() // No need to delete after each test.
        assertEquals(0, ProcessBuilder()
                .redirectOutput(shredder)
                .redirectError(shredder)
                .directory(this.toFile())
                .command((bin / command[0]).toString(), *command.sliceArray(1 until command.size))
                .start()
                .waitFor())
    }

    private val CODE_SIGNER = CordaX500Name("Test Code Signing Service", "London", "GB")

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

    fun Path.unsignJar() {
        // Remove the signatures
        useZipFile { zipFs ->
            zipFs.getPath("META-INF").takeIf { it.exists() }?.listDirectoryEntries("*.{SF,DSA,RSA,EC}")?.forEach(Path::deleteExisting)
        }
        // Remove all the hash information of the jar contents
        modifyJarManifest { manifest ->
            manifest.entries.clear()
        }
    }

    fun Path.getPublicKey(alias: String, storeName: String, storePassword: String) : PublicKey {
        val ks = loadKeyStore(this.resolve(storeName), storePassword)
        return ks.getCertificate(alias).publicKey
    }

    fun Path.containsKey(alias: String, storePassword: String, storeName: String = "_teststore"): Boolean {
        return try {
            val ks = loadKeyStore(this.resolve(storeName), storePassword)
            ks.containsAlias(alias)
        } catch (e: NoSuchFileException) {
            false
        }
    }

    fun Path.getPublicKey(alias: String, storePassword: String) = getPublicKey(alias, "_teststore", storePassword)

    fun Path.getJarSigners(fileName: String) =
            JarInputStream(FileInputStream((this / fileName).toFile())).use(JarSignatureCollector::collectSigners)

    fun Path.addManifest(fileName: String, vararg entries: Pair<Attributes.Name, String>) {
        val outputFile = this / (fileName + "Output")
        JarInputStream(FileInputStream((this / fileName).toFile())).use { input ->
            val manifest = input.manifest ?: Manifest()
            entries.forEach { (attributeName, value) ->
                manifest.mainAttributes[attributeName] = value
            }
            val output = JarOutputStream(FileOutputStream(outputFile.toFile()), manifest)
            var entry = input.nextEntry
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
        Files.copy(outputFile, this / fileName, REPLACE_EXISTING)
    }
}
