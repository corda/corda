package net.corda.node.internal.artemis

import net.corda.core.identity.CordaX500Name
import net.corda.core.utilities.contextLogger
import net.corda.nodeapi.internal.crypto.X509Utilities
import java.security.KeyStore
import java.security.cert.CertificateException

sealed class CertificateChainCheckPolicy {
    companion object {
        val log = contextLogger()
    }

    @FunctionalInterface
    interface Check {
        fun checkCertificateChain(theirChain: Array<java.security.cert.X509Certificate>)
    }

    abstract fun createCheck(keyStore: KeyStore, trustStore: KeyStore): Check

    object Any : CertificateChainCheckPolicy() {
        override fun createCheck(keyStore: KeyStore, trustStore: KeyStore): Check {
            return object : Check {
                override fun checkCertificateChain(theirChain: Array<java.security.cert.X509Certificate>) {
                    // nothing to do here
                }
            }
        }
    }

    object RootMustMatch : CertificateChainCheckPolicy() {
        override fun createCheck(keyStore: KeyStore, trustStore: KeyStore): Check {
            val rootAliases = trustStore.aliases().asSequence().filter { it.startsWith(X509Utilities.CORDA_ROOT_CA) }
            val rootPublicKeys = rootAliases.map { trustStore.getCertificate(it).publicKey }.toSet()
            return object : Check {
                override fun checkCertificateChain(theirChain: Array<java.security.cert.X509Certificate>) {
                    val theirRoot = theirChain.last().publicKey
                    if (theirRoot !in rootPublicKeys) {
                        throw CertificateException("Root certificate mismatch, their root = $theirRoot")
                    }
                }
            }
        }
    }

    object LeafMustMatch : CertificateChainCheckPolicy() {
        override fun createCheck(keyStore: KeyStore, trustStore: KeyStore): Check {
            val ourPublicKey = keyStore.getCertificate(X509Utilities.CORDA_CLIENT_TLS).publicKey
            return object : Check {
                override fun checkCertificateChain(theirChain: Array<java.security.cert.X509Certificate>) {
                    val theirLeaf = theirChain.first().publicKey
                    if (ourPublicKey != theirLeaf) {
                        throw CertificateException("Leaf certificate mismatch, their leaf = $theirLeaf")
                    }
                }
            }
        }
    }

    data class MustContainOneOf(private val trustedAliases: Set<String>) : CertificateChainCheckPolicy() {
        override fun createCheck(keyStore: KeyStore, trustStore: KeyStore): Check {
            val trustedPublicKeys = trustedAliases.map { trustStore.getCertificate(it).publicKey }.toSet()
            return object : Check {
                override fun checkCertificateChain(theirChain: Array<java.security.cert.X509Certificate>) {
                    if (!theirChain.any { it.publicKey in trustedPublicKeys }) {
                        throw CertificateException("Their certificate chain contained none of the trusted ones")
                    }
                }
            }
        }
    }

    object UsernameMustMatchCommonName : CertificateChainCheckPolicy() {
        override fun createCheck(keyStore: KeyStore, trustStore: KeyStore): Check {
            return UsernameMustMatchCommonNameCheck()
        }
    }

    class UsernameMustMatchCommonNameCheck : Check {
        lateinit var username: String
        override fun checkCertificateChain(theirChain: Array<java.security.cert.X509Certificate>) {
            if (!theirChain.any { certificate -> CordaX500Name.parse(certificate.subjectDN.name).commonName == username }) {
                throw CertificateException("Client certificate does not match login username.")
            }
        }
    }
}