package com.r3.corda.networkmanage

import com.nhaarman.mockito_kotlin.mock
import com.r3.corda.networkmanage.common.persistence.CertificateData
import com.r3.corda.networkmanage.common.persistence.CertificateSigningRequest
import com.r3.corda.networkmanage.common.persistence.CertificateStatus
import com.r3.corda.networkmanage.common.persistence.RequestStatus
import net.corda.core.crypto.SecureHash
import net.corda.core.utilities.seconds
import net.corda.nodeapi.internal.NetworkParameters
import net.corda.nodeapi.internal.NotaryInfo
import net.corda.testing.SerializationEnvironmentRule
import org.bouncycastle.pkcs.PKCS10CertificationRequest
import org.junit.Rule
import java.security.cert.CertPath
import java.time.Duration
import java.time.Instant

abstract class TestBase {
    @Rule
    @JvmField
    val testSerialization = SerializationEnvironmentRule()

    protected fun certificateSigningRequest(
            requestId: String = SecureHash.randomSHA256().toString(),
            status: RequestStatus = RequestStatus.NEW,
            legalName: String = "TestLegalName",
            remark: String = "Test remark",
            request: PKCS10CertificationRequest = mock(),
            certData: CertificateData = mock(),
            modifiedBy: List<String> = emptyList()
    ): CertificateSigningRequest {
        return CertificateSigningRequest(
                requestId = requestId,
                status = status,
                legalName = legalName,
                remark = remark,
                certData = certData,
                request = request,
                modifiedBy = modifiedBy
        )
    }

    protected fun certificateData(publicKeyHash: String = SecureHash.randomSHA256().toString(),
                                  certStatus: CertificateStatus = CertificateStatus.VALID,
                                  certPath: CertPath = mock()): CertificateData {
        return CertificateData(
                publicKeyHash = publicKeyHash,
                certStatus = certStatus,
                certPath = certPath
        )
    }

    // TODO remove this once testNetworkParameters are updated with default parameters
    protected fun createNetworkParameters(minimumPlatformVersion: Int = 1,
                                          notaries: List<NotaryInfo> = emptyList(),
                                          eventHorizon: Duration = 1.seconds,
                                          maxMessageSize: Int = 0,
                                          maxTransactionSize: Int = 0,
                                          modifiedTime: Instant = Instant.now(),
                                          epoch: Int = 1): NetworkParameters {
        return NetworkParameters(
                minimumPlatformVersion = minimumPlatformVersion,
                notaries = notaries,
                eventHorizon = eventHorizon,
                maxMessageSize = maxMessageSize,
                maxTransactionSize = maxTransactionSize,
                modifiedTime = modifiedTime,
                epoch = epoch
        )
    }
}