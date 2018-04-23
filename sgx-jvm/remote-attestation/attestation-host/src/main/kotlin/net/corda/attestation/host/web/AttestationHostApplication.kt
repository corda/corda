package net.corda.attestation.host.web

import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.Security
import javax.ws.rs.ApplicationPath
import javax.ws.rs.core.Application

@ApplicationPath("/")
class AttestationHostApplication : Application() {
    init {
        Security.addProvider(BouncyCastleProvider())
    }
}
