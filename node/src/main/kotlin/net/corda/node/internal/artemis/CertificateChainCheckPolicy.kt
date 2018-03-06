/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.node.internal.artemis

import net.corda.core.identity.CordaX500Name
import net.corda.nodeapi.internal.crypto.X509Utilities
import java.security.KeyStore
import javax.security.cert.CertificateException
import javax.security.cert.X509Certificate

sealed class CertificateChainCheckPolicy {
    @FunctionalInterface
    interface Check {
        fun checkCertificateChain(theirChain: Array<X509Certificate>)
    }

    abstract fun createCheck(keyStore: KeyStore, trustStore: KeyStore): Check

    object Any : CertificateChainCheckPolicy() {
        override fun createCheck(keyStore: KeyStore, trustStore: KeyStore): Check {
            return object : Check {
                override fun checkCertificateChain(theirChain: Array<X509Certificate>) {
                    // nothing to do here
                }
            }
        }
    }

    object RootMustMatch : CertificateChainCheckPolicy() {
        override fun createCheck(keyStore: KeyStore, trustStore: KeyStore): Check {
            val rootPublicKey = trustStore.getCertificate(X509Utilities.CORDA_ROOT_CA).publicKey
            return object : Check {
                override fun checkCertificateChain(theirChain: Array<X509Certificate>) {
                    val theirRoot = theirChain.last().publicKey
                    if (rootPublicKey != theirRoot) {
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
                override fun checkCertificateChain(theirChain: Array<X509Certificate>) {
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
                override fun checkCertificateChain(theirChain: Array<X509Certificate>) {
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
        override fun checkCertificateChain(theirChain: Array<X509Certificate>) {
            if (!theirChain.any { certificate -> CordaX500Name.parse(certificate.subjectDN.name).commonName == username }) {
                throw CertificateException("Client certificate does not match login username.")
            }
        }
    }
}