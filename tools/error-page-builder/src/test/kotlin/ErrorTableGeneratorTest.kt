import junit.framework.TestCase.assertEquals
import net.corda.errorPageBuilder.ErrorTableGenerator
import org.junit.Test
import java.io.File
import java.lang.IllegalArgumentException
import java.nio.file.Paths
import java.util.*

class ErrorTableGeneratorTest {

    companion object {
        private val RESOURCE_LOCATION = Paths.get("src/test/resources/test-errors").toAbsolutePath().toFile()
    }

    private val englishTable = """| Error Code | Aliases | Description | Actions to Fix |
        /| ---------- | ------- | ----------- | -------------- |
        /| test-error | foo, bar | Test description | Actions |
    """.trimMargin("/")

    private val irishTable = """| Cód Earráide | Ailiasanna | Cur síos | Caingne le Deisiú |
        /| ------------ | ---------- | -------- | ----------------- |
        /| test-error | foo, bar | Teachtaireacht tástála | Roinnt gníomhartha |
    """.trimMargin("/")

    @Test(timeout = 300_000)
    fun `check error table is produced as expected`() {
        val generator = ErrorTableGenerator(RESOURCE_LOCATION, Locale.forLanguageTag("en-US"))
        val table = generator.generateMarkdown()
        // Raw strings in Kotlin always use Unix line endings, so this is required to keep the test passing on Windows
        assertEquals(englishTable.split("\n").joinToString(System.lineSeparator()), table)
    }

    @Test(timeout = 300_000)
    fun `check table in other locales is produced as expected`() {
        val generator = ErrorTableGenerator(RESOURCE_LOCATION, Locale.forLanguageTag("ga-IE"))
        val table = generator.generateMarkdown()
        assertEquals(irishTable.split("\n").joinToString(System.lineSeparator()), table)
    }

    @Test(expected = IllegalArgumentException::class, timeout = 300_000)
    fun `error thrown if unknown directory passed to generator`() {
        val generator = ErrorTableGenerator(File("not/a/directory"), Locale.getDefault())
        generator.generateMarkdown()
    }
}