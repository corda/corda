package net.corda.node.internal.security

import net.corda.client.rpc.internal.security.READ_ONLY
import net.corda.core.messaging.RPCOps
import net.corda.ext.api.rpc.proxies.RpcAuthHelper.methodFullName
import net.corda.client.rpc.internal.security.RpcPermissionGroup
import org.junit.Test

import java.time.ZonedDateTime
import kotlin.test.assertEquals

class RPCPermissionResolverTest {

    companion object {
        const val SENSITIVE = "SENSITIVE"
    }

    @Suppress("unused")
    interface Alpha : RPCOps {
        @RpcPermissionGroup(READ_ONLY)
        fun readAlpha() : String
    }

    @Suppress("unused")
    interface Beta : Alpha {
        @RpcPermissionGroup(READ_ONLY, SENSITIVE)
        val betaValue : Int

        @RpcPermissionGroup(SENSITIVE)
        fun writeBeta(foo: Int)

        fun nothingSpecial() : Int
    }

    @Suppress("unused")
    interface Gamma : Beta {
        fun readGamma() : ZonedDateTime
    }

    private val readAlphaMethod = methodFullName(Alpha::class.java.getMethod("readAlpha"))
    private val readAlphaMethodKey = readAlphaMethod.toLowerCase()
    private val readOnlyAlphaKey = methodFullName(Alpha::class.java, READ_ONLY).toLowerCase()
    private val readOnlyBetaKey = methodFullName(Beta::class.java, READ_ONLY).toLowerCase()
    private val sensitiveBetaKey = methodFullName(Beta::class.java, SENSITIVE).toLowerCase()
    private val getBetaMethod = methodFullName(Beta::class.java.getMethod("getBetaValue"))
    private val writeBetaMethod = methodFullName(Beta::class.java.getMethod("writeBeta", Int::class.java))

    @Test
    fun `test Alpha`() {
        with(RPCPermissionResolver.inspectInterface(Alpha::class.java.name)) {
            assertEquals(4, size, toString()) // protocolVersion, ALL, READ_ONLY, readAlpha
            assertEquals(setOf(readAlphaMethod), this[readOnlyAlphaKey], toString())
            assertEquals(setOf(readAlphaMethod), this[readAlphaMethodKey], toString())
        }
    }

    @Test
    fun `test Beta`() {
        with(RPCPermissionResolver.inspectInterface(Beta::class.java.name)) {
            assertEquals(9, size, toString()) // protocolVersion, ALL, READ_ONLY(Aplha), READ_ONLY(Beta), readAlpha, SENSITIVE
                                                        // and 3 x Beta methods
            assertEquals(setOf(readAlphaMethod), this[readOnlyAlphaKey], toString())
            assertEquals(setOf(getBetaMethod), this[readOnlyBetaKey], toString())
            assertEquals(setOf(writeBetaMethod, getBetaMethod), this[sensitiveBetaKey], toString())
        }
    }

    @Test
    fun `test Gamma`() {
        with(RPCPermissionResolver.inspectInterface(Gamma::class.java.name)) {
            assertEquals(10, size, toString()) // protocolVersion, ALL, READ_ONLY(Aplha), READ_ONLY(Beta), readAlpha, SENSITIVE,
                                                        // 3 x Beta methods and 1 Gamma method
            assertEquals(setOf(readAlphaMethod), this[readOnlyAlphaKey], toString())
            assertEquals(setOf(getBetaMethod), this[readOnlyBetaKey], toString())
            assertEquals(setOf(writeBetaMethod, getBetaMethod), this[sensitiveBetaKey], toString())
        }
    }
}