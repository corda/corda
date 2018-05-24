package net.corda.sandbox.rewiring

import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.ClassWriter.COMPUTE_FRAMES
import org.objectweb.asm.ClassWriter.COMPUTE_MAXS

/**
 * Class writer for sandbox execution, with configurable a [classLoader] to ensure correct deduction of the used class
 * hierarchy.
 *
 * @param classReader The [ClassReader] used to read the original class. It will be used to copy the entire constant
 * pool and bootstrap methods from the original class and also to copy other fragments of original byte code where
 * applicable.
 * @property classLoader The class loader used to load the classes that are to be rewritten.
 * @param flags Option flags that can be used to modify the default behaviour of this class. Must be zero or a
 * combination of [COMPUTE_MAXS] and [COMPUTE_FRAMES]. These option flags do not affect methods that are copied as is
 * in the new class. This means that neither the maximum stack size nor the stack frames will be computed for these
 * methods.
 */
open class SandboxClassWriter(
        classReader: ClassReader,
        private val classLoader: ClassLoader,
        flags: Int = COMPUTE_FRAMES or COMPUTE_MAXS
) : ClassWriter(classReader, flags) {

    /**
     * Get the common super type of [type1] and [type2].
     */
    override fun getCommonSuperClass(type1: String, type2: String): String {
        // Need to override [getCommonSuperClass] to ensure that the correct class loader is used.
        when {
            type1 == OBJECT_NAME -> return type1
            type2 == OBJECT_NAME -> return type2
        }
        val class1 = try {
            classLoader.loadClass(type1.replace('/', '.'))
        } catch (exception: Exception) {
            throw TypeNotPresentException(type1, exception)
        }
        val class2 = try {
            classLoader.loadClass(type2.replace('/', '.'))
        } catch (exception: Exception) {
            throw TypeNotPresentException(type2, exception)
        }
        return when {
            class1.isAssignableFrom(class2) -> type1
            class2.isAssignableFrom(class1) -> type2
            class1.isInterface || class2.isInterface -> OBJECT_NAME
            else -> {
                var clazz = class1
                do {
                    clazz = clazz.superclass
                } while (!clazz.isAssignableFrom(class2))
                clazz.name.replace('.', '/')
            }
        }
    }

    companion object {

        private const val OBJECT_NAME = "java/lang/Object"

    }

}
