package net.corda.node.internal.security

import net.corda.core.messaging.RPCOps
import net.corda.node.internal.rpc.proxies.RpcAuthHelper.methodFullName
import org.junit.Test

import java.time.ZonedDateTime
import kotlin.test.assertEquals

class RPCPermissionResolverTest {

    @Suppress("unused")
    interface Alpha : RPCOps {
        fun readAlpha() : String
    }

    @Suppress("unused")
    interface Beta : Alpha {
        val betaValue : Int

        fun writeBeta(foo: Int)

        fun nothingSpecial() : Int
    }

    @Suppress("unused")
    interface Gamma : Beta {
        fun readGamma() : ZonedDateTime
    }

    private val readAlphaMethod = methodFullName(Alpha::class.java.getMethod("readAlpha"))
    private val readAlphaMethodKey = readAlphaMethod.toLowerCase()

    @Test(timeout=300_000)
    fun `test Alpha`() {
        with(RPCPermissionResolver.inspectInterface(Alpha::class.java.name)) {
            assertEquals(3, size, toString()) // protocolVersion, ALL, readAlpha
            assertEquals(setOf(readAlphaMethod), this[readAlphaMethodKey], toString())
        }
    }

    @Test(timeout=300_000)
    fun `test Beta`() {
        with(RPCPermissionResolver.inspectInterface(Beta::class.java.name)) {
            assertEquals(6, size, toString()) // protocolVersion, ALL, readAlpha
            // and 3 x Beta methods
        }
    }

    @Test(timeout=300_000)
    fun `test Gamma`() {
        with(RPCPermissionResolver.inspectInterface(Gamma::class.java.name)) {
            assertEquals(7, size, toString()) // protocolVersion, ALL, readAlpha,
            // 3 x Beta methods and 1 Gamma method
        }
    }
}