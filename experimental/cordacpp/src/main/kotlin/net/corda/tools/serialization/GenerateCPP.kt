package net.corda.tools.serialization

import net.corda.cliutils.CordaCliWrapper
import net.corda.cliutils.start
import net.corda.core.serialization.CordaSerializable
import net.corda.core.serialization.SerializationContext
import net.corda.core.serialization.internal.SerializationEnvironment
import net.corda.core.serialization.internal._contextSerializationEnv
import net.corda.core.serialization.serialize
import net.corda.core.utilities.OpaqueBytes
import net.corda.serialization.internal.AMQP_P2P_CONTEXT
import net.corda.serialization.internal.AMQP_STORAGE_CONTEXT
import net.corda.serialization.internal.CordaSerializationMagic
import net.corda.serialization.internal.SerializationFactoryImpl
import net.corda.serialization.internal.amqp.*
import net.corda.serialization.internal.model.LocalPropertyInformation
import net.corda.serialization.internal.model.LocalTypeInformation
import net.corda.serialization.internal.model.PropertyName
import org.apache.qpid.proton.amqp.Symbol
import picocli.CommandLine
import java.io.File
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.lang.reflect.TypeVariable
import java.lang.reflect.WildcardType
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.security.PublicKey
import java.time.Instant
import java.util.*
import kotlin.reflect.full.superclasses

fun main(args: Array<String>) {
    GenerateCPPHeaders().start(args)
}

@CordaSerializable
data class Employee(val names: Pair<String, String>)

@CordaSerializable
data class Department(val name: String, val employees: List<Employee>)

@CordaSerializable
data class Company(
        val name: String,
        val createdInYear: Short,
        val logo: OpaqueBytes,
        val departments: List<Department>,
        val historicalEvents: Map<String, Instant>
)

private fun makeTestData() {
    val e1 = Employee(Pair("Mike", "Hearn"))
    val e2 = Employee(Pair("Richard", "Brown"))
    val e3 = Employee(Pair("James", "Carlyle"))
    val d1 = Department("Platform", listOf(e1, e2, e3))
    val c = Company(
            "R3", 2014, OpaqueBytes.of(0),
            listOf(d1),
            mapOf(
                    "First lab project proposal email" to Instant.parse("2014-09-24T22:11:00.00Z"),
                    "Hired Mike" to Instant.parse("2015-11-03T12:00:00.00Z")
            )
    )
    File("/tmp/buf").writeBytes(c.serialize().bytes)
}

/**
 * Generates C++ source code that deserialises types.
 */
class GenerateCPPHeaders : CordaCliWrapper("generate-cpp-headers", "Generate source code for reading serialised messages in C++ languages") {
    @CommandLine.Parameters(index = "0", paramLabel = "OUTPUT", description = ["Path to where the output files are generated"], defaultValue = "out")
    lateinit var outputDirectory: String

    @CommandLine.Parameters(index = "1", arity = "1..*", paramLabel = "CLASS-NAME", description = ["A list of fully qualified Java class names to generate"])
    lateinit var classNames: List<String>

    private fun Type.mangleToCPPSyntax() = this.typeName.replace(".", "::").replace("?", "void *")

    override fun runProgram(): Int {
        val classes = classNames.map { Class.forName(it) }

        try {
            println("Initializing ...")
            initSerialization()
            //makeTestData()

            val outPath = Paths.get(outputDirectory)
            Files.createDirectories(outPath)

            val allHeaders = mutableListOf<String>()
            generateClassesFor(classes) { path, content ->
                val filePath = outPath.resolve(path)
                Files.createDirectories(filePath.parent)
                Files.write(filePath, content.toByteArray())
                println("Generated $filePath")
                allHeaders += path.toString()
            }

            // TODO: Topologically sort the dependencies. We can't get away with just using pre-declarations and pointer types.
            val uberHeader = listOf("#include \"corda-std-serializers.h\"") + allHeaders.map { "#include \"$it\"" }.sorted()
            val uberHeaderPath = outPath.resolve("all-messages.h")
            Files.write(uberHeaderPath, uberHeader)
            println("Generated $uberHeaderPath")
            return 0
        } catch (e: Exception) {
            e.printStackTrace()
            return 1
        }
    }

    private val Type.baseClass: Class<*> get() = ((this as? ParameterizedType)?.rawType as? Class<*> ?: this as Class<*>)
    private val Type.eraseGenericsToBounds: String get() = baseClass.toGenericString()

    private data class GenResult(val code: String?, val dependencies: Set<Type>, val needsSpecialisationFor: Symbol?)

    /**
     * Invokes the given callback with the file path and the file contents that should be created.
     */
    private fun generateClassesFor(roots: List<Type>, block: (Path, String) -> Unit) {
        // Generate a chunk of code for all the given types and all the resulting dependencies of those types.
        // This code is made complex by the desire to support generics. We will convert Java generic classes to
        // C++ templated classes, and use specialisation to allow the same class to have many concrete descriptors
        // at decode time.
        //
        // The factory lets us look up serializers, which gives us the fingerprints we will use to check the format
        // of the data we're decoding matches the generated code. This version of C++ support doesn't do evolution.
        val serializerFactory: SerializerFactory = Scheme.getSerializerFactory(AMQP_STORAGE_CONTEXT)
        // We don't want to generate the same code twice, so we keep track of the classes we already made here.
        // This set contains type names with erased type parameters in C++ form, e.g. kotlin::Pair<A, B>, not resolved
        // type parameters.
        val seenSoFar = mutableSetOf<String>()
        // The work queue keeps track of types we haven't visited yet.
        val workQ = LinkedList<Type>()
        workQ += roots
        // Map of base type/class name (without type params) to generated code.
        val classSources = HashMap<String, String>()
        // Map of base type/class name (without type params) to a list of pre-declarations of classes it depends on.
        //
        // We pre-declare classes rather than do the obvious thing of importing headers, because that approach led to
        // circular dependencies between headers due to the specializations.
        val classPredeclarations = HashMap<String, MutableSet<Type>>()
        // Map of base type/class name (without type params) to a set of specialisations for that class.
        //
        // We face a simple problem: we'd like to generate a single class with the obvious name for generic types
        // like Pair, but the concrete serialised structures have a descriptor symbol (e.g. "net.corda:AMNq64uhVP8WpSl2MBKq7A==")
        // that depends on the values of the type variables. That is, kotlin::Pair<Foo, Bar> has a different descriptor
        // to kotlin::Pair<Biz, Boz>. Therefore the generated code calls a function to find out what its descriptor
        // should be, and we provide more specific versions of this function outside the class body itself, at the end
        // of the code block. When specialising the class, the compiler will pick the specialised function to match
        // and the code will look for the right descriptor. We keep track of the specialisations we need here.
        val descriptorSpecializations = HashMap<String, MutableSet<String>>()
        while (workQ.isNotEmpty()) {
            val type = workQ.pop()
            if (type.eraseGenericsToBounds in seenSoFar)
                continue   // This can happen if we have cycles in the _type_ graph (not object graph).
            try {
                val baseName: String = type.baseClass.name
                val (code, dependencies, needsSpecialisationFor) = generateClassFor(type, serializerFactory, seenSoFar) ?: continue

                check(dependencies.none { it.baseClass.isArray }) {
                    dependencies
                }

                // We have to pre-declare any type that appears anywhere in any field type, including in nested generics.
                val predeclarationsNeeded: MutableSet<Type> = dependencies
                        .flatMap { it.allMentionedClasses }
                        .map { it.baseClass }
                        .toMutableSet()

                if (needsSpecialisationFor != null) {
                    val specialization = """template<> const std::string ${type.mangleToCPPSyntax()}::descriptor() { return "$needsSpecialisationFor"; }"""
                    descriptorSpecializations.getOrPut(baseName) { LinkedHashSet() } += specialization
                    predeclarationsNeeded += type.allMentionedClasses
                }

                // generateClassFor may not have actually generated any class code, if we already did so (it's already
                // in seenSoFar). We have to let generateClassFor run at least a little bit because even if we generated
                // the class already, this particular template instantiation of it might have changed the types of its
                // fields and that, in turn, may require us to generate more descriptor specializations. So we have to
                // explore the dependency graph taking into account resolved type variables, even though the final
                // emitted code is a template.
                if (code != null)
                    classSources[baseName] = code

                if (predeclarationsNeeded.isNotEmpty())
                    classPredeclarations.getOrPut(baseName) { LinkedHashSet() }.addAll(predeclarationsNeeded)

                workQ += dependencies
                seenSoFar += type.eraseGenericsToBounds
            } catch (e: AMQPNotSerializableException) {
                println("Warning: Skipping $type due to inability to process it: ${e.message}")
            }
        }

        for ((baseName, classSource) in classSources) {
            val path = Paths.get(baseName.replace('.', File.separatorChar).replace('$', '.') + ".h")
            val guardName = baseName.toUpperCase().replace('.', '_').replace('$', '_') + "_H"
            val predeclarations = classPredeclarations[baseName]?.let { formatPredeclarations(it, baseName) } ?: ""
            val specializations = descriptorSpecializations[baseName]?.joinToString(System.lineSeparator()) ?: ""
            block(path, """
                |////////////////////////////////////////////////////////////////////////////////////////////////////////
                |// Auto-generated code. Do not edit.
                |
                |#ifndef $guardName
                |#define $guardName
                |
                |#include "corda.h"
                |
                |// Pre-declarations to speed up processing and avoid circular header dependencies.
                |$predeclarations
                |// End of pre-declarations.
                |
                |$classSource
                |
                |// Template specializations of the descriptor() method.
                |$specializations
                |// End specializations.
                |
                |#endif
            """.trimMargin())
        }
    }

    private fun formatPredeclarations(predeclarations: MutableSet<Type>, baseName: String): String {
        val predeclaration = StringBuffer()
        // Group them by package to reduce the amount of repetitive namespace nesting we need.
        val groupedPredeclarations: Map<Package, List<Type>> = predeclarations.groupBy { it.baseClass.`package` }
        for ((pkg, types) in groupedPredeclarations) {
            // Don't need to pre-declare ourselves.
            if (types.singleOrNull()?.baseClass?.name == baseName) continue
            val (openings, closings) = namespaceBoilerplate("${pkg.name}.DummyClass".split('.'))
            predeclaration.appendln(openings)
            for (needed in types) {
                val params = needed.baseClass.typeParameters
                predeclaration.appendln("${templateHeader(params)}class ${needed.baseClass.simpleName};")
            }
            predeclaration.appendln(closings)
        }
        return predeclaration.toString()
    }

    private fun Type.inner(index: Int): Type {
        val pt = this as ParameterizedType
        val i = pt.actualTypeArguments[index]
        return if (i is WildcardType)
            i.upperBounds.first()
        else
            i
    }

    // Foo<Bar, Baz<Boz>> -> [Foo, Bar, Baz, Boz]
    private val Type.allMentionedClasses: Set<Class<*>>
        get() {
            val result = HashSet<Class<*>>()

            fun recurse(t: Type) {
                result += t.baseClass
                if (t !is ParameterizedType) return
                for (i in t.actualTypeArguments.indices) {
                    recurse(t.inner(i))
                }
            }

            recurse(this)
            return result
        }

    private fun generateClassFor(type: Type, serializerFactory: LocalSerializerFactory, seenSoFar: Set<String>): GenResult? {
        val fieldDeclarations = mutableListOf<String>()
        val fieldInitializations = mutableListOf<String>()
        val dependencies = mutableSetOf<Type>()

        // Get the serializer created by the serialization engine, and map it to C++.
        val typeInformation: LocalTypeInformation = serializerFactory.getTypeInformation(type)
        if (type == Instant::class.java || type == PublicKey::class.java || type == Any::class.java || type.baseClass.isInterface) {
            // Some serialisers are special and need to be hand coded, or can be ignored.
            return null
        }

        // descriptorSymbol is the string "net.corda:blahblah" that maps a section of data to a schema.
        val descriptorSymbol = serializerFactory.createDescriptor(typeInformation)

        val properties: Map<PropertyName, LocalPropertyInformation> = when (typeInformation) {
            is LocalTypeInformation.Abstract -> {
                // We can only discover nested classes at the moment for kotlin classes. This should be reworked to use a ClassGraph scanner
                // so we can navigate the hierarchy better but it'll do for now.
                val kclass = type.baseClass.kotlin
                if (kclass.isSealed)
                    dependencies += kclass.nestedClasses.filter { kclass in it.superclasses }.map { it.java }

                typeInformation.properties
            }
            is LocalTypeInformation.Composable -> typeInformation.properties
            else -> {
                println("Need to write code for custom serializer '$type' / $descriptorSymbol")
                return null
            }
        }

        // Calculate the body of the class where field are declared and initialised in the constructor.
        for ((javaName, propInfo) in properties) {
            val name = javaToCPPName(javaName)
            val genericReturnType = if (propInfo is LocalPropertyInformation.HasObservedGetter) propInfo.observedGetter.genericReturnType else null
            val (declType, newDeps) = convertType(propInfo.type.observedType, genericReturnType)
            dependencies += newDeps
            fieldDeclarations += "$declType $name;"
            val readTo = "net::corda::Parser::read_to(decoder, $name);"
            fieldInitializations += if (!propInfo.isMandatory)
                "if (decoder.next_type() != proton::NULL_TYPE) $readTo else decoder.next();"
            else
                readTo
        }

        // We have fully specified generics here, as used in the parameter types e.g. kotlin.Pair<Person, Person>
        // but we want to generate generic C++, so we have to put the type variables back
        val typeParameters = ((type as? ParameterizedType)?.rawType as? Class<*>)?.typeParameters ?: emptyArray()
        val isGeneric = typeParameters.isNotEmpty()

        // Bail out early without generating code if we already did this class. We had to scan it anyway to discover
        // if there were any new dependencies as a result of the template substitution.
        if (type.eraseGenericsToBounds in seenSoFar)
            return GenResult(null, dependencies, if (isGeneric) descriptorSymbol else null)

        // Calculate the right namespace{} blocks.
        val nameComponents = type.mangleToCPPSyntax().substringBefore('<').split("::")
        val (namespaceOpenings, namespaceClosings) = namespaceBoilerplate(nameComponents)
        val undecoratedName = nameComponents.last()

        val descriptorFunction = if (isGeneric) {
            "const std::string descriptor();"
        } else {
            "const std::string descriptor() { return \"$descriptorSymbol\"; }"
        }

        // Inheritance.
        val supers: List<LocalTypeInformation> = when (typeInformation) {
            is LocalTypeInformation.Composable -> typeInformation.interfaces + typeInformation.superclass
            is LocalTypeInformation.Abstract -> typeInformation.interfaces + typeInformation.superclass
            else -> emptyList()
        }.filterNot {
            it.observedType.baseClass.name.startsWith("java.lang.")
        }

        dependencies += supers.map { it.observedType }

        val inheritance = if (supers.isEmpty()) "" else {
            ": " + supers.joinToString(", ") { "public " + it.observedType.mangleToCPPSyntax() } + " "
        }

        return GenResult("""
                    |$namespaceOpenings
                    |
                    |${templateHeader(typeParameters)}class $undecoratedName $inheritance{
                    |public:
                    |    ${fieldDeclarations.joinToString(System.lineSeparator() + (" ".repeat(4)))}
                    |
                    |    explicit $undecoratedName(proton::codec::decoder &decoder) {
                    |        net::corda::CompositeTypeGuard guard(decoder, "$type", descriptor(), ${fieldDeclarations.size});
                    |        ${fieldInitializations.joinToString(System.lineSeparator() + (" ".repeat(8)))}
                    |    }
                    |
                    |    $descriptorFunction
                    |};
                    |
                    |$namespaceClosings
                """.trimMargin(), dependencies, if (isGeneric) descriptorSymbol else null)
    }

    // Java type to C++ type. See SerializerFactory.primitiveTypeNames and https://qpid.apache.org/releases/qpid-proton-0.26.0/proton/cpp/api/types_page.html
    private fun convertType(resolved: Type, genericReturnType: Type?): Pair<String, Set<Type>> {
        val dependencies = mutableSetOf<Type>()

        val cppType = when (resolved.typeName) {
            // Primitives.
            "char" -> "char"
            "boolean" -> "bool"
            "byte" -> "int8_t"
            "short" -> "int16_t"
            "int" -> "int32_t"
            "long" -> "int64_t"
            "float" -> "float"
            "double" -> "double"

            // Boxed types.
            "java.lang.Character" -> "char"
            "java.lang.Boolean" -> "bool"
            "java.lang.Byte" -> "int8_t"
            "java.lang.Short" -> "int16_t"
            "java.lang.Integer" -> "int32_t"
            "java.lang.Long" -> "int64_t"
            "java.lang.Float" -> "float"
            "java.lang.Double" -> "double"

            // AMQP specific types.
            "org.apache.qpid.proton.amqp.UnsignedByte" -> "uint8_t"
            "org.apache.qpid.proton.amqp.UnsignedShort" -> "uint16_t"
            "org.apache.qpid.proton.amqp.UnsignedInt" -> "uint32_t"
            "org.apache.qpid.proton.amqp.UnsignedLong" -> "uint64_t"
            "org.apache.qpid.proton.amqp.Decimal32" -> "proton::decimal32"
            "org.apache.qpid.proton.amqp.Decimal64" -> "proton::decimal64"
            "org.apache.qpid.proton.amqp.Decimal128" -> "proton::decimal128"
            "org.apache.qpid.proton.amqp.Symbol" -> "proton::symbol"

            // Utility types.
            "java.util.Date" -> "proton::timestamp"
            "java.util.UUID" -> "proton::uuid"
            "byte[]" -> "proton::binary"
            "java.lang.String" -> "std::string"

            // Classes, containers and other custom types.
            else -> when (resolved.baseClass.name) {
                "java.util.List" -> {
                    resolved as ParameterizedType
                    var innerType: Type = resolved.inner(0)
                    if (innerType is WildcardType) innerType = innerType.upperBounds.first()
                    val (innerName, innerDeps) = convertType(innerType, innerType)
                    dependencies += innerDeps
                    "std::list<$innerName>"
                }
                "java.util.Map" -> {
                    resolved as ParameterizedType
                    val keyType: Type = resolved.inner(0)
                    val valueType: Type = resolved.inner(1)
                    val (cppKeyType, extraDeps1) = convertType(keyType, keyType)
                    dependencies += extraDeps1
                    val (cppValueType, extraDeps2) = convertType(valueType, valueType)
                    dependencies += extraDeps2
                    "std::map<$cppKeyType, $cppValueType>"
                }
                else -> {
                    check(!resolved.baseClass.isArray) { "Unsupported array type: $resolved" }

                    // A plain old class OR a type variable.
                    dependencies += resolved
                    "net::corda::ptr<${genericReturnType!!.mangleToCPPSyntax()}>"
                }
            }
        }

        // println("${resolved.typeName} -> $cppType     $dependencies")

        return Pair(cppType, dependencies)
    }

    private fun javaToCPPName(javaName: String): String {
        val buf = StringBuffer()
        for (c in javaName) {
            if (c.isLowerCase()) {
                buf.append(c)
            } else {
                buf.append('_')
                buf.append(c.toLowerCase())
            }
        }
        return buf.toString()
    }

    private fun templateHeader(typeParameters: Array<out TypeVariable<out Class<out Any>>>) =
            if (typeParameters.isEmpty()) "" else "template <" + typeParameters.joinToString { "class $it" } + "> "

    private fun namespaceBoilerplate(nameComponents: List<String>): Pair<String, String> {
        val namespaceComponents: List<String> = nameComponents.dropLast(1)
        val namespaceOpenings = namespaceComponents.joinToString(System.lineSeparator()) { "namespace $it {" }
        val namespaceClosings = Array(namespaceComponents.size) { "}" }.joinToString(System.lineSeparator())
        return Pair(namespaceOpenings, namespaceClosings)
    }

    private fun initSerialization() {
        val factory = SerializationFactoryImpl()
        factory.registerScheme(Scheme)
        _contextSerializationEnv.set(SerializationEnvironment.with(
                factory,
                p2pContext = AMQP_P2P_CONTEXT.withLenientCarpenter(),
                storageContext = AMQP_STORAGE_CONTEXT.withLenientCarpenter()
        ))
        // Hack: Just force the serialization engine to fully initialize by serializing something.
        Scheme.serialize(Instant.now(), factory.defaultContext)
    }

    private object Scheme : AbstractAMQPSerializationScheme(emptyList()) {
        override fun canDeserializeVersion(magic: CordaSerializationMagic, target: SerializationContext.UseCase): Boolean {
            return magic == amqpMagic
        }

        override fun rpcClientSerializerFactory(context: SerializationContext) = throw UnsupportedOperationException()
        override fun rpcServerSerializerFactory(context: SerializationContext) = throw UnsupportedOperationException()
    }
}