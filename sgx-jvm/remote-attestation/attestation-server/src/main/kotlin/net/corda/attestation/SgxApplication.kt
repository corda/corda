package net.corda.attestation

import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.Security
import javax.ws.rs.ApplicationPath
import javax.ws.rs.core.Application

@ApplicationPath("/")
class SgxApplication : Application() {
    init {
        Security.addProvider(BouncyCastleProvider())
    }
}