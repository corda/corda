package net.corda.coretesting.internal

import net.corda.core.identity.CordaX500Name
import net.corda.coretesting.internal.stubs.CertificateStoreStubs
import net.corda.nodeapi.internal.config.MutualSslConfiguration
import net.corda.nodeapi.internal.loadDevCaTrustStore
import net.corda.nodeapi.internal.registerDevP2pCertificates
import java.nio.file.FileSystem
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.util.jar.Attributes
import java.util.jar.JarOutputStream
import java.util.jar.Manifest
import kotlin.io.path.fileSize
import kotlin.io.path.inputStream
import kotlin.io.path.outputStream

fun configureTestSSL(legalName: CordaX500Name): MutualSslConfiguration {
    val certificatesDirectory = Files.createTempDirectory("certs")
    val config = CertificateStoreStubs.P2P.withCertificatesDirectory(certificatesDirectory)
    if (config.trustStore.getOptional() == null) {
        loadDevCaTrustStore().copyTo(config.trustStore.get(true))
    }
    if (config.keyStore.getOptional() == null) {
        config.keyStore.get(true).registerDevP2pCertificates(legalName)
    }
    return config
}

inline fun <T> Path.useZipFile(block: (FileSystem) -> T): T {
    if (fileSize() == 0L) {
        // Need to first create an empty jar before it can be opened
        JarOutputStream(outputStream()).close()
    }
    return FileSystems.newFileSystem(this).use(block)
}

inline fun <T> Path.modifyJarManifest(block: (Manifest) -> T): T {
    return useZipFile { zipFs ->
        val manifestFile = zipFs.getPath("META-INF", "MANIFEST.MF")
        val manifest = manifestFile.inputStream().use(::Manifest)
        val result = block(manifest)
        manifestFile.outputStream().use(manifest::write)
        result
    }
}

fun Attributes.delete(name: String): String? = remove(Attributes.Name(name)) as String?
