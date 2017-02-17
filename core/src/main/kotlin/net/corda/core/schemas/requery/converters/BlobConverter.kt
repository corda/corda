package net.corda.node.services.persistence

import io.requery.Converter
import java.sql.Blob
import javax.sql.rowset.serial.SerialBlob

class BlobConverter : Converter<ByteArray, Blob> {
    override fun convertToMapped(type: Class<out ByteArray>?, value: Blob?): ByteArray? {
        if(value == null)
            return null
        else return value.getBytes(1, value.length().toInt())
    }

    override fun getMappedType(): Class<ByteArray> {
        return ByteArray::class.java
    }

    override fun getPersistedSize(): Int? {
        return null
    }

    override fun convertToPersisted(value: ByteArray?): Blob? {
        if(value == null)
            return null
        else
            return SerialBlob(value)
    }

    override fun getPersistedType(): Class<Blob> {
        return Blob::class.java
    }


}