package com.r3.corda.doorman.persistence

import com.r3.corda.doorman.CertificateUtilities
import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.commonName
import net.corda.node.utilities.instant
import net.corda.node.utilities.transaction
import org.apache.commons.io.IOUtils
import org.bouncycastle.pkcs.PKCS10CertificationRequest
import org.jetbrains.exposed.sql.*
import java.security.cert.Certificate
import java.time.Instant
import javax.sql.rowset.serial.SerialBlob

// TODO Relax the uniqueness requirement to be on the entire X.500 subject rather than just the legal name
class DBCertificateRequestStorage(private val database: Database) : CertificationRequestStorage {
    private object DataTable : Table("certificate_signing_request") {
        val requestId = varchar("request_id", 64).index().primaryKey()
        val hostName = varchar("hostName", 100)
        val ipAddress = varchar("ip_address", 15)
        val legalName = varchar("legal_name", 256)
        // TODO : Do we need to store this in column? or is it ok with blob.
        val request = blob("request")
        val requestTimestamp = instant("request_timestamp")
        val processTimestamp = instant("process_timestamp").nullable()
        val certificate = blob("certificate").nullable()
        val rejectReason = varchar("reject_reason", 256).nullable()
    }

    init {
        // Create table if not exists.
        database.transaction {
            SchemaUtils.create(DataTable)
        }
    }

    override fun saveRequest(certificationData: CertificationRequestData): String {
        val legalName = certificationData.request.subject.commonName
        val requestId = SecureHash.randomSHA256().toString()
        database.transaction {
            val duplicate = DataTable.select {
                // A duplicate legal name is one where a previously approved, or currently pending, request has the same legal name.
                // A rejected request with the same legal name doesn't count as a duplicate
                DataTable.legalName eq legalName and (DataTable.certificate.isNotNull() or DataTable.processTimestamp.isNull())
            }.any()
            val rejectReason = if (duplicate) {
                "Duplicate legal name"
            } else if ("[=,]".toRegex() in legalName) {
                "Legal name cannot contain '=' or ','"
            } else {
                null
            }
            val now = Instant.now()
            DataTable.insert {
                it[this.requestId] = requestId
                it[hostName] = certificationData.hostName
                it[ipAddress] = certificationData.ipAddress
                it[this.legalName] = legalName
                it[request] = SerialBlob(certificationData.request.encoded)
                it[requestTimestamp] = now
                if (rejectReason != null) {
                    it[this.rejectReason] = rejectReason
                    it[processTimestamp] = now
                }
            }
        }
        return requestId
    }

    override fun getResponse(requestId: String): CertificateResponse {
        return database.transaction {
            val response = DataTable
                    .select { DataTable.requestId eq requestId and DataTable.processTimestamp.isNotNull() }
                    .map { Pair(it[DataTable.certificate]?.let { IOUtils.toByteArray(it.binaryStream) }, it[DataTable.rejectReason]) }
                    .singleOrNull()
            if (response == null) {
                CertificateResponse.NotReady
            } else {
                val (certificate, rejectReason) = response
                if (certificate != null) {
                    CertificateResponse.Ready(CertificateUtilities.toX509Certificate(certificate))
                } else {
                    CertificateResponse.Unauthorised(rejectReason!!)
                }
            }
        }
    }

    override fun approveRequest(requestId: String, generateCertificate: CertificationRequestData.() -> Certificate) {
        database.transaction {
            val request = singleRequestWhere { DataTable.requestId eq requestId and DataTable.processTimestamp.isNull() }
            if (request != null) {
                DataTable.update({ DataTable.requestId eq requestId }) {
                    it[certificate] = SerialBlob(request.generateCertificate().encoded)
                    it[processTimestamp] = Instant.now()
                }
            }
        }
    }

    override fun rejectRequest(requestId: String, rejectReason: String) {
        database.transaction {
            val request = singleRequestWhere { DataTable.requestId eq requestId and DataTable.processTimestamp.isNull() }
            if (request != null) {
                DataTable.update({ DataTable.requestId eq requestId }) {
                    it[this.rejectReason] = rejectReason
                    it[processTimestamp] = Instant.now()
                }
            }
        }
    }

    override fun getRequest(requestId: String): CertificationRequestData? {
        return database.transaction {
            singleRequestWhere { DataTable.requestId eq requestId }
        }
    }

    override fun getPendingRequestIds(): List<String> {
        return database.transaction {
            DataTable.select { DataTable.processTimestamp.isNull() }.map { it[DataTable.requestId] }
        }
    }

    override fun getApprovedRequestIds(): List<String> = emptyList()

    private fun singleRequestWhere(where: SqlExpressionBuilder.() -> Op<Boolean>): CertificationRequestData? {
        return DataTable
                .select(where)
                .map { CertificationRequestData(it[DataTable.hostName], it[DataTable.ipAddress], PKCS10CertificationRequest(IOUtils.toByteArray(it[DataTable.request].binaryStream))) }
                .singleOrNull()
    }
}