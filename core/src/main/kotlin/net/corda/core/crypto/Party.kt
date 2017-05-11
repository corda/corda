package net.corda.core.crypto

import org.bouncycastle.asn1.x500.X500Name
import java.security.PublicKey

@Deprecated("Party has moved to identity package", ReplaceWith("net.corda.core.identity.Party"))
class Party(name: X500Name, owningKey: PublicKey) : net.corda.core.identity.Party(name, owningKey)