package net.corda.bridge.services

import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.whenever
import net.corda.bridge.services.receiver.CryptoServiceSigningService
import net.corda.nodeapi.internal.config.CertificateStore
import net.corda.nodeapi.internal.config.FileBasedCertificateStoreSupplier
import net.corda.nodeapi.internal.config.MutualSslConfiguration
import net.corda.nodeapi.internal.crypto.X509KeyStore
import net.corda.nodeapi.internal.cryptoservice.CryptoService
import net.corda.testing.core.DUMMY_BANK_A_NAME
import net.corda.testing.core.SerializationEnvironmentRule
import net.corda.testing.internal.participant
import org.junit.Rule
import org.junit.Test
import rx.subjects.BehaviorSubject
import java.util.concurrent.SynchronousQueue
import kotlin.test.assertEquals

class CryptoServiceSigningServiceTest {
    @Rule
    @JvmField
    val serializationEnvironment = SerializationEnvironmentRule(true)

    private enum class Operation { CONNECT, HEARTBEAT }
    private class CryptoServiceMock(val mock: CryptoService = participant<CryptoService>()) : CryptoService by mock {
        private val certificateStore = participant<CertificateStore>().also {
            doReturn(X509KeyStore("")).whenever(it).value
        }
        private val certificateSupplier = participant<FileBasedCertificateStoreSupplier>().also {
            doReturn(certificateStore).whenever(it).get(any())
        }
        val sslConfig = participant<MutualSslConfiguration>().also {
            doReturn(certificateSupplier).whenever(it).keyStore
            doReturn(certificateSupplier).whenever(it).trustStore
        }
        private val sleepQueue = SynchronousQueue<Long>()
        private val heartbeatChange = BehaviorSubject.create<Operation>()
        private val heartbeatFollower = heartbeatChange.serialize().toBlocking().iterator
        var failHeartbeat = false
        var failConnect = false

        override fun containsKey(alias: String): Boolean {
            heartbeatChange.onNext(Operation.HEARTBEAT)
            if (failHeartbeat) throw IllegalArgumentException("Heartbeat failed") else return true
        }

        fun connect(): CryptoService {
            heartbeatChange.onNext(Operation.CONNECT)
            if (failConnect) throw IllegalArgumentException("Connect failed") else return this
        }

        // block loop() thread until nextOperation(true) is called
        fun sleep(millis: Long) = sleepQueue.put(millis)

        fun nextOperation(sleep: Boolean = false): Operation {
            if (sleep) sleepQueue.take() // release sleep() in loop() thread
            return heartbeatFollower.next()
        }
    }

    @Test
    fun `Signing service lifecycle test`() {
        // create signing service
        val cryptoService = CryptoServiceMock()
        val signingService = CryptoServiceSigningService(null, DUMMY_BANK_A_NAME, cryptoService.sslConfig, 10000,
                TestAuditService(), name = "P2P", sleep = cryptoService::sleep, makeCryptoService = cryptoService::connect)
        val stateFollower = signingService.activeChange.toBlocking().iterator
        assertEquals(false, stateFollower.next())
        assertEquals(false, signingService.active)

        // start signing service and receive 1st heartbeat
        signingService.start()
        assertEquals(true, stateFollower.next())
        assertEquals(true, signingService.active)
        assertEquals(Operation.CONNECT, cryptoService.nextOperation())
        assertEquals(Operation.HEARTBEAT, cryptoService.nextOperation())

        // 2nd heartbeat OK
        assertEquals(Operation.HEARTBEAT, cryptoService.nextOperation(true))

        // 3rd heartbeat failed: signing service disconnected
        cryptoService.failConnect = true
        cryptoService.failHeartbeat = true
        assertEquals(Operation.HEARTBEAT, cryptoService.nextOperation(true))
        assertEquals(false, stateFollower.next())
        assertEquals(false, signingService.active)

        // unable to connect to crypto service
        assertEquals(Operation.CONNECT, cryptoService.nextOperation(true))
        assertEquals(false, signingService.active)

        // crypto service is connected but 1st heartbeat still fails
        cryptoService.failConnect = false
        assertEquals(Operation.CONNECT, cryptoService.nextOperation(true))
        assertEquals(Operation.HEARTBEAT, cryptoService.nextOperation())
        assertEquals(false, signingService.active)

        // signing service is online
        cryptoService.failHeartbeat = false
        assertEquals(Operation.CONNECT, cryptoService.nextOperation(true))
        assertEquals(Operation.HEARTBEAT, cryptoService.nextOperation())
        assertEquals(true, stateFollower.next())
        assertEquals(true, signingService.active)

        // restart service and receive 2 heartbeats
        signingService.stop()
        signingService.start()
        assertEquals(Operation.CONNECT, cryptoService.nextOperation())
        assertEquals(Operation.HEARTBEAT, cryptoService.nextOperation())
        assertEquals(Operation.HEARTBEAT, cryptoService.nextOperation(true))
    }
}