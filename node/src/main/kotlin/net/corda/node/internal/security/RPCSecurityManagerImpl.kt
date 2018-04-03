package net.corda.node.internal.security


import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import com.google.common.primitives.Ints
import net.corda.core.context.AuthServiceId
import net.corda.core.utilities.loggerFor
import net.corda.node.internal.DataSourceFactory
import net.corda.node.services.config.PasswordEncryption
import net.corda.node.services.config.SecurityConfiguration
import net.corda.node.services.config.AuthDataSourceType
import net.corda.nodeapi.internal.config.User
import org.apache.shiro.authc.*
import org.apache.shiro.authc.credential.PasswordMatcher
import org.apache.shiro.authc.credential.SimpleCredentialsMatcher
import org.apache.shiro.authz.AuthorizationInfo
import org.apache.shiro.authz.Permission
import org.apache.shiro.authz.SimpleAuthorizationInfo
import org.apache.shiro.authz.permission.DomainPermission
import org.apache.shiro.authz.permission.PermissionResolver
import org.apache.shiro.cache.CacheManager
import org.apache.shiro.mgt.DefaultSecurityManager
import org.apache.shiro.realm.AuthorizingRealm
import org.apache.shiro.realm.jdbc.JdbcRealm
import org.apache.shiro.subject.PrincipalCollection
import org.apache.shiro.subject.SimplePrincipalCollection
import java.io.Closeable
import javax.security.auth.login.FailedLoginException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
private typealias AuthServiceConfig = SecurityConfiguration.AuthService

/**
 * Default implementation of [RPCSecurityManager] adapting
 * [org.apache.shiro.mgt.SecurityManager]
 */
class RPCSecurityManagerImpl(config: AuthServiceConfig) : RPCSecurityManager {

    override val id = config.id
    private val manager: DefaultSecurityManager

    init {
        manager = buildImpl(config)
    }

    @Throws(FailedLoginException::class)
    override fun authenticate(principal: String, password: Password): AuthorizingSubject {
        password.use {
            val authToken = UsernamePasswordToken(principal, it.value)
            try {
                manager.authenticate(authToken)
            } catch (authcException: AuthenticationException) {
                throw FailedLoginException(authcException.toString())
            }
            return ShiroAuthorizingSubject(
                    subjectId = SimplePrincipalCollection(principal, id.value),
                    manager = manager)
        }
    }

    override fun buildSubject(principal: String): AuthorizingSubject =
            ShiroAuthorizingSubject(
                    subjectId = SimplePrincipalCollection(principal, id.value),
                    manager = manager)

    override fun close() {
        manager.realms?.filterIsInstance<Closeable>()?.forEach { it.close() }
        manager.destroy()
    }

    companion object {

        private val logger = loggerFor<RPCSecurityManagerImpl>()

        /**
         * Instantiate RPCSecurityManager initialised with users data from a list of [User]
         */
        fun fromUserList(id: AuthServiceId, users: List<User>) =
                RPCSecurityManagerImpl(
                    AuthServiceConfig.fromUsers(users).copy(id = id))

        // Build internal Shiro securityManager instance
        private fun buildImpl(config: AuthServiceConfig): DefaultSecurityManager {
            val realm = when (config.dataSource.type) {
                AuthDataSourceType.DB -> {
                    logger.info("Constructing DB-backed security data source: ${config.dataSource.connection}")
                    NodeJdbcRealm(config.dataSource)
                }
                AuthDataSourceType.INMEMORY -> {
                    logger.info("Constructing realm from list of users in config ${config.dataSource.users!!}")
                    InMemoryRealm(config.dataSource.users, config.id.value, config.dataSource.passwordEncryption)
                }
            }
            return DefaultSecurityManager(realm).also {
                // Setup optional cache layer if configured
                it.cacheManager = config.options?.cache?.let {
                    CaffeineCacheManager(
                            timeToLiveSeconds = it.expireAfterSecs,
                            maxSize = it.maxEntries)
                }
            }
        }
    }
}

/*
 * Provide a representation of RPC permissions based on Apache Shiro permissions framework.
 * A permission represents a set of actions: for example, the set of all RPC invocations, or the set
 * of RPC invocations acting on a given class of Flows in input. A permission `implies` another one if
 * its set of actions contains the set of actions in the other one. In Apache Shiro, permissions are
 * represented by instances of the [Permission] interface which offers a single method: [implies], to
 * test if the 'x implies y' binary predicate is satisfied.
 */
private class RPCPermission : DomainPermission {

    /**
     * Helper constructor directly setting actions and target field
     *
     * @param methods Set of allowed RPC methods
     * @param target  An optional "target" type on which methods act
     */
    constructor(methods: Set<String>, target: String? = null) : super(methods, target?.let { setOf(it) })


    /**
     * Default constructor instantiate an "ALL" permission
     */
    constructor() : super()
}

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
private object RPCPermissionResolver : PermissionResolver {

    private val SEPARATOR = '.'
    private val ACTION_START_FLOW = "startflow"
    private val ACTION_INVOKE_RPC = "invokerpc"
    private val ACTION_ALL = "all"
    private val FLOW_RPC_CALLS = setOf(
            "startFlowDynamic",
            "startTrackedFlowDynamic",
            "startFlow",
            "startTrackedFlow")

    override fun resolvePermission(representation: String): Permission {
    	val action = representation.substringBefore(SEPARATOR).toLowerCase()
        when (action) {
            ACTION_INVOKE_RPC -> {
                val rpcCall = representation.substringAfter(SEPARATOR, "")
                require(representation.count { it == SEPARATOR } == 1 && !rpcCall.isEmpty()) {
                    "Malformed permission string"
                }
                val permitted = when(rpcCall) {
                    "startFlow" -> setOf("startFlowDynamic", rpcCall)
                    "startTrackedFlow" -> setOf("startTrackedFlowDynamic", rpcCall)
                    else -> setOf(rpcCall)
                }
                return RPCPermission(permitted)
            }
            ACTION_START_FLOW -> {
                val targetFlow = representation.substringAfter(SEPARATOR, "")
                require(targetFlow.isNotEmpty()) {
                    "Missing target flow after StartFlow"
                }
                return RPCPermission(FLOW_RPC_CALLS, targetFlow)
            }
            ACTION_ALL -> {
                // Leaving empty set of targets and actions to match everything
                return RPCPermission()
            }
            else -> throw IllegalArgumentException("Unknown permission action specifier: $action")
        }
    }
}

class ShiroAuthorizingSubject(
        private val subjectId: PrincipalCollection,
        private val manager: DefaultSecurityManager) : AuthorizingSubject {

    override val principal get() = subjectId.primaryPrincipal.toString()

    override fun isPermitted(action: String, vararg arguments: String) =
            manager.isPermitted(subjectId, RPCPermission(setOf(action), arguments.firstOrNull()))
}

private fun buildCredentialMatcher(type: PasswordEncryption) = when (type) {
    PasswordEncryption.NONE -> SimpleCredentialsMatcher()
    PasswordEncryption.SHIRO_1_CRYPT -> PasswordMatcher()
}

class InMemoryRealm(users: List<User>,
                            realmId: String,
                            passwordEncryption: PasswordEncryption = PasswordEncryption.NONE) : AuthorizingRealm() {

    private val authorizationInfoByUser: Map<String, AuthorizationInfo>
    private val authenticationInfoByUser: Map<String, AuthenticationInfo>

    init {
        permissionResolver = RPCPermissionResolver
        users.forEach {
            require(it.username.matches("\\w+".toRegex())) {
                "Username ${it.username} contains invalid characters"
            }
        }
        val resolvePermission = { s: String -> permissionResolver.resolvePermission(s) }
        authorizationInfoByUser = users.associate {
            it.username to SimpleAuthorizationInfo().apply {
                objectPermissions = it.permissions.map { resolvePermission(it) }.toSet()
                roles = emptySet<String>()
                stringPermissions = emptySet<String>()
            }
        }
        authenticationInfoByUser = users.associate {
            it.username to SimpleAuthenticationInfo().apply {
                credentials = it.password
                principals = SimplePrincipalCollection(it.username, realmId)
            }
        }
        credentialsMatcher = buildCredentialMatcher(passwordEncryption)
    }

    // Methods from AuthorizingRealm interface used by Shiro to query
    // for authentication/authorization data for a given user
    override fun doGetAuthenticationInfo(token: AuthenticationToken) =
            authenticationInfoByUser[token.principal as String]

    override fun doGetAuthorizationInfo(principals: PrincipalCollection) =
            authorizationInfoByUser[principals.primaryPrincipal as String]
}

private class NodeJdbcRealm(config: SecurityConfiguration.AuthService.DataSource) : JdbcRealm(), Closeable {

    init {
        credentialsMatcher = buildCredentialMatcher(config.passwordEncryption)
        setPermissionsLookupEnabled(true)
        dataSource = DataSourceFactory.createDataSource(config.connection!!)
        permissionResolver = RPCPermissionResolver
    }

    override fun close() {
        (dataSource as? Closeable)?.close()
    }
}

private typealias ShiroCache<K, V> = org.apache.shiro.cache.Cache<K, V>

/*
 * Adapts a [com.github.benmanes.caffeine.cache.Cache] to a [org.apache.shiro.cache.Cache] implementation.
 */
private fun <K : Any, V> Cache<K, V>.toShiroCache(name: String) = object : ShiroCache<K, V> {

    val name = name
    private val impl = this@toShiroCache

    override operator fun get(key: K) = impl.getIfPresent(key)

    override fun put(key: K, value: V): V? {
        val lastValue = get(key)
        impl.put(key, value)
        return lastValue
    }

    override fun remove(key: K): V? {
        val lastValue = get(key)
        impl.invalidate(key)
        return lastValue
    }

    override fun clear() {
        impl.invalidateAll()
    }

    override fun size() = Ints.checkedCast(impl.estimatedSize())
    override fun keys() = impl.asMap().keys
    override fun values() = impl.asMap().values
    override fun toString() = "Guava cache adapter [$impl]"
}

/*
 * Implementation of [org.apache.shiro.cache.CacheManager] based on
 * cache implementation in [com.github.benmanes.caffeine.cache.Cache]
 */
private class CaffeineCacheManager(val maxSize: Long,
                                   val timeToLiveSeconds: Long) : CacheManager {

    private val instances = ConcurrentHashMap<String, ShiroCache<*, *>>()

    override fun <K : Any, V> getCache(name: String): ShiroCache<K, V> {
        val result = instances[name] ?: buildCache<K, V>(name)
        instances.putIfAbsent(name, result)
        return result as ShiroCache<K, V>
    }

    private fun <K : Any, V> buildCache(name: String): ShiroCache<K, V> {
        logger.info("Constructing cache '$name' with maximumSize=$maxSize, TTL=${timeToLiveSeconds}s")
        return Caffeine.newBuilder()
                .expireAfterWrite(timeToLiveSeconds, TimeUnit.SECONDS)
                .maximumSize(maxSize)
                .build<K, V>()
                .toShiroCache(name)
    }

    companion object {
        private val logger = loggerFor<CaffeineCacheManager>()
    }
}