package net.corda.testing.internal

import net.corda.nodeapi.internal.crypto.CertificateAndKeyPair
import java.time.Instant

val DEV_INTERMEDIATE_CA: CertificateAndKeyPair by lazy { net.corda.nodeapi.internal.DEV_INTERMEDIATE_CA }

val DEV_ROOT_CA: CertificateAndKeyPair by lazy { net.corda.nodeapi.internal.DEV_ROOT_CA }

/** A dummy time at which we will be pretending test transactions are created. **/
@JvmField
val TEST_TX_TIME: Instant = Instant.parse("2015-04-17T12:00:00.00Z")