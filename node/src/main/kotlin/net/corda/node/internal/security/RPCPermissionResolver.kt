package net.corda.node.internal.security

import com.github.benmanes.caffeine.cache.CacheLoader
import com.github.benmanes.caffeine.cache.Caffeine
import com.github.benmanes.caffeine.cache.LoadingCache
import net.corda.core.internal.toMultiMap
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.messaging.RPCOps
import net.corda.node.internal.rpc.proxies.RpcAuthHelper.INTERFACE_SEPARATOR
import net.corda.node.internal.rpc.proxies.RpcAuthHelper.methodFullName
import org.apache.shiro.authz.Permission
import org.apache.shiro.authz.permission.PermissionResolver
import org.slf4j.LoggerFactory
import java.lang.reflect.Method
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KProperty
import kotlin.reflect.jvm.javaMethod

/*
 * A [org.apache.shiro.authz.permission.PermissionResolver] implementation for RPC permissions.
 * Provides a method to construct an [RPCPermission] instance from its string representation
 * in the form used by a Node admin.
 *
 * Currently valid permission strings have the forms:
 *
 *   - `ALL`: allowing all type of RPC calls
 *
 *   - `InvokeRpc.$RPCMethodName`: allowing to call a given RPC method without restrictions on its arguments.
 *
 *   - `StartFlow.$FlowClassName`: allowing to call a `startFlow*` RPC method targeting a Flow instance
 *     of a given class
 */
internal object RPCPermissionResolver : PermissionResolver {

    private val logger = LoggerFactory.getLogger(RPCPermissionResolver::class.java)

    private const val SEPARATOR = '.'
    private const val NEW_STYLE_SEP = ":"
    private const val ACTION_START_FLOW = "startflow"
    private const val ACTION_INVOKE_RPC = "invokerpc"
    private const val ACTION_ALL = "all"
    private val FLOW_RPC_CALLS = setOf(
            "startFlowDynamic",
            "startTrackedFlowDynamic",
            "startFlow",
            "startTrackedFlow")

    override fun resolvePermission(representation: String): Permission {
        when (representation.substringBefore(SEPARATOR).toLowerCase()) {
            ACTION_INVOKE_RPC -> {
                val rpcCall = representation.substringAfter(SEPARATOR, "")
                require(representation.count { it == SEPARATOR } == 1 && rpcCall.isNotEmpty()) {
                    "Malformed permission string"
                }
                val legacyPermitted = when (rpcCall) {
                    "startFlow" -> setOf("startFlowDynamic", rpcCall)
                    "startTrackedFlow" -> setOf("startTrackedFlowDynamic", rpcCall)
                    else -> setOf(rpcCall)
                }
                return RPCPermission(legacyPermitted.toFullyQualified())
            }
            ACTION_START_FLOW -> {
                val targetFlow = representation.substringAfter(SEPARATOR, "")
                require(targetFlow.isNotEmpty()) {
                    "Missing target flow after StartFlow"
                }
                return RPCPermission(FLOW_RPC_CALLS.toFullyQualified(), targetFlow)
            }
            ACTION_ALL -> {
                // Leaving empty set of targets and actions to match everything
                return RPCPermission()
            }
            else -> return attemptNewStyleParsing(representation)
        }
    }

    private fun Set<String>.toFullyQualified(): Set<String> {
        return map { methodFullName(CordaRPCOps::class.java, it) }.toSet()
    }

    /**
     * New style permissions representation:
     * 1. Fully qualified form: InvokeRpc:com.fully.qualified.package.CustomClientRpcOps#firstMethod
     * 2. All methods of the interface: InvokeRpc:com.fully.qualified.package.CustomClientRpcOps#ALL
     * 3. Methods of specific group: InvokeRpc:com.fully.qualified.package.CustomClientRpcOps#READONLY
     */
    private fun attemptNewStyleParsing(permAsString: String): Permission {
        return when(permAsString.substringBefore(NEW_STYLE_SEP).toLowerCase()) {
            ACTION_INVOKE_RPC -> {
                val interfaceAndMethods = permAsString.substringAfter(NEW_STYLE_SEP, "")
                val interfaceParts = interfaceAndMethods.split(INTERFACE_SEPARATOR)
                require(interfaceParts.size == 2) { "Malformed to comply with new style of InvokeRpc: $interfaceAndMethods" }
                val methodsMap = requireNotNull(cache.get(interfaceParts[0]))
                    { "Method map for ${interfaceParts[0]} must not be null in the cache. There must have been error processing interface. " +
                            "Please look at the error log lines above." }
                val lookupKey = interfaceAndMethods.toLowerCase()
                val methods = requireNotNull(methodsMap[lookupKey]) { "Cannot find record for " +
                        "'$lookupKey' for interface '${interfaceParts[0]}' in $methodsMap. " +
                        "Please check permissions configuration string '$permAsString' matching class representation." }
                RPCPermission(methods)
            }
            else -> throw IllegalArgumentException("Unable to parse permission as string: $permAsString")
        }
    }

    private val cache: LoadingCache<String, Map<String, Set<String>>> = Caffeine.newBuilder()
            .maximumSize(java.lang.Long.getLong("net.corda.node.internal.security.rpc.interface.cacheSize", 20))
            .build(InterfaceMethodMapCacheLoader())

    private class InterfaceMethodMapCacheLoader : CacheLoader<String, Map<String, Set<String>>> {
        override fun load(interfaceName: String): Map<String, Set<String>>? {
            return try {
                inspectInterface(interfaceName)
            } catch (ex: Exception) {
                logger.error("Unexpected error when populating cache for $interfaceName", ex)
                null
            }
        }
    }

    /**
     * Returns a map where key is either:
     *  - fully qualified interface method name;
     *  or
     *  - Wildcard string representing the group of methods like: ALL, READ_ONLY, etc.
     *  Value is always a set of fully qualified method names.
     */
    internal fun inspectInterface(interfaceName: String): Map<String, Set<String>> {
        val interfaceClass = Class.forName(interfaceName).kotlin
        require(interfaceClass.java.isInterface) { "Must be an interface: $interfaceClass"}
        require(RPCOps::class.java.isAssignableFrom(interfaceClass.java)) { "Must be assignable from RPCOps: $interfaceClass" }

        val membersPairs = interfaceClass.members.flatMap { member ->
            when(member) {
                is KFunction -> {
                    val method = member.javaMethod
                    if (method != null) {
                        processMethod(method, interfaceClass)
                    } else {
                        logger.info("KFunction $member does not have Java representation - ignoring")
                        emptyList()
                    }
                }
                is KProperty -> {
                    val method = member.getter.javaMethod
                    if (method != null) {
                        processMethod(method, interfaceClass)
                    } else {
                        logger.info("KProperty $member does not have Java representation - ignoring")
                        emptyList()
                    }
                }
                else -> {
                    logger.info("$member is an unhandled type of KCallable - ignoring")
                    emptyList()
                }
            }
        }
        // Pack the pairs into desired resulting data structure
        return membersPairs.toMultiMap().mapValues { it.value.toSet() }
    }

    private fun processMethod(method: Method, interfaceClass: KClass<out Any>): List<Pair<String, String>> {
        if(!RPCOps::class.java.isAssignableFrom(method.declaringClass)) {
            // To prevent going too deep to Object level
            return emptyList()
        }

        val allKey = methodFullName(interfaceClass.java, ACTION_ALL).toLowerCase()
        val methodFullName = methodFullName(method)
        return listOf(allKey to methodFullName) + // ALL group
        listOf(methodFullName.toLowerCase() to methodFullName) // Full method names individually
    }
}