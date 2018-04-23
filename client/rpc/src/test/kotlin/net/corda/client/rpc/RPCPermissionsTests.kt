/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.client.rpc

import net.corda.core.messaging.RPCOps
import net.corda.node.services.messaging.rpcContext
import net.corda.testing.node.User
import net.corda.testing.node.internal.RPCDriverDSL
import net.corda.testing.node.internal.rpcDriver
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import kotlin.test.assertFailsWith

@RunWith(Parameterized::class)
class RPCPermissionsTests : AbstractRPCTest() {
    companion object {
        const val DUMMY_FLOW = "StartFlow.net.corda.flows.DummyFlow"
        const val ALL_ALLOWED = "ALL"
    }

    /*
     * RPC operation.
     */
    interface TestOps : RPCOps {
        fun validatePermission(method: String, target: String? = null)
    }

    class TestOpsImpl : TestOps {
        override val protocolVersion = 1
        override fun validatePermission(method: String, target: String?) {
            val authorized = if (target == null) {
                rpcContext().isPermitted(method)
            } else {
                rpcContext().isPermitted(method, target)
            }
            if (!authorized) {
                throw PermissionException("RPC user not authorized")
            }
        }
    }

    /**
     * Create an RPC proxy for the given user.
     */
    private fun RPCDriverDSL.testProxyFor(rpcUser: User) = testProxy<TestOps>(TestOpsImpl(), rpcUser).ops

    private fun userOf(name: String, permissions: Set<String>) = User(name, "password", permissions)

    @Test
    fun `empty user cannot use any flows`() {
        rpcDriver {
            val emptyUser = userOf("empty", emptySet())
            val proxy = testProxyFor(emptyUser)
            assertNotAllowed {
                proxy.validatePermission("startFlowDynamic", "net.corda.flows.DummyFlow")
            }
        }
    }

    @Test
    fun `admin user can use any flow`() {
        rpcDriver {
            val adminUser = userOf("admin", setOf(ALL_ALLOWED))
            val proxy = testProxyFor(adminUser)
            proxy.validatePermission("startFlowDynamic", "net.corda.flows.DummyFlow")
            proxy.validatePermission("startTrackedFlowDynamic", "net.corda.flows.DummyFlow")
        }
    }

    @Test
    fun `joe user is allowed to use DummyFlow`() {
        rpcDriver {
            val joeUser = userOf("joe", setOf(DUMMY_FLOW))
            val proxy = testProxyFor(joeUser)
            proxy.validatePermission("startFlowDynamic", "net.corda.flows.DummyFlow")
            proxy.validatePermission("startTrackedFlowDynamic", "net.corda.flows.DummyFlow")
        }
    }

    @Test
    fun `joe user is not allowed to use OtherFlow`() {
        rpcDriver {
            val joeUser = userOf("joe", setOf(DUMMY_FLOW))
            val proxy = testProxyFor(joeUser)
            assertNotAllowed {
                proxy.validatePermission("startFlowDynamic", "net.corda.flows.OtherFlow")
            }
            assertNotAllowed {
                proxy.validatePermission("startTrackedFlowDynamic", "net.corda.flows.OtherFlow")
            }
        }
    }

    @Test
    fun `joe user is not allowed to call other RPC methods`() {
        rpcDriver {
            val joeUser = userOf("joe", setOf(DUMMY_FLOW))
            val proxy = testProxyFor(joeUser)
            assertNotAllowed {
                proxy.validatePermission("nodeInfo")
            }
            assertNotAllowed {
                proxy.validatePermission("networkMapFeed")
            }
        }
    }

    @Test
    fun `checking invokeRpc permissions entitlements`() {
        rpcDriver {
            val joeUser = userOf("joe", setOf("InvokeRpc.networkMapFeed"))
            val proxy = testProxyFor(joeUser)
            assertNotAllowed {
                proxy.validatePermission("nodeInfo")
            }
            assertNotAllowed {
                proxy.validatePermission("startTrackedFlowDynamic", "net.corda.flows.OtherFlow")
            }
            proxy.validatePermission("networkMapFeed")
        }
    }

    @Test
    fun `killing flows requires permission`() {

        rpcDriver {
            val proxy = testProxyFor(userOf("joe", emptySet()))
            assertNotAllowed {
                proxy.validatePermission("killFlow")
            }
        }
    }

    private fun assertNotAllowed(action: () -> Unit) {

        assertFailsWith(PermissionException::class, "User should not be allowed to perform this action.", action)
    }
}
