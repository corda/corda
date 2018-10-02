package net.corda.nodeapi

import java.nio.file.Path

data class BrokerRpcSslOptions(val keyStorePath: Path, val keyStorePassword: String)