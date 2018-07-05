package net.corda.djvm.rewiring

/**
 * A class or interface running in a Java application, together with its raw byte code representation and all references
 * made from within the type.
 *
 * @property type The class/interface representation.
 * @property byteCode The raw byte code forming the class/interface.
 */
class LoadedClass(
        val type: Class<*>,
        val byteCode: ByteCode
) {

    /**
     * The name of the loaded type.
     */
    val name: String
        get() = type.name.replace('.', '/')

    override fun toString(): String {
        return "Class(type=$name, size=${byteCode.bytes.size}, isModified=${byteCode.isModified})"
    }

}
