package com.r3corda.netpermission.internal.persistence

import com.r3corda.core.crypto.SecureHash
import com.r3corda.node.utilities.*
import org.jetbrains.exposed.sql.*
import java.security.cert.Certificate
import java.time.LocalDateTime

class DBCertificateRequestStorage(private val database: Database) : CertificationRequestStorage {
    private object DataTable : Table("certificate_signing_request") {
        val requestId = varchar("request_id", 64).index().primaryKey()
        val hostName = varchar("hostName", 100)
        val ipAddress = varchar("ip_address", 15)
        // TODO : Do we need to store this in column? or is it ok with blob.
        val request = blob("request")
        val requestTimestamp = localDateTime("request_timestamp")
        val approvedTimestamp = localDateTime("approved_timestamp").nullable()
        val certificate = blob("certificate").nullable()
    }

    init {
        // Create table if not exists.
        databaseTransaction(database) {
            SchemaUtils.create(DataTable)
        }
    }

    override fun getCertificate(requestId: String): Certificate? {
        return databaseTransaction(database) { DataTable.select { DataTable.requestId.eq(requestId) }.map { it[DataTable.certificate] }.filterNotNull().map { deserializeFromBlob<Certificate>(it) }.firstOrNull() }
    }

    override fun saveCertificate(requestId: String, certificateGenerator: (CertificationData) -> Certificate) {
        databaseTransaction(database) {
            withFinalizables { finalizables ->
                getRequest(requestId)?.let {
                    val clientCert = certificateGenerator(it)
                    DataTable.update({ DataTable.requestId eq requestId }) {
                        it[approvedTimestamp] = LocalDateTime.now()
                        it[certificate] = serializeToBlob(clientCert, finalizables)
                    }
                }
            }
        }
    }

    override fun getRequest(requestId: String): CertificationData? {
        return databaseTransaction(database) { DataTable.select { DataTable.requestId eq requestId }.map { CertificationData(it[DataTable.hostName], it[DataTable.ipAddress], deserializeFromBlob(it[DataTable.request])) }.firstOrNull() }
    }

    override fun saveRequest(certificationData: CertificationData): String {
        return databaseTransaction(database) {
            withFinalizables { finalizables ->
                val requestId = SecureHash.randomSHA256().toString()
                DataTable.insert {
                    it[DataTable.requestId] = requestId
                    it[hostName] = certificationData.hostName
                    it[ipAddress] = certificationData.ipAddr
                    it[DataTable.request] = serializeToBlob(certificationData.request, finalizables)
                    it[requestTimestamp] = LocalDateTime.now()
                }
                requestId
            }
        }
    }

    override fun pendingRequestIds(): List<String> {
        return databaseTransaction(database) { DataTable.select { DataTable.approvedTimestamp.isNull() }.map { it[DataTable.requestId] } }
    }
}