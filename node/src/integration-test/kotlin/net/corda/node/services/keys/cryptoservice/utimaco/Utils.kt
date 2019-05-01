package net.corda.node.services.keys.cryptoservice.utimaco

import net.corda.nodeapi.internal.cryptoservice.utimaco.UtimacoCryptoService

fun testConfig(
        port: Int,
        host: String = "127.0.0.1",
        connectionTimeout: Int = 30000,
        timeout: Int = 60000,
        keepSessionAlive: Boolean = false,
        keyGroup: String = "TEST.CORDACONNECT.ROOT",
        keySpecifier: Int = 1,
        username: String = "INTEGRATION_TEST",
        password: String = "INTEGRATION_TEST"): UtimacoCryptoService.UtimacoConfig {
    return UtimacoCryptoService.UtimacoConfig(
            host = host,
            port = port,
            connectionTimeout = connectionTimeout,
            timeout = timeout,
            keepSessionAlive = keepSessionAlive,
            keyGroup = keyGroup,
            keySpecifier = keySpecifier,
            authThreshold = 1,
            username = username,
            password = password
    )
}