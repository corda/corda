package net.corda.node.services.network

import com.google.common.jimfs.Configuration
import com.google.common.jimfs.Jimfs
import net.corda.core.internal.createDirectories
import net.corda.core.internal.div
import net.corda.core.internal.exists
import net.corda.core.internal.readObject
import net.corda.core.serialization.deserialize
import net.corda.core.utilities.days
import net.corda.core.utilities.seconds
import net.corda.node.internal.NetworkParametersReader
import net.corda.nodeapi.internal.network.*
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.core.SerializationEnvironmentRule
import net.corda.testing.internal.DEV_ROOT_CA
import net.corda.testing.node.internal.network.NetworkMapServer
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.net.URL
import java.nio.file.FileSystem
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull

class NetworkParametersReaderTest {
    @Rule
    @JvmField
    val testSerialization = SerializationEnvironmentRule(true)

    private val fs: FileSystem = Jimfs.newFileSystem(Configuration.unix())
    private val cacheTimeout = 100000.seconds

    private lateinit var server: NetworkMapServer
    private lateinit var networkMapClient: NetworkMapClient

    @Before
    fun setUp() {
        server = NetworkMapServer(cacheTimeout)
        val address = server.start()
        networkMapClient = NetworkMapClient(URL("http://$address"))
        networkMapClient.start(DEV_ROOT_CA.certificate)
    }

    @After
    fun tearDown() {
        server.close()
        fs.close()
    }

    @Test
    fun `read correct set of parameters from file`() {
        val baseDirectory = fs.getPath("/node").createDirectories()
        val oldParameters = testNetworkParameters(epoch = 1)
        NetworkParametersCopier(oldParameters).install(baseDirectory)
        NetworkParametersCopier(server.networkParameters, update = true).install(baseDirectory) // Parameters update file.
        val parameters = NetworkParametersReader(DEV_ROOT_CA.certificate, networkMapClient, baseDirectory).read().networkParameters
        assertFalse((baseDirectory / NETWORK_PARAMS_UPDATE_FILE_NAME).exists())
        assertEquals(server.networkParameters, parameters)
        // Parameters from update should be moved to `network-parameters` file.
        val parametersFromFile = (baseDirectory / NETWORK_PARAMS_FILE_NAME)
                .readObject<SignedNetworkParameters>()
                .verifiedNetworkMapCert(DEV_ROOT_CA.certificate)
        assertEquals(server.networkParameters, parametersFromFile)
    }

    @Test
    fun `read network parameters from file when network map server is down`() {
        server.close()
        val baseDirectory = fs.getPath("/node").createDirectories()
        val fileParameters = testNetworkParameters(epoch = 1)
        NetworkParametersCopier(fileParameters).install(baseDirectory)
        val parameters = NetworkParametersReader(DEV_ROOT_CA.certificate, networkMapClient, baseDirectory).read().networkParameters
        assertThat(parameters).isEqualTo(fileParameters)
    }

    @Test
    fun `serialized parameters compatibility`() {
        // Network parameters file from before eventHorizon extension
        val inputStream = javaClass.classLoader.getResourceAsStream("network-compatibility/network-parameters")
        assertNotNull(inputStream)
        val inByteArray: ByteArray = inputStream.readBytes()
        val parameters = inByteArray.deserialize<SignedNetworkParameters>()
        assertThat(parameters.verified().eventHorizon).isEqualTo(Int.MAX_VALUE.days)
    }
}