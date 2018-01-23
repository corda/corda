package com.r3.corda.networkmanage.hsm

import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.whenever
import com.r3.corda.networkmanage.HsmSimulator
import com.r3.corda.networkmanage.hsm.authentication.Authenticator
import com.r3.corda.networkmanage.hsm.authentication.InputReader
import com.r3.corda.networkmanage.hsm.authentication.createProvider
import com.r3.corda.networkmanage.hsm.configuration.Parameters
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.assertTrue

class HsmTest {

    @Rule
    @JvmField
    val hsmSimulator: HsmSimulator = HsmSimulator()

    @Rule
    @JvmField
    val tempFolder = TemporaryFolder()

    private val testParameters = Parameters(
            dataSourceProperties = mock(),
            device = "${hsmSimulator.port}@${hsmSimulator.host}",
            keySpecifier = 1,
            csrPrivateKeyPassword = "",
            networkMapPrivateKeyPassword = "",
            rootPrivateKeyPassword = "",
            keyGroup = "DEV.DOORMAN",
            validDays = 3650,
            csrCertCrlDistPoint = "http://test.com/revoked.crl"
    )

    private lateinit var inputReader: InputReader

    @Before
    fun setUp() {
        inputReader = mock()
        whenever(inputReader.readLine()).thenReturn(hsmSimulator.cryptoUserCredentials().username)
        whenever(inputReader.readPassword(any())).thenReturn(hsmSimulator.cryptoUserCredentials().password)
    }

    @Test
    fun `Authenticator executes the block once user is successfully authenticated`() {
        // given
        val authenticator = Authenticator(testParameters.createProvider(), inputReader = inputReader)
        val executed = AtomicBoolean(false)

        // when
        authenticator.connectAndAuthenticate { _, _ -> executed.set(true) }

        // then
        assertTrue(executed.get())
    }
}