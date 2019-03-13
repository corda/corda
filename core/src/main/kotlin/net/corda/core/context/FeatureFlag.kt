package net.corda.core.context

import net.corda.core.KeepForDJVM
import net.corda.core.internal.VisibleForTesting
import net.corda.core.utilities.loggerFor
import java.lang.UnsupportedOperationException
import kotlin.reflect.KProperty
import kotlin.reflect.KProperty0
import kotlin.reflect.jvm.isAccessible

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

    /**
     * Forces an exception if an unknown type is received during serialisation and the class carpenter is not enabled.
     *
     * If this flag is not set, an exception will only be thrown if the unknown type is required in the construction of an object; usually
     * it will be thrown away during evolution.
     */
    val DISABLE_CORDA_2707 by FeatureFlagValue()

    /**
     * Override the supplied feature flags with test values (visible only within the current thread), run the supplied action, then return
     * the supplied feature flags to their original values.
     *
     * This should never be used outside of testing for any reason.
     *
     * @param properties The feature flags to set, indicated by property references e.g. `FeatureFlag::DISABLE_CORDA_2707`.
     * @param action The action to run with the flags set.
     *
     * @return The value (if any) returned by the action.
     */
    @VisibleForTesting
    fun <T> withSet(vararg properties: Pair<KProperty0<Boolean>, Boolean>, action: () -> T): T {
        val delegates = properties.associate { (property, value) ->
            val delegate = property.apply { isAccessible = true }.getDelegate() as? FeatureFlagValue
                    ?: throw UnsupportedOperationException("$property is not a FeatureFlagValue")
            delegate to value
        }

        try {
            delegates.forEach { (delegate, value) -> delegate.testValue.set(value) }
            return action()
        } finally {
            delegates.forEach { (delegate, _) -> delegate.testValue.set(null) }
        }
    }

    private class FeatureFlagValue {
        // A value which can be set, thread-locally, to override the configured value. If it is null, then no test value is set.
        val testValue: ThreadLocal<Boolean> = ThreadLocal()

        operator fun getValue(target: FeatureFlag, property: KProperty<*>): Boolean =
                testValue.get() ?: configuration?.let { it[property] } ?: false
    }

    private val logger = loggerFor<FeatureFlag>()
    private var configuration: Map<KProperty0<Boolean>, Boolean>? = null

    /**
     * Called during process initialisation, when the configuration file is read. This should only be called once, and must never be called
     * by tests, which must use `withSet` to set/override feature-flag values.
     *
     * Note that this means that it is not possible to control the feature-flag configuration of in-process nodes during testing.
     *
     * @param value The "featureFlag" configuration to apply.
     */
    fun configure(vararg values: Pair<KProperty0<Boolean>, Boolean>) {
        val newConfiguration = mapOf(*values)
        if (newConfiguration != configuration) { logger.warn("Feature flags configured with $newConfiguration, overriding existing $configuration") }
        configuration = newConfiguration
    }
}