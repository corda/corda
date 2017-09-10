@file:JvmName("X509Utils")

package net.corda.core.utilities

import net.corda.core.internal.toX509CertHolder
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.X509CertificateHolder
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import java.security.cert.X509Certificate

val X509Certificate.subject: X500Name get() = toX509CertHolder().subject
val X509CertificateHolder.cert: X509Certificate get() = JcaX509CertificateConverter().getCertificate(this)