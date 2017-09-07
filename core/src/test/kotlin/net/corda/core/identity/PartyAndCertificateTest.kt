package net.corda.core.identity

import net.corda.core.crypto.entropyToKeyPair
import net.corda.core.serialization.deserialize
import net.corda.core.serialization.serialize
import net.corda.core.utilities.getX500Name
import net.corda.testing.getTestPartyAndCertificate
import net.corda.testing.withTestSerialization
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.math.BigInteger

class PartyAndCertificateTest {
    @Test
    fun `kryo serialisation`() {
        withTestSerialization {
            val original = getTestPartyAndCertificate(Party(
                    getX500Name(O = "Test Corp", L = "Madrid", C = "ES"),
                    entropyToKeyPair(BigInteger.valueOf(83)).public))
            val copy = original.serialize().deserialize()
            assertThat(copy).isEqualTo(original).isNotSameAs(original)
            assertThat(copy.certPath).isEqualTo(original.certPath)
            assertThat(copy.certificate).isEqualTo(original.certificate)
        }
    }
}
