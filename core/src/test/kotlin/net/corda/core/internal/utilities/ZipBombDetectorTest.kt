package net.corda.core.internal.utilities

import net.corda.core.obfuscator.XorInputStream
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class ZipBombDetectorTest(private val case : TestCase) {

    enum class TestCase(
            val description : String,
            val zipResource : String,
            val maxUncompressedSize : Long,
            val maxCompressionRatio : Float,
            val expectedOutcome : Boolean
    ) {
        LEGIT_JAR("This project's jar file", "zip/core.jar", 128_000, 10f, false),

        // This is not detected as a zip bomb as ZipInputStream is unable to read all of its entries
        // (https://stackoverflow.com/questions/69286786/zipinputstream-cannot-parse-a-281-tb-zip-bomb),
        // so the total uncompressed size doesn't exceed maxUncompressedSize
        SMALL_BOMB(
                "A large (5.5 GB) zip archive",
                "zip/zbsm.zip.xor", 64_000_000, 10f, false),

        // Decreasing maxUncompressedSize leads to a successful detection
        SMALL_BOMB2(
                "A large (5.5 GB) zip archive, with 1MB maxUncompressedSize",
                "zip/zbsm.zip.xor", 1_000_000, 10f, true),

        // ZipInputStream is also unable to read all entries of zblg.zip, but since the first one is already bigger than 4GB,
        // that is enough to exceed maxUncompressedSize
        LARGE_BOMB(
            "A huge (281 TB) Zip bomb, this is the biggest possible non-recursive non-Zip64 archive",
            "zip/zblg.zip.xor", 64_000_000, 10f, true),

        //Same for this, but its entries are 22GB each
        EXTRA_LARGE_BOMB(
            "A humongous (4.5 PB) Zip64 bomb",
            "zip/zbxl.zip.xor", 64_000_000, 10f, true),

        //This is a jar file containing a single 10GB manifest
        BIG_MANIFEST(
                "A jar file with a huge manifest",
                "zip/big-manifest.jar.xor", 64_000_000, 10f, true);

        override fun toString() = description
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun generateTestCases(): Collection<*> {
            return TestCase.values().toList()
        }
    }

    @Test(timeout=10_000)
    fun test() {
        (javaClass.classLoader.getResourceAsStream(case.zipResource) ?:
            throw IllegalStateException("Missing test resource file ${case.zipResource}"))
                .buffered()
                .let(::XorInputStream)
                .let {
            Assert.assertEquals(case.expectedOutcome, ZipBombDetector.scanZip(it, case.maxUncompressedSize, case.maxCompressionRatio))
        }
    }
}