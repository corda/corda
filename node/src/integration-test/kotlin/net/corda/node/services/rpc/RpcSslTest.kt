//package net.corda.node.services.rpc
//
//import net.corda.client.rpc.CordaRPCClient
//import net.corda.client.rpc.internal.createCordaRPCClientWithSsl
//import net.corda.core.identity.CordaX500Name
//import net.corda.core.utilities.getOrThrow
//import net.corda.node.services.Permissions.Companion.all
//import net.corda.node.testsupport.withCertificates
//import net.corda.node.testsupport.withKeyStores
//import net.corda.testing.driver.DriverParameters
//import net.corda.testing.driver.PortAllocation
//import net.corda.testing.driver.driver
//import net.corda.testing.driver.internal.RandomFree
//import net.corda.testing.internal.useSslRpcOverrides
//import net.corda.testing.node.User
//import org.assertj.core.api.Assertions.assertThat
//import org.junit.Test
//
//class RpcSslTest {
//    @Test
//    fun rpc_client_using_ssl() {
//        val user = User("mark", "dadada", setOf(all()))
//        withCertificates { server, client, createSelfSigned, createSignedBy ->
//            val rootCertificate = createSelfSigned(CordaX500Name("SystemUsers/Node", "IT", "R3 London", "London", "London", "GB"))
//            val markCertificate = createSignedBy(CordaX500Name("mark", "IT", "R3 London", "London", "London", "GB"), rootCertificate)
//
//            // truststore needs to contain root CA for how the driver works...
//            server.keyStore["cordaclienttls"] = rootCertificate
//            server.trustStore["cordaclienttls"] = rootCertificate
//            server.trustStore["mark"] = markCertificate
//
//            client.keyStore["mark"] = markCertificate
//            client.trustStore["cordaclienttls"] = rootCertificate
//
//            withKeyStores(server, client) { nodeSslOptions, clientSslOptions ->
//                var successful = false
//                driver(DriverParameters(isDebug = true, startNodesInProcess = true, portAllocation = RandomFree)) {
//                    startNode(rpcUsers = listOf(user), customOverrides = nodeSslOptions.useSslRpcOverrides()).getOrThrow().use { node ->
//                        createCordaRPCClientWithSsl(node.rpcAddress, sslConfiguration = clientSslOptions).start(user.username, user.password).use { connection ->
//                            connection.proxy.apply {
//                                nodeInfo()
//                                successful = true
//                            }
//                        }
//                    }
//                }
//                assertThat(successful).isTrue()
//            }
//        }
//    }
//
//    @Test
//    fun rpc_client_not_using_ssl() {
//        val user = User("mark", "dadada", setOf(all()))
//        var successful = false
//        driver(DriverParameters(isDebug = true, startNodesInProcess = true, portAllocation = RandomFree)) {
//            startNode(rpcUsers = listOf(user)).getOrThrow().use { node ->
//                CordaRPCClient(node.rpcAddress).start(user.username, user.password).use { connection ->
//                    connection.proxy.apply {
//                        nodeInfo()
//                        successful = true
//                    }
//                }
//            }
//        }
//        assertThat(successful).isTrue()
//    }
//}