///*
// * R3 Proprietary and Confidential
// *
// * Copyright (c) 2018 R3 Limited.  All rights reserved.
// *
// * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
// *
// * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
// */
//
//package net.corda.notarytest
//
//import net.corda.cordform.CordformContext
//import net.corda.cordform.CordformDefinition
//import net.corda.cordform.CordformNode
//import net.corda.core.identity.CordaX500Name
//import net.corda.node.services.config.NotaryConfig
//import net.corda.nodeapi.internal.DevIdentityGenerator
//
//fun main(args: Array<String>) = JDBCNotaryCordform().nodeRunner().deployAndRunNodes()
//
//class JDBCNotaryCordform : CordformDefinition() {
//    private val clusterName = CordaX500Name("Mysql Notary", "Zurich", "CH")
//    private val notaryNames = createNotaryNames(3)
//
//    private fun createNotaryNames(clusterSize: Int) = (0 until clusterSize).map {
//        CordaX500Name("Notary Service $it", "Zurich", "CH")
//    }
//
//    init {
//        fun notaryNode(index: Int, configure: CordformNode.() -> Unit) = node {
//            name(notaryNames[index])
//            notary(
//                    NotaryConfig(
//                            validating = true,
//                            custom = true
//                    )
//            )
//            extraConfig = mapOf("custom" to
//                    mapOf(
//                            "mysql" to mapOf(
//                                    "dataSource" to mapOf(
//                                            // Update the db address/port accordingly
//                                            "jdbcUrl" to "jdbc:mysql://localhost:330${6 + index}/corda?rewriteBatchedStatements=true&useSSL=false&failOverReadOnly=false",
//                                            "username" to "corda",
//                                            "password" to "awesome",
//                                            "autoCommit" to "false")
//                            ),
//                            "graphiteAddress" to "performance-metrics.northeurope.cloudapp.azure.com:2004"
//                    )
//            )
//            configure()
//        }
//
//        notaryNode(0) {
//            p2pPort(10009)
//            rpcSettings {
//                address("localhost:10010")
//                adminAddress("localhost:10110")
//            }
//            rpcUsers(notaryDemoUser)
//        }
//        notaryNode(1) {
//            p2pPort(10013)
//            rpcSettings {
//                address("localhost:10014")
//                adminAddress("localhost:10114")
//            }
//            rpcUsers(notaryDemoUser)
//        }
//        notaryNode(2) {
//            p2pPort(10017)
//            rpcSettings {
//                address("localhost:10018")
//                adminAddress("localhost:10118")
//            }
//            rpcUsers(notaryDemoUser)
//        }
//    }
//
//    override fun setup(context: CordformContext) {
//        DevIdentityGenerator.generateDistributedNotarySingularIdentity(
//                notaryNames.map { context.baseDirectory(it.toString()) },
//                clusterName
//        )
//    }
//}