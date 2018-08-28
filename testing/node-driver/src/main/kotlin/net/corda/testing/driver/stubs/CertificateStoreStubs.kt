package net.corda.testing.driver.stubs

import net.corda.core.internal.div
import net.corda.nodeapi.internal.config.CertificateStoreSupplier
import net.corda.nodeapi.internal.config.FileBasedCertificateStoreLoader
import java.nio.file.Path

class CertificateStoreStubs {

    companion object {

        @JvmStatic
        fun withStoreAt(certificateStorePath: Path, password: String): CertificateStoreSupplier = FileBasedCertificateStoreLoader(certificateStorePath, password)
    }

    // TODO sollecitom create P2P equivalent
    class Signing {

        companion object {

            @JvmStatic
            fun withCertificatesDirectory(certificatesDirectory: Path, password: String, certificateStoreFileName: String = "nodekeystore.jks"): CertificateStoreSupplier {

                return FileBasedCertificateStoreLoader(certificatesDirectory / certificateStoreFileName, password)
            }

            @JvmStatic
            fun withBaseDirectory(baseDirectory: Path, password: String, certificatesDirectoryName: String = "certificates", certificateStoreFileName: String = "nodekeystore.jks"): CertificateStoreSupplier {

                return FileBasedCertificateStoreLoader(baseDirectory / certificatesDirectoryName / certificateStoreFileName, password)
            }
        }
    }
}