package com.r3.corda.networkmanage.hsm.authentication

import CryptoServerJCE.CryptoServerProvider
import com.nhaarman.mockito_kotlin.*
import com.r3.corda.networkmanage.TestBase
import org.junit.Before
import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AuthenticatorTest : TestBase() {

    private lateinit var provider: CryptoServerProvider
    private lateinit var inputReader: InputReader

    @Before
    fun setUp() {
        provider = mock()
        whenever(provider.cryptoServer).thenReturn(mock())
        inputReader = mock()
    }

    @Test
    fun `connectAndAuthenticate aborts when user inputs Q`() {
        // given
        givenUserConsoleInputOnReadLine("Q")
        var executed = false

        // when
        Authenticator(provider = provider, inputReader = inputReader).connectAndAuthenticate { _, _ -> executed = true }

        // then
        assertFalse(executed)
        verify(provider, never()).loginPassword(any<String>(), any<String>())
        verify(provider, never()).loginSign(any<String>(), any<String>(), any<String>())
    }

    @Test
    fun `connectAndAuthenticate authenticates user with password`() {
        // given
        val username = "TEST_USER"
        val password = "TEST_PASSWORD"
        givenUserConsoleInputOnReadLine(username)
        givenUserConsoleInputOnReadPassword(password)
        givenAuthenticationResult(true)
        var executed = false

        // when
        Authenticator(provider = provider, inputReader = inputReader).connectAndAuthenticate { _, _ -> executed = true }

        // then
        verify(provider).loginPassword(username, password)
        verify(provider, never()).loginSign(any<String>(), any<String>(), any<String>())
        assertTrue(executed)
    }

    @Test
    fun `connectAndAuthenticate authenticates user with card reader`() {
        // given
        val username = "TEST_USER"
        givenUserConsoleInputOnReadLine(username)
        givenAuthenticationResult(true)
        var executed = false

        // when
        Authenticator(provider = provider, inputReader = inputReader, mode = AuthMode.CARD_READER).connectAndAuthenticate { _, _ -> executed = true }

        // then
        verify(provider).loginSign(username, ":cs2:cyb:USB0", null)
        verify(provider, never()).loginPassword(any<String>(), any<String>())
        assertTrue(executed)
    }

    @Test
    fun `connectAndAuthenticate authenticates multiple users with password`() {
        // given
        val username = "TEST_USER"
        val password = "TEST_PASSWORD"
        givenUserConsoleInputOnReadLine(username)
        givenUserConsoleInputOnReadPassword(password)
        givenAuthenticationResult(false, false, true)
        var executed = false

        // when
        Authenticator(provider = provider, inputReader = inputReader).connectAndAuthenticate { _, _ -> executed = true }

        // then
        verify(provider, times(3)).loginPassword(username, password)
        verify(provider, never()).loginSign(any<String>(), any<String>(), any<String>())
        assertTrue(executed)
    }

    private fun givenAuthenticationResult(sufficient: Boolean, vararg subsequent: Boolean) {
        val stub = whenever(provider.cryptoServer.authState).thenReturn(if (sufficient) 3 else 0)
        subsequent.forEach {
            stub.thenReturn(if (it) 3 else 0)
        }
    }

    private fun givenUserConsoleInputOnReadPassword(input: String) {
        whenever(inputReader.readPassword(any<String>())).thenReturn(input)
    }

    private fun givenUserConsoleInputOnReadLine(input: String) {
        whenever(inputReader.readLine()).thenReturn(input)
    }

}