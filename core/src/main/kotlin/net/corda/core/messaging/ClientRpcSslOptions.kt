package net.corda.core.messaging

import java.nio.file.Path

/** As an RPC Client, use this class to point to the truststore that contains the RPC SSL certificate provided by the node admin */
data class ClientRpcSslOptions(val trustStorePath: Path, val trustStorePassword: String, val trustStoreProvider: String = "JKS")