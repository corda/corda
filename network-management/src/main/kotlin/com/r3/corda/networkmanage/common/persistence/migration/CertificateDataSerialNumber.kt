package com.r3.corda.networkmanage.common.persistence.migration

import com.r3.corda.networkmanage.common.utils.buildCertPath
import liquibase.change.custom.CustomTaskChange
import liquibase.database.Database
import liquibase.database.jvm.JdbcConnection
import liquibase.exception.ValidationErrors
import liquibase.resource.ResourceAccessor
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.math.BigDecimal
import java.security.Security
import java.security.cert.X509Certificate
import java.sql.ResultSet

class CertificateDataSerialNumber : CustomTaskChange {
    override fun validate(database: Database?): ValidationErrors? {
        return null
    }

    override fun setUp() {
        // Do nothing
    }

    override fun setFileOpener(resourceAccessor: ResourceAccessor?) {
        // Do nothing
    }

    override fun getConfirmationMessage(): String {
        return "Certificate data serial numbers have been extracted and persisted."
    }

    override fun execute(database: Database?) {
        Security.addProvider(BouncyCastleProvider())
        val jdbcConnection = database!!.connection as JdbcConnection
        jdbcConnection.autoCommit = true
        val statement = jdbcConnection.createStatement(ResultSet.TYPE_SCROLL_SENSITIVE, ResultSet.CONCUR_UPDATABLE)
        val resultSet = statement.executeQuery("SELECT certificate_path_bytes, certificate_serial_number FROM certificate_data")
        while (resultSet.next()) {
            val blob = resultSet.getBlob(1)
            val certPath = buildCertPath(blob.getBytes(1, blob.length().toInt()))
            blob.free()
            val serialNumber = (certPath.certificates.first() as X509Certificate).serialNumber
            resultSet.updateBigDecimal(2, BigDecimal(serialNumber))
            resultSet.updateRow()
        }
        statement.close()
    }

}