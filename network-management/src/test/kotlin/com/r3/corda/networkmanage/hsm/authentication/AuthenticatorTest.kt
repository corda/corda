/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package com.r3.corda.networkmanage.hsm.authentication

import CryptoServerJCE.CryptoServerProvider
import com.nhaarman.mockito_kotlin.*
import com.r3.corda.networkmanage.TestBase
import com.r3.corda.networkmanage.common.signer.AuthenticationException
import org.junit.Before
import org.junit.Test
import kotlin.test.assertFailsWith
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

        // when
        assertFailsWith<AuthenticationException> {
            Authenticator(provider = provider, inputReader = inputReader).connectAndAuthenticate { _, _ -> }
        }

        //then
        verify(provider, never()).loginPassword(any(), any<String>())
        verify(provider, never()).loginSign(any(), any(), any())
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
        verify(provider, never()).loginSign(any(), any(), any())
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
        verify(provider, never()).loginPassword(any(), any<String>())
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
        verify(provider, never()).loginSign(any(), any(), any())
        assertTrue(executed)
    }

    private fun givenAuthenticationResult(sufficient: Boolean, vararg subsequent: Boolean) {
        val stub = whenever(provider.cryptoServer.authState).thenReturn(if (sufficient) 3 else 0)
        subsequent.forEach {
            stub.thenReturn(if (it) 3 else 0)
        }
    }

    private fun givenUserConsoleInputOnReadPassword(input: String) {
        whenever(inputReader.readPassword(any())).thenReturn(input)
    }

    private fun givenUserConsoleInputOnReadLine(input: String) {
        whenever(inputReader.readLine()).thenReturn(input)
    }

}