package net.corda.node.internal.protonwrapper.netty

import org.bouncycastle.cert.X509CertificateHolder
import java.net.InetSocketAddress

data class ConnectionChange(val remoteAddress: InetSocketAddress, val remoteCert: X509CertificateHolder?, val connected: Boolean)