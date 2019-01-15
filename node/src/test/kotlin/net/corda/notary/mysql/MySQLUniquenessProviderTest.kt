package net.corda.notary.mysql

import com.codahale.metrics.MetricRegistry
import com.typesafe.config.ConfigFactory
import net.corda.core.contracts.StateRef
import net.corda.core.crypto.DigitalSignature
import net.corda.core.crypto.NullKeys
import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.generateKeyPair
import net.corda.core.flows.NotarisationRequestSignature
import net.corda.core.internal.notary.UniquenessProvider
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.getTestPartyAndCertificate
import net.corda.testing.node.internal.makeInternalTestDataSourceProperties
import org.hamcrest.CoreMatchers.instanceOf
import org.junit.After
import org.junit.Assert.assertThat
import org.junit.Before
import org.junit.Test
import java.time.Clock
import java.util.*

class MySQLUniquenessProviderTest {
    private val dataStoreProperties = makeInternalTestDataSourceProperties(configSupplier = { ConfigFactory.empty() }).apply {
        setProperty("autoCommit", "false")
    }
    private val config = MySQLNotaryConfig(dataStoreProperties, maxBatchSize = 10, maxBatchInputStates = 100)
    private val clock = Clock.systemUTC()!!
    private val party = getTestPartyAndCertificate(ALICE_NAME, generateKeyPair().public).party
    private val uniquenessProvider = MySQLUniquenessProvider(MetricRegistry(), clock, config)

    @Before
    fun before() {
        uniquenessProvider.createTable()
    }

    @After
    fun after() {
        uniquenessProvider.stop()
    }

    @Test
    fun `intra-batch conflict between reference state and input state is detected`() {
        val inputs = listOf(StateRef(SecureHash.randomSHA256(), 0))
        val request = MySQLUniquenessProvider.CommitRequest(
                inputs,
                SecureHash.randomSHA256(),
                party,
                NotarisationRequestSignature(DigitalSignature.WithKey(NullKeys.NullPublicKey, ByteArray(32)), 0),
                null,
                emptyList(),
                UUID.randomUUID()
        )
        val conflictingRequest = MySQLUniquenessProvider.CommitRequest(
                listOf(StateRef(SecureHash.randomSHA256(), 0)),
                SecureHash.randomSHA256(),
                party,
                NotarisationRequestSignature(DigitalSignature.WithKey(NullKeys.NullPublicKey, ByteArray(32)), 0),
                null,
                request.states,
                UUID.randomUUID()
        )
        val results = MySQLUniquenessProvider.CommitStates(listOf(request, conflictingRequest), clock).execute(uniquenessProvider.connection)
        assertThat(results[request.id], instanceOf(UniquenessProvider.Result.Success::class.java))
        assertThat(results[conflictingRequest.id], instanceOf(UniquenessProvider.Result.Failure::class.java))
    }
}
