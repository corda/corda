package net.corda.coretesting.internal.stubs

import net.corda.core.internal.div
import net.corda.nodeapi.internal.DEV_CA_KEY_STORE_PASS
import net.corda.nodeapi.internal.DEV_CA_TRUST_STORE_PASS
import net.corda.nodeapi.internal.DEV_CA_TRUST_STORE_PRIVATE_KEY_PASS
import net.corda.nodeapi.internal.config.FileBasedCertificateStoreSupplier
import net.corda.nodeapi.internal.config.MutualSslConfiguration
import net.corda.nodeapi.internal.config.SslConfiguration
import java.nio.file.Path

class CertificateStoreStubs {

    companion object {

        const val DEFAULT_CERTIFICATES_DIRECTORY_NAME = "certificates"
    }

    class Signing {

        companion object {

            private const val DEFAULT_STORE_FILE_NAME = "nodekeystore.jks"
            private const val DEFAULT_STORE_PASSWORD = DEV_CA_KEY_STORE_PASS

            @JvmStatic
            fun withCertificatesDirectory(certificatesDirectory: Path, password: String = DEFAULT_STORE_PASSWORD,
                                          keyPassword: String = password, certificateStoreFileName: String = DEFAULT_STORE_FILE_NAME): FileBasedCertificateStoreSupplier {

                return FileBasedCertificateStoreSupplier(certificatesDirectory / certificateStoreFileName, password, keyPassword)
            }

            @JvmStatic
            fun withBaseDirectory(baseDirectory: Path, password: String = DEFAULT_STORE_PASSWORD, keyPassword: String = password,
                                  certificatesDirectoryName: String = DEFAULT_CERTIFICATES_DIRECTORY_NAME, certificateStoreFileName: String = DEFAULT_STORE_FILE_NAME): FileBasedCertificateStoreSupplier {

                return FileBasedCertificateStoreSupplier(baseDirectory / certificatesDirectoryName / certificateStoreFileName, password, keyPassword)
            }
        }
    }

    class P2P {

        companion object {

            @Suppress("LongParameterList")
            @JvmStatic
            fun withCertificatesDirectory(certificatesDirectory: Path, keyStoreFileName: String = KeyStore.DEFAULT_STORE_FILE_NAME,
                                          keyStorePassword: String = KeyStore.DEFAULT_STORE_PASSWORD, keyPassword: String = keyStorePassword,
                                          trustStoreFileName: String = TrustStore.DEFAULT_STORE_FILE_NAME,
                                          trustStorePassword: String = TrustStore.DEFAULT_STORE_PASSWORD,
                                          trustStoreKeyPassword: String = TrustStore.DEFAULT_KEY_PASSWORD): MutualSslConfiguration {

                val keyStore = FileBasedCertificateStoreSupplier(certificatesDirectory / keyStoreFileName, keyStorePassword, keyPassword)
                val trustStore = FileBasedCertificateStoreSupplier(certificatesDirectory / trustStoreFileName, trustStorePassword, trustStoreKeyPassword)
                return SslConfiguration.mutual(keyStore, trustStore)
            }

            @JvmStatic
            fun withBaseDirectory(baseDirectory: Path, certificatesDirectoryName: String = DEFAULT_CERTIFICATES_DIRECTORY_NAME,
                                  keyStoreFileName: String = KeyStore.DEFAULT_STORE_FILE_NAME, keyStorePassword: String = KeyStore.DEFAULT_STORE_PASSWORD,
                                  keyPassword: String = keyStorePassword, trustStoreFileName: String = TrustStore.DEFAULT_STORE_FILE_NAME,
                                  trustStorePassword: String = TrustStore.DEFAULT_STORE_PASSWORD): MutualSslConfiguration {

                return withCertificatesDirectory(baseDirectory / certificatesDirectoryName, keyStoreFileName, keyStorePassword, keyPassword, trustStoreFileName, trustStorePassword)
            }
        }

        class KeyStore {

            companion object {

                const val DEFAULT_STORE_FILE_NAME = "sslkeystore.jks"
                const val DEFAULT_STORE_PASSWORD = DEV_CA_KEY_STORE_PASS

                @JvmStatic
                fun withCertificatesDirectory(certificatesDirectory: Path, password: String = DEFAULT_STORE_PASSWORD, keyPassword: String = password,
                                              certificateStoreFileName: String = DEFAULT_STORE_FILE_NAME): FileBasedCertificateStoreSupplier {

                    return FileBasedCertificateStoreSupplier(certificatesDirectory / certificateStoreFileName, password, keyPassword)
                }

                @JvmStatic
                fun withBaseDirectory(baseDirectory: Path, password: String = DEFAULT_STORE_PASSWORD, keyPassword: String = password,
                                      certificatesDirectoryName: String = DEFAULT_CERTIFICATES_DIRECTORY_NAME, certificateStoreFileName: String = DEFAULT_STORE_FILE_NAME): FileBasedCertificateStoreSupplier {

                    return FileBasedCertificateStoreSupplier(baseDirectory / certificatesDirectoryName / certificateStoreFileName, password, keyPassword)
                }
            }
        }

        class TrustStore {

            companion object {

                const val DEFAULT_STORE_FILE_NAME = "truststore.jks"
                const val DEFAULT_STORE_PASSWORD = DEV_CA_TRUST_STORE_PASS
                const val DEFAULT_KEY_PASSWORD = DEV_CA_TRUST_STORE_PRIVATE_KEY_PASS

                @JvmStatic
                fun withCertificatesDirectory(certificatesDirectory: Path, password: String = DEFAULT_STORE_PASSWORD,
                                              keyPassword: String = DEFAULT_KEY_PASSWORD, certificateStoreFileName: String = DEFAULT_STORE_FILE_NAME): FileBasedCertificateStoreSupplier {

                    return FileBasedCertificateStoreSupplier(certificatesDirectory / certificateStoreFileName, password, keyPassword)
                }

                @JvmStatic
                fun withBaseDirectory(baseDirectory: Path, password: String = DEFAULT_STORE_PASSWORD, keyPassword: String = DEFAULT_KEY_PASSWORD,
                                      certificatesDirectoryName: String = DEFAULT_CERTIFICATES_DIRECTORY_NAME, certificateStoreFileName: String = DEFAULT_STORE_FILE_NAME): FileBasedCertificateStoreSupplier {

                    return FileBasedCertificateStoreSupplier(baseDirectory / certificatesDirectoryName / certificateStoreFileName, password, keyPassword)
                }
            }
        }
    }
}