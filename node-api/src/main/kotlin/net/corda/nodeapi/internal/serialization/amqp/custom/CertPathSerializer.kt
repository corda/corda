/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.nodeapi.internal.serialization.amqp.custom

import net.corda.nodeapi.internal.serialization.amqp.CustomSerializer
import net.corda.nodeapi.internal.serialization.amqp.SerializerFactory
import java.io.NotSerializableException
import java.security.cert.CertPath
import java.security.cert.CertificateException
import java.security.cert.CertificateFactory

class CertPathSerializer(factory: SerializerFactory)
    : CustomSerializer.Proxy<CertPath, CertPathSerializer.CertPathProxy>(CertPath::class.java, CertPathProxy::class.java, factory) {
    override fun toProxy(obj: CertPath): CertPathProxy = CertPathProxy(obj.type, obj.encoded)

    override fun fromProxy(proxy: CertPathProxy): CertPath {
        try {
            val cf = CertificateFactory.getInstance(proxy.type)
            return cf.generateCertPath(proxy.encoded.inputStream())
        } catch (ce: CertificateException) {
            val nse = NotSerializableException("java.security.cert.CertPath: $type")
            nse.initCause(ce)
            throw nse
        }
    }

    @Suppress("ArrayInDataClass")
    data class CertPathProxy(val type: String, val encoded: ByteArray)
}