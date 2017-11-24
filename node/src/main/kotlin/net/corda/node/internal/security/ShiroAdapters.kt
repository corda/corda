package net.corda.node.internal.security

import org.apache.shiro.authz.permission.DomainPermission
import org.apache.shiro.subject.Subject
import net.corda.nodeapi.User
import org.apache.shiro.authc.AuthenticationInfo
import org.apache.shiro.authc.AuthenticationToken
import org.apache.shiro.authc.SimpleAuthenticationInfo
import org.apache.shiro.authc.credential.SimpleCredentialsMatcher
import org.apache.shiro.authz.AuthorizationInfo
import org.apache.shiro.authz.Permission
import org.apache.shiro.authz.SimpleAuthorizationInfo
import org.apache.shiro.authz.permission.PermissionResolver
import org.apache.shiro.realm.AuthorizingRealm
import org.apache.shiro.subject.PrincipalCollection
import org.apache.shiro.subject.SimplePrincipalCollection

/**
 * Provide a representation of RPC permissions based on Apache Shiro permissions framework.
 * A permission represents a set of actions: for example, the set of all RPC invocations, or the set
 * of RPC invocations acting on a given class of Flows in input. A permission `implies` another one if
 * its set of actions contains the set of actions in the other one. In Apache Shiro, permissions are
 * represented by instances of the [Permission] interface which offers a single method: [implies], to
 * test if the 'x implies y' binary predicate is satisfied.
 */
class RPCPermission : DomainPermission {

    /**
     * Helper constructor directly setting actions and target field
     *
     * @param methods Set of allowed RPC methods
     * @param target  An optional "target" type on which methods act
     */
    constructor(methods: Set<String>, target: String? = null)
        : super(methods, target?.let { setOf(it) })


    /**
     * Default constructur instantiate an "ALL" permission
     */
    constructor() : super()
}

/**
 * Implementation of [org.apache.shiro.authz.permission.PermissionResolver] for RPC permissions.
 * Provides a method to construct an [RPCPermission] instance from its string represenatation
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
 *
 */
object RPCPermissionResolver : PermissionResolver {

    /**
     * Construct [RPCPermission] from string representation
     *
     * @param representation
     */
    override fun resolvePermission(representation: String): Permission {
        /*
         * Parse action and targets from string representation
         */
        val action = representation.substringBefore(SEPARATOR).toLowerCase()
        when(action) {
            ACTION_INVOKE_RPC -> {
                /*
                 * Permission to call a given RPC on any input
                 */
                val rpcCall = representation.substringAfter(SEPARATOR)
                require(representation.count { it == SEPARATOR } == 1) {
                    "Malformed permission string" }
                return RPCPermission(setOf(rpcCall))
            }
            ACTION_START_FLOW -> {
                /*
                 * Permission to start a given Flow via RPC
                 */
                val targetFlow = representation.substringAfter(SEPARATOR)
                require(targetFlow.isNotEmpty()) {
                    "Missing target flow after StartFlow" }
                return RPCPermission(FLOW_RPC_CALLS, targetFlow)
            }
            ACTION_ALL -> {
                // Leaving empty set of targets and actions to match everything
                return RPCPermission()
            }
            else -> throw IllegalArgumentException("Unkwnow permission action specifier: $action")
        }
    }

    /*
     * Collection of static factory functions and private string constants
     */
    private val SEPARATOR = '.'
    private val ACTION_START_FLOW = "startflow"
    private val ACTION_INVOKE_RPC = "invokerpc"
    private val ACTION_ALL = "all"

    /*
     * List of RPC calls granted by a StartFlow permission
     */
    private val FLOW_RPC_CALLS = setOf(
            "startFlowDynamic",
            "startTrackedFlowDynamic")
}

/**
 * An implementation of the [net.corda.node.AuthorizingSubject] adapting
 * [org.apacge.shiro.subject.Subject]
 */
class ShiroAuthorizingSubject (private val impl : Subject) : AuthorizingSubject {

    override val principal: String
        get() = impl.principals.primaryPrincipal as String

    override fun isPermitted(action: String, vararg arguments: String) : Boolean {
        if (arguments.isEmpty()) {
            return impl.isPermitted(RPCPermission(setOf(action)))
        }
        else {
            val target = arguments.first()
            return impl.isPermitted(RPCPermission(setOf(action), target))
        }
    }
}

/**
 * An implementation of AuthorizingRealm serving data
 * from an input list of User
 */
class InMemoryRealm : AuthorizingRealm {

    constructor(users: List<User>,
                realmId : String,
                resolver: PermissionResolver = RPCPermissionResolver) {
        permissionResolver = resolver
        val resolvePermission = {s : String -> permissionResolver.resolvePermission(s) }
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
        // Plain credential matcher
        credentialsMatcher = SimpleCredentialsMatcher()
    }

    /*
     * Methods from AuthorizingRealm interface used by Shiro to query
     * for authentication/authorization data for a given user
     */
    override fun doGetAuthenticationInfo(token: AuthenticationToken) =
            authenticationInfoByUser.getValue(token.principal as String)

    override fun doGetAuthorizationInfo(principals: PrincipalCollection) =
            authorizationInfoByUser.getValue(principals.primaryPrincipal as String)

    private val authorizationInfoByUser: Map<String, AuthorizationInfo>
    private val authenticationInfoByUser: Map<String, AuthenticationInfo>
}


