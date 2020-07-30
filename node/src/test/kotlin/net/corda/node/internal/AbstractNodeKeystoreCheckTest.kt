package net.corda.node.internal

import com.github.benmanes.caffeine.cache.Caffeine
import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.doAnswer
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.whenever
import net.corda.core.identity.PartyAndCertificate
import net.corda.core.internal.notary.NotaryService
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.messaging.RPCOps
import net.corda.core.node.NetworkParameters
import net.corda.core.node.NodeInfo
import net.corda.core.serialization.SerializeAsToken
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.node.CordaClock
import net.corda.node.VersionInfo
import net.corda.node.services.config.NodeConfiguration
import net.corda.node.services.messaging.MessagingService
import net.corda.node.utilities.AffinityExecutor
import net.corda.node.utilities.BindableNamedCacheFactory
import net.corda.nodeapi.internal.config.CertificateStore
import net.corda.nodeapi.internal.config.FileBasedCertificateStoreSupplier
import net.corda.nodeapi.internal.config.MutualSslConfiguration
import net.corda.nodeapi.internal.crypto.X509KeyStore
import net.corda.nodeapi.internal.crypto.X509Utilities.CORDA_CLIENT_CA
import net.corda.nodeapi.internal.crypto.X509Utilities.CORDA_CLIENT_TLS
import net.corda.nodeapi.internal.crypto.X509Utilities.CORDA_ROOT_CA
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.node.internal.setDriverSerialization
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.Test
import org.mockito.ArgumentMatchers.anyString
import org.slf4j.Logger
import rx.Scheduler
import java.io.IOException
import java.nio.channels.SeekableByteChannel
import java.nio.file.FileSystem
import java.nio.file.Path
import java.nio.file.spi.FileSystemProvider
import java.security.KeyStoreException
import java.security.cert.X509Certificate
import java.time.Duration
import java.util.*

class AbstractNodeKeystoreCheckTest {
    @Test
    fun `starting node in non-dev mode with no key store`() {
        whenever(signingSupplier.get()).doAnswer { throw IOException() }

        assertThatThrownBy {
            node.start()
        }.hasMessageContaining("One or more keyStores (identity or TLS) or trustStore not found.")
    }

    @Test
    fun `starting node in non-dev mode with invalid password`() {
        whenever(signingSupplier.get()).doAnswer { throw KeyStoreException() }

        assertThatThrownBy {
            node.start()
        }.hasMessageContaining("At least one of the keystores or truststore passwords does not match configuration")
    }

    @Test
    fun `starting node in non-dev mode without trusted root`() {
        whenever(trustStore.contains(CORDA_ROOT_CA)).thenReturn(false)

        assertThatThrownBy {
            node.start()
        }.hasMessageContaining("Alias for trustRoot key not found. Please ensure you have an updated trustStore file")
    }

    @Test
    fun `starting node in non-dev mode without alias for TLS key`() {
        whenever(keyStore.contains(CORDA_CLIENT_TLS)).thenReturn(false)

        assertThatThrownBy {
            node.start()
        }.hasMessageContaining("Alias for TLS key not found. Please ensure you have an updated TLS keyStore file")
    }

    @Test
    fun `starting node in non-dev mode without alias for node CA key`() {
        whenever(signingStore.contains(CORDA_CLIENT_CA)).thenReturn(false)

        assertThatThrownBy {
            node.start()
        }.hasMessageContaining("Alias for Node CA key not found. Please ensure you have an updated identity keyStore file")
    }

    @Test
    fun `node should throw exception if cert path does not chain to the trust root`() {
        val untrustedRoot = mock<X509Certificate>()
        whenever(signingStore.query(any<X509KeyStore.() -> List<X509Certificate>>())).thenReturn(mutableListOf(untrustedRoot))

        assertThatThrownBy {
            node.start()
        }.hasMessageContaining("Client CA certificate must chain to the trusted root")
    }

    @Test
    fun `node should throw exception if TLS certificate does not chain to the trust root`() {
        val untrustedRoot = mock<X509Certificate>()
        whenever(keyStore.query(any<X509KeyStore.() -> List<X509Certificate>>())).thenReturn(mutableListOf(untrustedRoot))

        assertThatThrownBy {
            node.start()
        }.hasMessageContaining("TLS certificate must chain to the trusted root")
    }

    private val path = mock<Path>()
    private val fileSystem = mock<FileSystem>()
    private val provider = mock<FileSystemProvider>()
    private val config = mock<NodeConfiguration>()
    private val clock = mock<CordaClock>()
    private val cacheFactory = mock<BindableNamedCacheFactory>()
    private val versionInfo = VersionInfo.UNKNOWN
    private val flowManager = mock<FlowManager>()
    private val thread = mock<AffinityExecutor.ServiceAffinityExecutor>()
    private val channel = mock<SeekableByteChannel>()

    private val trustStore = mock<CertificateStore>()
    private val signingStore = mock<CertificateStore>()
    private val keyStore = mock<CertificateStore>()
    private val sslOptions = mock<MutualSslConfiguration>()
    private val trustSupplier = mock<FileBasedCertificateStoreSupplier>()
    private val signingSupplier = mock<FileBasedCertificateStoreSupplier>()
    private val keySupplier = mock<FileBasedCertificateStoreSupplier>()
    private val trustRoot = mock<X509Certificate>()

    init {
        whenever(path.resolve(anyString())).thenReturn(path)
        whenever(path.fileSystem).thenReturn(fileSystem)
        whenever((fileSystem.provider())).thenReturn(provider)

        whenever(provider.checkAccess(any())).thenAnswer { throw IOException() }
        whenever(provider.newByteChannel(any(), any(), any())).thenReturn(channel)
        whenever(cacheFactory.bindWithConfig(config)).thenReturn(cacheFactory)
        whenever(cacheFactory.bindWithMetrics(any())).thenReturn(cacheFactory)
        whenever(cacheFactory.buildNamed(any<Caffeine<String, Int>>(), anyString(), any())).thenReturn(mock())

        whenever(config.database).thenReturn(mock())
        whenever(config.dataSourceProperties).thenReturn(Properties())
        whenever(config.myLegalName).thenReturn(ALICE_NAME)
        whenever(config.signingCertificateStore).thenReturn(mock())
        whenever(config.baseDirectory).thenReturn(path)
        whenever(config.additionalNodeInfoPollingFrequencyMsec).thenReturn(Duration.ofMinutes(1).toMillis())
        whenever(config.drainingModePollPeriod).thenReturn(Duration.ofDays(1))
        whenever(config.flowExternalOperationThreadPoolSize).thenReturn(1)
        whenever(config.devMode).thenReturn(false)

        whenever(sslOptions.keyStore).thenReturn(keySupplier)
        whenever(sslOptions.trustStore).thenReturn(trustSupplier)
        whenever(config.signingCertificateStore).thenReturn(signingSupplier)
        whenever(trustSupplier.get()).thenReturn(trustStore)
        whenever(signingSupplier.get()).thenReturn(signingStore)
        whenever(keySupplier.get()).thenReturn(keyStore)
        whenever(trustStore.contains(CORDA_ROOT_CA)).thenReturn(true)
        whenever(keyStore.contains(CORDA_CLIENT_TLS)).thenReturn(true)
        whenever(signingStore.contains(CORDA_CLIENT_CA)).thenReturn(true)
        whenever(config.p2pSslOptions).thenReturn(sslOptions)
        whenever(trustStore[CORDA_ROOT_CA]).thenReturn(trustRoot)
        whenever(signingStore.query(any<X509KeyStore.() -> List<X509Certificate>>())).thenReturn(mutableListOf(trustRoot))
        whenever(keyStore.query(any<X509KeyStore.() -> List<X509Certificate>>())).thenReturn(mutableListOf(trustRoot))

        setDriverSerialization(ClassLoader.getSystemClassLoader())
    }

    private val node: AbstractNode<Unit> by lazy {
        object : AbstractNode<Unit>(config, clock, cacheFactory, versionInfo, flowManager, thread) {
            override val log: Logger
                get() = mock()

            override val transactionVerifierWorkerCount: Int = 0

            override val rxIoScheduler: Scheduler
                get() = mock()

            override fun createStartedNode(nodeInfo: NodeInfo, rpcOps: CordaRPCOps, notaryService: NotaryService?) {
            }

            override fun myAddresses(): List<NetworkHostAndPort> = emptyList()

            override fun makeMessagingService(): MessagingService {
                val service = mock<MessagingService>(extraInterfaces = arrayOf(SerializeAsToken::class))
                whenever(service.activeChange).thenReturn(mock())
                whenever(service.ourSenderUUID).thenReturn("9a9af028-3c6b-460e-95c0-be5c8edeae1b")
                return service
            }

            override fun startMessagingService(
                rpcOps: RPCOps,
                nodeInfo: NodeInfo,
                myNotaryIdentity: PartyAndCertificate?,
                networkParameters: NetworkParameters
            ) {
            }
        }
    }
}
