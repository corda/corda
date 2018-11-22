package net.corda.node.services.keys.cryptoservice.utimaco

fun testConfig(
        port: Int,
        host: String = "127.0.0.1",
        connectionTimeout: Int = 30000,
        timeout: Int = 60000,
        endSessionOnShutdown: Boolean = false,
        keepSessionAlive: Boolean = false,
        keyGroup: String = "TEST.CORDACONNECT.ROOT",
        keySpecifier: Int = 1,
        storeKeysExternal: Boolean = false,
        username: String = "INTEGRATION_TEST",
        password: String = "INTEGRATION_TEST"): UtimacoCryptoService.UtimacoConfig {
    return UtimacoCryptoService.UtimacoConfig(
            UtimacoCryptoService.ProviderConfig(
                    host,
                    port,
                    connectionTimeout,
                    timeout,
                    endSessionOnShutdown,
                    keepSessionAlive,
                    keyGroup,
                    keySpecifier,
                    storeKeysExternal
            ),
            UtimacoCryptoService.KeyGenerationConfiguration(
                    keyGroup = "TEST.CORDACONNECT.ROOT",
                    keySpecifier = 1
            ),
            1,
            UtimacoCryptoService.Credentials(username, password)
    )
}