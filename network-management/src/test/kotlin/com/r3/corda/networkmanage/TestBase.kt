package com.r3.corda.networkmanage

import com.nhaarman.mockito_kotlin.mock
import com.r3.corda.networkmanage.common.persistence.CertificateData
import com.r3.corda.networkmanage.common.persistence.CertificateSigningRequest
import com.r3.corda.networkmanage.common.persistence.CertificateStatus
import com.r3.corda.networkmanage.common.persistence.RequestStatus
import net.corda.core.crypto.SecureHash
import net.corda.testing.core.SerializationEnvironmentRule
import org.bouncycastle.pkcs.PKCS10CertificationRequest
import org.junit.Rule
import java.security.cert.CertPath

abstract class TestBase {
    @Rule
    @JvmField
    val testSerialization = SerializationEnvironmentRule()

    protected fun certificateSigningRequest(
            requestId: String = SecureHash.randomSHA256().toString(),
            status: RequestStatus = RequestStatus.NEW,
            legalName: String = "TestLegalName",
            publicKeyHash: SecureHash = SecureHash.randomSHA256(),
            remark: String = "Test remark",
            request: PKCS10CertificationRequest = mock(),
            certData: CertificateData = mock(),
            modifiedBy: List<String> = emptyList()
    ): CertificateSigningRequest {
        return CertificateSigningRequest(
                requestId = requestId,
                status = status,
                legalName = legalName,
                publicKeyHash = publicKeyHash,
                remark = remark,
                certData = certData,
                request = request,
                modifiedBy = modifiedBy
        )
    }

    protected fun certificateData(certStatus: CertificateStatus = CertificateStatus.VALID,
                                  certPath: CertPath = mock()): CertificateData {
        return CertificateData(
                certStatus = certStatus,
                certPath = certPath
        )
    }
}