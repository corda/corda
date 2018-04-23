package net.corda.attestation

import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement
import java.security.Security

class CryptoProvider : TestRule {
    override fun apply(statement: Statement, description: Description?): Statement {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(BouncyCastleProvider())
        }
        return statement
    }
}
