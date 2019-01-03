package net.corda.node.services.network

import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.times
import com.nhaarman.mockito_kotlin.verify
import com.sun.xml.internal.messaging.saaj.util.ByteOutputStream
import net.corda.core.crypto.SecureHash
import net.corda.core.internal.SignedDataWithCert
import net.corda.core.node.NetworkParameters
import net.corda.core.node.services.NetworkParametersService
import net.corda.node.internal.DBNetworkParametersStorage
import net.corda.nodeapi.internal.createDevNetworkMapCa
import net.corda.nodeapi.internal.crypto.CertificateAndKeyPair
import net.corda.nodeapi.internal.persistence.CordaPersistence
import net.corda.nodeapi.internal.persistence.DatabaseConfig
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.core.SerializationEnvironmentRule
import net.corda.testing.internal.DEV_INTERMEDIATE_CA
import net.corda.testing.internal.DEV_ROOT_CA
import net.corda.testing.internal.TestingNamedCacheFactory
import net.corda.testing.internal.configureDatabase
import net.corda.testing.node.MockServices
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.PrintStream
import kotlin.streams.toList

class DBNetworkParametersStorageTest {
    @Rule
    @JvmField
    val testSerialization = SerializationEnvironmentRule(true)

    private lateinit var networkMapClient: NetworkMapClient
    private lateinit var networkParametersService: NetworkParametersService
    private lateinit var database: CordaPersistence

    private val certKeyPair: CertificateAndKeyPair = createDevNetworkMapCa()
    private lateinit var netParams1: SignedDataWithCert<NetworkParameters>
    private lateinit var netParams2: SignedDataWithCert<NetworkParameters>
    private lateinit var incorrectParams: SignedDataWithCert<NetworkParameters>
    private lateinit var hash1: SecureHash
    private lateinit var hash2: SecureHash
    private lateinit var hash3: SecureHash

    @Before
    fun setUp() {
        netParams1 = certKeyPair.sign(testNetworkParameters(minimumPlatformVersion = 1))
        netParams2 = certKeyPair.sign(testNetworkParameters(minimumPlatformVersion = 2))
        incorrectParams = createDevNetworkMapCa(DEV_INTERMEDIATE_CA).sign(testNetworkParameters(minimumPlatformVersion = 3))
        hash1 = netParams1.raw.hash
        hash2 = netParams2.raw.hash
        hash3 = incorrectParams.raw.hash
        database = configureDatabase(
                MockServices.makeTestDataSourceProperties(),
                DatabaseConfig(),
                { null },
                { null }
        )
        networkMapClient = createMockNetworkMapClient()
        networkParametersService = DBNetworkParametersStorage(TestingNamedCacheFactory(), database, networkMapClient).apply {
            database.transaction {
                setCurrentParameters(netParams1, DEV_ROOT_CA.certificate)
            }
        }
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `set current parameters`() {
        assertThat(networkParametersService.currentHash).isEqualTo(hash1)
        assertThat(networkParametersService.lookup(hash1)).isEqualTo(netParams1.verified())
    }

    @Test
    fun `get default parameters`() {
        // TODO After implementing default endpoint on network map check it is correct, for now we set it to current.
        assertThat(networkParametersService.defaultHash).isEqualTo(hash1)
    }

    @Test
    fun `download parameters from network map server`() {
        database.transaction {
            val netParams = networkParametersService.lookup(hash2)
            assertThat(networkParametersService.lookup(hash2)).isEqualTo(netParams)
            verify(networkMapClient, times(1)).getNetworkParameters(hash2)

        }
    }

    @Test
    fun `try save parameters with incorrect signature`() {
        database.transaction {
            val consoleOutput = interceptConsoleOutput {
                networkParametersService.lookup(hash3)
            }
            assertThat(consoleOutput).anySatisfy {
                it.contains("Caused by: java.security.cert.CertPathValidatorException: subject/issuer name chaining check failed")
            }
        }
    }

    private fun interceptConsoleOutput(block: () -> Unit): List<String> {
        val oldOut = System.out
        val out = ByteOutputStream()
        System.setOut(PrintStream(out))
        block()
        System.setOut(oldOut)
        return out.bytes.inputStream().bufferedReader().lines().toList()
    }

    private fun createMockNetworkMapClient(): NetworkMapClient {
        return mock {
            on { getNetworkParameters(any()) }.then {
                val hash = it.arguments[0]
                when (hash) {
                    hash1 -> netParams1
                    hash2 -> netParams2
                    hash3 -> incorrectParams
                    else -> null
                }
            }
        }
    }
}
