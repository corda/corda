package net.corda.healthsurvey

import net.corda.core.utilities.toHexString
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest

object FileUtils {

    fun checksum(path: Path): String {
        val messageDigest = MessageDigest.getInstance("SHA1")
        val bytes = Files.readAllBytes(path)
        messageDigest.update(bytes, 0, bytes.size)
        val digestBytes = messageDigest.digest()
        return digestBytes.toHexString().toLowerCase()
    }

}