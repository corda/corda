package com.r3.conclave.cordapp.common

import java.security.PublicKey

/**
 * Defines the interface for querying the sender's identity properties. The SenderIdentity is accessible to the
 * the Enclave and it can be used to uniquely identify the sender if the sender decided to share
 * its verifiable identity. Any identity shared by the sender goes through a verification process to ensure the identity
 * is part of the same certificate chain as the root certificate hardcoded into the enclave.
 */
interface SenderIdentity {

    /**
     * The verified X.500 subject name of the sender
     */
    val name: String

    /**
     * The verified public key of the sender's identity. Specifically, this is the public key from the sender's X.509 certificate.
     * Note, this public key is different to the encryption key used in Mail ([EnclaveMail.authenticatedSender])
     */
    val publicKey: PublicKey
}