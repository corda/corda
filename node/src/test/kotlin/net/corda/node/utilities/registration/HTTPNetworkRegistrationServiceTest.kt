package net.corda.node.utilities.registration

import com.nhaarman.mockito_kotlin.anyOrNull
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.whenever
import net.corda.node.VersionInfo
import net.corda.node.services.config.NetworkServicesConfig
import net.corda.coretesting.internal.rigorousMock
import org.bouncycastle.pkcs.PKCS10CertificationRequest
import org.junit.Test
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.*
import kotlin.test.assertEquals

class HTTPNetworkRegistrationServiceTest {

    @Test(timeout=300_000)
	fun `post request properties`() {
        val versionInfo = VersionInfo.UNKNOWN
        val pnm = UUID.randomUUID();
        val config = rigorousMock<NetworkServicesConfig>().also {
            doReturn(pnm).whenever(it).pnm
            doReturn(null).whenever(it).csrToken
        }
        var header = submitDummyRequest(versionInfo, config).requestProperties
        assertEquals(4, header.size)
        assertEquals(listOf(pnm.toString()), header["Private-Network-Map"])
        assertEquals(listOf(versionInfo.platformVersion.toString()), header["Platform-Version"])
        assertEquals(listOf(versionInfo.releaseVersion), header["Client-Version"])
        assertEquals(listOf("application/octet-stream"), header["Content-Type"])
    }

    @Test(timeout=300_000)
	fun `post request properties with CSR token`() {
        val versionInfo = VersionInfo.UNKNOWN
        val config = rigorousMock<NetworkServicesConfig>().also {
            doReturn(null).whenever(it).pnm
            doReturn("My-TOKEN").whenever(it).csrToken
        }
        var header = submitDummyRequest(versionInfo, config).requestProperties
        assertEquals(5, header.size)
        assertEquals(listOf(""), header["Private-Network-Map"])
        assertEquals(listOf(versionInfo.platformVersion.toString()), header["Platform-Version"])
        assertEquals(listOf(versionInfo.releaseVersion), header["Client-Version"])
        assertEquals(listOf("application/octet-stream"), header["Content-Type"])
        assertEquals(listOf("My-TOKEN"), header["X-CENM-Submission-Token"])
    }

    private fun submitDummyRequest(versionInfo: VersionInfo, config: NetworkServicesConfig) : HttpURLConnection {
        val request = rigorousMock<PKCS10CertificationRequest>().also {
            doReturn("dummy".toByteArray()).whenever(it).encoded
        }
        val inputStream = rigorousMock<InputStream>().also {
            doReturn(-1).whenever(it).read()
        }
        val connection = rigorousMock<HttpURLConnection>().also {
            doReturn(inputStream).whenever(it).inputStream
            doReturn(mock<OutputStream>()).whenever(it).outputStream
            doReturn(HttpURLConnection.HTTP_OK).whenever(it).responseCode
        }
        val url = rigorousMock<URL>().also {
            doReturn(connection).whenever(it).openConnection()
            doReturn(connection).whenever(it).openConnection(anyOrNull())
        }
        val service = HTTPNetworkRegistrationService(config, versionInfo, url)
        service.submitRequest(request)
        return connection
    }
}
