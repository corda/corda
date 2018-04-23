package com.r3.corda.networkmanage.common.utils

import com.r3.corda.networkmanage.common.persistence.CertificateRevocationRequestData
import com.r3.corda.networkmanage.common.signer.Signer
import net.corda.nodeapi.internal.crypto.X509Utilities
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.*
import org.bouncycastle.cert.X509v2CRLBuilder
import org.bouncycastle.cert.jcajce.JcaX509CRLConverter
import org.bouncycastle.cert.jcajce.JcaX509ExtensionUtils
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.operator.ContentSigner
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.net.URL
import java.security.cert.X509CRL
import java.security.cert.X509Certificate
import java.time.Duration
import java.time.Instant
import java.util.*

fun createSignedCrl(issuerCertificate: X509Certificate,
                    endpointUrl: URL,
                    nextUpdateInterval: Duration,
                    signer: Signer,
                    includeInCrl: List<CertificateRevocationRequestData>): X509CRL {
    val extensionUtils = JcaX509ExtensionUtils()
    val builder = X509v2CRLBuilder(X500Name.getInstance(issuerCertificate.issuerX500Principal.encoded), Date())
    builder.addExtension(Extension.authorityKeyIdentifier, false, extensionUtils.createAuthorityKeyIdentifier(issuerCertificate))
    val issuingDistributionPointName = GeneralName(GeneralName.uniformResourceIdentifier, endpointUrl.toString())
    val issuingDistributionPoint = IssuingDistributionPoint(DistributionPointName(GeneralNames(issuingDistributionPointName)), false, false)
    builder.addExtension(Extension.issuingDistributionPoint, true, issuingDistributionPoint)
    builder.setNextUpdate(Date((Instant.now() + nextUpdateInterval).toEpochMilli()))
    includeInCrl.forEach {
        builder.addCRLEntry(it.certificateSerialNumber, Date(it.modifiedAt.toEpochMilli()), it.reason.ordinal)
    }
    val crlHolder = builder.build(CrlContentSigner(signer))
    return JcaX509CRLConverter().setProvider(BouncyCastleProvider()).getCRL(crlHolder)
}

private class CrlContentSigner(private val signer: Signer) : ContentSigner {

    private val outputStream = ByteArrayOutputStream()

    override fun getAlgorithmIdentifier(): AlgorithmIdentifier = X509Utilities.DEFAULT_TLS_SIGNATURE_SCHEME.signatureOID
    override fun getOutputStream(): OutputStream = outputStream
    override fun getSignature(): ByteArray = signer.signBytes(outputStream.toByteArray()).bytes
}