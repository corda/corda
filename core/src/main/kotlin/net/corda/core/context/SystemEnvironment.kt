package net.corda.core.context

import net.corda.core.KeepForDJVM
import net.corda.core.StubOutForDJVM
import net.corda.core.internal.VisibleForTesting
import java.lang.UnsupportedOperationException
import kotlin.reflect.KProperty
import kotlin.reflect.KProperty0
import kotlin.reflect.jvm.isAccessible

/**
 * Encapsulates [System.getenv] in such a way as to avoid falling foul of determinisation.
 */
@KeepForDJVM
object SystemEnvironment : (String) -> String? {
    override fun invoke(variableName: String) =
        try {
            getFromEnv(variableName)
        } catch (e: UnsupportedOperationException) {
            // We are in the deterministic sandbox, where no flags can be set.
            null
        }

    @StubOutForDJVM
    fun getFromEnv(variableName: String): String? = System.getenv(variableName)
}

/**
 * Encapsulates reading a feature flag from the system environment. The environment variable name is automagically generated from the name
 * of the feature-flag constant by substituting hyphens for underscores, e.g. `DISABLE_CORDA_2707' becomes `DISABLE-CORDA-2707`.
 *
 * The result of reading the variable is cached, so it should be safe to read a feature flag on a hot path.
 *
 * A feature flag can be temporarily set for testing using the withSet method.
 */
@KeepForDJVM
object FeatureFlag  {

    val DISABLE_CORDA_2707 by FeatureFlagValue()

    /**
     * Set the supplied feature flags to "true", run the supplied action, then return the supplied feature flags to their original values.
     *
     * This should never be used outside of testing for any reason.
     *
     * @param properties The feature flags to set, indicated by property references e.g. `FeatureFlag::DISABLE_CORDA_2707`.
     * @param action The action to run with the flags set.
     *
     * @return The value (if any) returned by the action.
     */
    @VisibleForTesting
    fun <T> withSet(vararg properties: KProperty0<Boolean>, action: () -> T): T {
        val delegates = properties.map { property ->
            property.apply { isAccessible = true }.getDelegate() as? FeatureFlagValue
                    ?: throw UnsupportedOperationException("$property is not a FeatureFlagValue")
        }

        val oldValues = properties.map(KProperty0<Boolean>::get)
        try {
            delegates.forEach { it.isSet = true }
            return action()
        } finally {
            delegates.asSequence().zip(oldValues.asSequence()).forEach { (delegate, oldValue) ->
                delegate.isSet = oldValue
            }
        }
    }

    private class FeatureFlagValue {
        var alreadyRead: Boolean = false
        var isSet: Boolean = false

        operator fun getValue(target: FeatureFlag, property: KProperty<*>): Boolean =
                if (alreadyRead) isSet else {
                    isSet = SystemEnvironment(property.name.replace("_", "-"))?.toBoolean() ?: false
                    alreadyRead = true
                    isSet
                }
    }
}