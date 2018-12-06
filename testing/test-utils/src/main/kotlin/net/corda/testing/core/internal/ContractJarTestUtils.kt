package net.corda.testing.core.internal

import net.corda.core.internal.cordapp.CORDAPP_CONTRACT_VERSION
import net.corda.testing.core.internal.JarSignatureTestUtils.addManifest
import net.corda.testing.core.internal.JarSignatureTestUtils.createJar
import net.corda.testing.core.internal.JarSignatureTestUtils.generateKey
import net.corda.testing.core.internal.JarSignatureTestUtils.signJar
import net.corda.core.internal.delete
import net.corda.core.internal.div
import net.corda.core.internal.toPath
import net.corda.testing.core.ALICE_NAME
import java.io.OutputStream
import java.net.URI
import java.net.URL
import java.nio.file.*
import java.security.PublicKey
import java.util.jar.Attributes
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import javax.tools.JavaFileObject
import javax.tools.SimpleJavaFileObject
import javax.tools.StandardLocation
import javax.tools.ToolProvider

object ContractJarTestUtils {

    @JvmOverloads
    fun makeTestJar(output: OutputStream, extraEntries: List<Pair<String, String>> = emptyList()) {
        output.use {
            val jar = JarOutputStream(it)
            jar.putNextEntry(JarEntry("test1.txt"))
            jar.write("This is some useful content".toByteArray())
            jar.closeEntry()
            jar.putNextEntry(JarEntry("test2.txt"))
            jar.write("Some more useful content".toByteArray())
            extraEntries.forEach {
                jar.putNextEntry(JarEntry(it.first))
                jar.write(it.second.toByteArray())
            }
            jar.closeEntry()
        }
    }

    @JvmOverloads
    fun makeTestSignedContractJar(workingDir: Path, contractName: String, version: Int = 1): Pair<Path, PublicKey> {
        val alias = "testAlias"
        val pwd = "testPassword"
        workingDir.generateKey(alias, pwd, ALICE_NAME.toString())
        val jarName = makeTestContractJar(workingDir, contractName, true, version)
        val signer = workingDir.signJar(jarName.toAbsolutePath().toString(), alias, pwd)
        (workingDir / "_shredder").delete()
        (workingDir / "_teststore").delete()
        return workingDir.resolve(jarName) to signer
    }

    @JvmOverloads
    fun makeTestContractJar(workingDir: Path, contractName: String, signed: Boolean = false, version: Int = 1): Path {
        val packages = contractName.split(".")
        val jarName = "attachment-${packages.last()}-$version-${(if (signed) "signed" else "")}.jar"
        val className = packages.last()
        createTestClass(workingDir, className, packages.subList(0, packages.size - 1))
        workingDir.createJar(jarName, "${contractName.replace(".", "/")}.class")
        workingDir.addManifest(jarName, Pair(Attributes.Name(CORDAPP_CONTRACT_VERSION), version.toString()))
        return workingDir.resolve(jarName)
    }

    private fun createTestClass(workingDir: Path, className: String, packages: List<String>): Path {
        val newClass = """package ${packages.joinToString(".")};
                import net.corda.core.contracts.*;
                import net.corda.core.transactions.*;

                public class $className implements Contract {
                    @Override
                    public void verify(LedgerTransaction tx) throws IllegalArgumentException {
                    }
                }
            """.trimIndent()
        val compiler = ToolProvider.getSystemJavaCompiler()
        val source = object : SimpleJavaFileObject(URI.create("string:///${packages.joinToString("/")}/$className.java"), JavaFileObject.Kind.SOURCE) {
            override fun getCharContent(ignoreEncodingErrors: Boolean): CharSequence {
                return newClass
            }
        }
        val fileManager = compiler.getStandardFileManager(null, null, null)
        fileManager.setLocation(StandardLocation.CLASS_OUTPUT, listOf(workingDir.toFile()))

        compiler.getTask(System.out.writer(), fileManager, null, null, null, listOf(source)).call()
        val outFile = fileManager.getFileForInput(StandardLocation.CLASS_OUTPUT, packages.joinToString("."), "$className.class")
        return Paths.get(outFile.name)
    }

    fun signContractJar(jarURL: URL, copyFirst: Boolean, keyStoreDir: Path? = null, alias: String = "testAlias", pwd: String  = "testPassword"): Pair<Path, PublicKey> {
        val urlAsPath = jarURL.toPath()
        val jarName =
            if (copyFirst) {
                val signedJarName = Paths.get(urlAsPath.toString().substringBeforeLast(".") + "-SIGNED.jar")
                Files.copy(urlAsPath, signedJarName, StandardCopyOption.REPLACE_EXISTING)
                signedJarName
            }
            else urlAsPath

        val workingDir =
            if (keyStoreDir == null) {
                val workingDir = jarName.parent
                workingDir.generateKey(alias, pwd, ALICE_NAME.toString())
                workingDir
            } else keyStoreDir

        val signer = workingDir.signJar(jarName.toAbsolutePath().toString(), alias, pwd)
        (workingDir / "_shredder").delete()
        (workingDir / "_teststore").delete()
        return workingDir.resolve(jarName) to signer
    }
}