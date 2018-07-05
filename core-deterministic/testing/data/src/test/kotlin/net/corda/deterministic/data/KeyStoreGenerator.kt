package net.corda.deterministic.data

import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.io.OutputStream
import java.math.BigInteger.TEN
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.spec.ECGenParameterSpec
import java.util.*
import java.util.Calendar.*

object KeyStoreGenerator {
    private val keyPairGenerator: KeyPairGenerator = KeyPairGenerator.getInstance("EC").apply {
        initialize(ECGenParameterSpec("secp256k1"))
    }

    fun writeKeyStore(output: OutputStream, alias: String, password: CharArray) {
        val keyPair = keyPairGenerator.generateKeyPair()
        val signer = JcaContentSignerBuilder("SHA256WithECDSA").build(keyPair.private)
        val dname = X500Name("CN=Enclavelet")
        val startDate = Calendar.getInstance().let { cal ->
            cal.time = Date()
            cal.add(HOUR, -1)
            cal.time
        }
        val endDate = Calendar.getInstance().let { cal ->
            cal.time = startDate
            cal.add(YEAR, 1)
            cal.time
        }
        val certificate = JcaX509v3CertificateBuilder(
            dname,
            TEN,
            startDate,
            endDate,
            dname,
            keyPair.public
        ).build(signer)
        val x509 = JcaX509CertificateConverter().getCertificate(certificate)

        KeyStore.getInstance("PKCS12").apply {
            load(null, password)
            setKeyEntry(alias, keyPair.private, password, arrayOf(x509))
            store(output, password)
        }
    }
}