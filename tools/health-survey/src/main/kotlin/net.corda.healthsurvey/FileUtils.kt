package net.corda.healthsurvey

import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import javax.xml.bind.DatatypeConverter

object FileUtils {

    fun checksum(path: Path): String {
        val messageDigest = MessageDigest.getInstance("SHA1")
        val bytes = Files.readAllBytes(path)
        messageDigest.update(bytes, 0, bytes.size)
        val digestBytes = messageDigest.digest()
        return DatatypeConverter.printHexBinary(digestBytes).toLowerCase()
    }

}