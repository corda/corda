package net.corda.core.schemas.requery.converters

import io.requery.Converter
import java.sql.Blob
import javax.sql.rowset.serial.SerialBlob

/**
 * Converts from a [ByteArray] to a [Blob].
 */
class BlobConverter : Converter<ByteArray, Blob> {

    override fun getMappedType(): Class<ByteArray> = ByteArray::class.java

    override fun getPersistedType(): Class<Blob> = Blob::class.java

    /**
     * creates BLOB(INT.MAX) = 2 GB
     */
    override fun getPersistedSize(): Int? = null

    override fun convertToPersisted(value: ByteArray?): Blob? {
        return value?.let { SerialBlob(value) }
    }

    override fun convertToMapped(type: Class<out ByteArray>?, value: Blob?): ByteArray? {
        return value?.getBytes(1, value.length().toInt())
    }
}