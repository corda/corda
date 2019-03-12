package net.corda.core.context

import net.corda.core.StubOutForDJVM
import net.corda.core.internal.VisibleForTesting
import java.lang.UnsupportedOperationException
import kotlin.reflect.KProperty
import kotlin.reflect.KProperty0
import kotlin.reflect.jvm.isAccessible

/**
 * Encapsulates [System.getenv] in such a way as to avoid falling foul of determinisation.
 */
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
object FeatureFlag  {

    val DISABLE_CORDA_2707: Boolean by FeatureFlagValue()

    // Nothing outside of testing should ever use this.
    @VisibleForTesting
    fun <T> withSet(property: KProperty0<Boolean>, action: () -> T): T {
        val delegate = property.apply { isAccessible = true }.getDelegate() as FeatureFlagValue
        val oldValue = property.get()
        try {
            delegate.isSet = true
            return action()
        } finally {
            delegate.isSet = oldValue
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