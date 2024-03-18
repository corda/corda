package net.corda.core.crypto.internal

import net.corda.core.crypto.CordaSecurityProvider
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.pqc.jcajce.provider.BouncyCastlePQCProvider
import java.security.Provider
import java.security.Security
import java.util.Collections.unmodifiableMap

val sunEcProvider = checkNotNull(Security.getProvider("SunEC")).also {
    // Insert Secp256k1SupportProvider just in-front of SunEC for adding back support for secp256k1
    Security.insertProviderAt(Secp256k1SupportProvider(), Security.getProviders().indexOf(it))
}

val cordaSecurityProvider = CordaSecurityProvider().also {
    // Among the others, we should register [CordaSecurityProvider] as the first provider, to ensure that when invoking [SecureRandom()]
    // the [platformSecureRandom] is returned (which is registered in CordaSecurityProvider).
    // Note that internally, [SecureRandom()] will look through all registered providers.
    // Then it returns the first PRNG algorithm of the first provider that has registered a SecureRandom
    // implementation (in our case [CordaSecurityProvider]), or null if none of the registered providers supplies
    // a SecureRandom implementation.
    Security.insertProviderAt(it, 1) // The position is 1-based.
}

val cordaBouncyCastleProvider = BouncyCastleProvider().also {
    Security.addProvider(it)
}

val bouncyCastlePQCProvider = BouncyCastlePQCProvider().apply {
    require(name == "BCPQC") { "Invalid PQCProvider name" }
}.also {
    Security.addProvider(it)
}
// This map is required to defend against users that forcibly call Security.addProvider / Security.removeProvider
// that could cause unexpected and suspicious behaviour.
// i.e. if someone removes a Provider and then he/she adds a new one with the same name.
// The val is immutable to avoid any harmful state changes.
internal val providerMap: Map<String, Provider> = unmodifiableMap(
    listOf(cordaBouncyCastleProvider, cordaSecurityProvider, bouncyCastlePQCProvider)
        .associateByTo(LinkedHashMap(), Provider::getName)
)
