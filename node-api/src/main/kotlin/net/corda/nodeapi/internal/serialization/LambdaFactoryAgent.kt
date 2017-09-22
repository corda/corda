package net.corda.nodeapi.internal.serialization

import jdk.internal.org.objectweb.asm.ClassReader
import jdk.internal.org.objectweb.asm.ClassVisitor
import jdk.internal.org.objectweb.asm.ClassWriter
import jdk.internal.org.objectweb.asm.MethodVisitor
import jdk.internal.org.objectweb.asm.Opcodes
import java.lang.instrument.ClassFileTransformer
import java.lang.instrument.IllegalClassFormatException
import java.lang.instrument.Instrumentation
import java.security.ProtectionDomain

object LambdaFactoryAgent {

    private val innerClassLambdaMetafactoryClassName = "java.lang.invoke.InnerClassLambdaMetafactory"
    private val innerClassLambdaMetafactoryClassNameSlashes = "java/lang/invoke/InnerClassLambdaMetafactory"

    fun agentmain(agentArgs: String, inst: Instrumentation) {
        premain(agentArgs, inst)
    }

    fun premain(agentArgs: String, inst: Instrumentation) {

        inst.addTransformer(InnerClassLambdaMetafactoryTransformer(), true)
        try {
            inst.retransformClasses(Class.forName(innerClassLambdaMetafactoryClassName))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private class InnerClassLambdaMetafactoryTransformer : ClassFileTransformer {

        @Throws(IllegalClassFormatException::class)
        override fun transform(loader: ClassLoader, className: String, classBeingRedefined: Class<*>, protectionDomain: ProtectionDomain, classFileBuffer: ByteArray): ByteArray? {

            return if (className == innerClassLambdaMetafactoryClassNameSlashes) {
                val cr = ClassReader(classFileBuffer)
                val cw = ClassWriter(cr, 0)
                cr.accept(object : ClassVisitor(Opcodes.ASM5, cw) {

                    override fun visitMethod(access: Int, name: String, desc: String, signature: String, exceptions: Array<String>): MethodVisitor {
                        // only modify the (only) constructor
                        return if ("<init>" == name) {
                            object : MethodVisitor(Opcodes.ASM5, super.visitMethod(access, name, desc, signature, exceptions)) {

                                override fun visitCode() {

                                    super.visitCode()
                                    // set the isSerializable-parameter to true
                                    mv.visitInsn(Opcodes.ICONST_1)
                                    mv.visitVarInsn(Opcodes.ISTORE, 7)
                                }
                            }
                        } else super.visitMethod(access, name, desc, signature, exceptions)
                    }
                }, 0)
                cw.toByteArray()
            } else null
        }
    }
}