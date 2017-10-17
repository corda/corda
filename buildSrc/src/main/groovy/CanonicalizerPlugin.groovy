import com.google.common.io.ByteStreams
import org.gradle.api.*
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import java.nio.file.Files
import java.nio.file.attribute.FileTime
import java.nio.file.Paths
import java.nio.file.StandardCopyOption

// Custom Gradle plugin that attempts to make the resulting jar file deterministic.
// Ie. same contract definition should result when compiled in same jar file.
// This is done by removing date time stamps from the files inside the jar.
class CanonicalizerPlugin implements Plugin<Project> {
    void apply(Project project) {

        project.getTasks().getByName('jar').doLast() {

            def zipPath = (String) project.jar.archivePath
            def destPath = Files.createTempFile("processzip", null)

            def zeroTime = FileTime.fromMillis(0)

            def input = new ZipFile(zipPath)
            def entries = input.entries().toList().sort { it.name }

            def output = new ZipOutputStream(new FileOutputStream(destPath.toFile()))
            output.setMethod(ZipOutputStream.DEFLATED)

            entries.each {
                def newEntry = new ZipEntry(it.name)

                newEntry.setLastModifiedTime(zeroTime)
                newEntry.setCreationTime(zeroTime)
                newEntry.compressedSize = -1
                newEntry.size = it.size
                newEntry.crc = it.crc

                output.putNextEntry(newEntry)

                ByteStreams.copy(input.getInputStream(it), output)

                output.closeEntry()
            }
            output.close()
            input.close()

            Files.move(destPath, Paths.get(zipPath), StandardCopyOption.REPLACE_EXISTING)
        }

    }
}
