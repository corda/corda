package net.corda.attestation.ias

import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.Security
import javax.ws.rs.ApplicationPath
import javax.ws.rs.core.Application

@ApplicationPath("/")
class IASProxyApplication : Application() {
    init {
        Security.addProvider(BouncyCastleProvider())
    }
}
