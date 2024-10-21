package net.corda.node.internal.security

import com.github.benmanes.caffeine.cache.Cache
import com.google.common.primitives.Ints
import net.corda.core.internal.NamedCacheFactory
import net.corda.core.internal.uncheckedCast
import net.corda.core.utilities.loggerFor
import net.corda.node.internal.DataSourceFactory
import net.corda.node.services.config.AuthDataSourceType
import net.corda.node.services.config.PasswordEncryption
import net.corda.node.services.config.SecurityConfiguration
import net.corda.nodeapi.internal.config.User
import org.apache.shiro.authc.AuthenticationException
import org.apache.shiro.authc.AuthenticationInfo
import org.apache.shiro.authc.AuthenticationToken
import org.apache.shiro.authc.SimpleAuthenticationInfo
import org.apache.shiro.authc.UsernamePasswordToken
import org.apache.shiro.authc.credential.PasswordMatcher
import org.apache.shiro.authc.credential.SimpleCredentialsMatcher
import org.apache.shiro.authz.AuthorizationInfo
import org.apache.shiro.authz.SimpleAuthorizationInfo
import org.apache.shiro.authz.permission.DomainPermission
import org.apache.shiro.cache.CacheManager
import org.apache.shiro.mgt.DefaultSecurityManager
import org.apache.shiro.realm.AuthorizingRealm
import org.apache.shiro.realm.jdbc.JdbcRealm
import org.apache.shiro.subject.PrincipalCollection
import org.apache.shiro.subject.SimplePrincipalCollection
import java.util.concurrent.ConcurrentHashMap
import javax.security.auth.login.FailedLoginException

private typealias AuthServiceConfig = SecurityConfiguration.AuthService

/**
 * Default implementation of [RPCSecurityManager] adapting
 * [org.apache.shiro.mgt.SecurityManager]
 */
class RPCSecurityManagerImpl(config: AuthServiceConfig, cacheFactory: NamedCacheFactory) : RPCSecurityManager {

    override val id = config.id
    private val manager: DefaultSecurityManager = buildImpl(config, cacheFactory)

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
        manager.realms?.filterIsInstance<AutoCloseable>()?.forEach(AutoCloseable::close)
        manager.destroy()
    }

    companion object {

        private val logger = loggerFor<RPCSecurityManagerImpl>()

        // Build internal Shiro securityManager instance
        private fun buildImpl(config: AuthServiceConfig, cacheFactory: NamedCacheFactory): DefaultSecurityManager {
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
                it.cacheManager = config.options?.cache?.let { options ->
                    CaffeineCacheManager(
                            timeToLiveSeconds = options.expireAfterSecs,
                            maxSize = options.maxEntries,
                            cacheFactory = cacheFactory
                    )
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
internal class RPCPermission : DomainPermission {

    /**
     * Helper constructor directly setting actions and target field
     *
     * @param methods Set of allowed RPC methods
     * @param target  An optional "target" type on which methods act
     */
    constructor(methods: Set<String>, target: String? = null) : super(methods, target?.let { setOf(it.replace(".", ":")) })


    /**
     * Default constructor instantiate an "ALL" permission
     */
    constructor() : super()
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
        users.forEach { user ->
            require(user.username.matches("\\w+".toRegex())) {
                "Username ${user.username} contains invalid characters"
            }
        }
        val resolvePermission = permissionResolver::resolvePermission
        authorizationInfoByUser = users.associate { user ->
            user.username to SimpleAuthorizationInfo().apply {
                objectPermissions = user.permissions.map(resolvePermission).toSet()
                roles = emptySet<String>()
                stringPermissions = emptySet<String>()
            }
        }
        authenticationInfoByUser = users.associate { user ->
            user.username to SimpleAuthenticationInfo().apply {
                credentials = user.password
                principals = SimplePrincipalCollection(user.username, realmId)
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

private class NodeJdbcRealm(config: SecurityConfiguration.AuthService.DataSource) : JdbcRealm(), AutoCloseable {

    init {
        credentialsMatcher = buildCredentialMatcher(config.passwordEncryption)
        setPermissionsLookupEnabled(true)
        dataSource = DataSourceFactory.createDataSource(config.connection!!)
        permissionResolver = RPCPermissionResolver
    }

    override fun close() {
        (dataSource as? AutoCloseable)?.close()
    }
}

private typealias ShiroCache<K, V> = org.apache.shiro.cache.Cache<K, V>

/*
 * Adapts a [com.github.benmanes.caffeine.cache.Cache] to a [org.apache.shiro.cache.Cache] implementation.
 */
private fun <K : Any, V : Any> Cache<K, V>.toShiroCache() = object : ShiroCache<K, V> {
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
                                   val timeToLiveSeconds: Long,
                                   val cacheFactory: NamedCacheFactory) : CacheManager {

    private val instances = ConcurrentHashMap<String, ShiroCache<*, *>>()

    override fun <K : Any, V : Any> getCache(name: String): ShiroCache<K, V> {
        val result = instances[name] ?: buildCache<K, V>(name)
        instances.putIfAbsent(name, result)
        return uncheckedCast(result)
    }

    private fun <K : Any, V : Any> buildCache(name: String): ShiroCache<K, V> {
        logger.info("Constructing cache '$name' with maximumSize=$maxSize, TTL=${timeToLiveSeconds}s")
        return cacheFactory.buildNamed<K, V>("RPCSecurityManagerShiroCache_$name").toShiroCache()
    }

    companion object {
        private val logger = loggerFor<CaffeineCacheManager>()
    }
}
