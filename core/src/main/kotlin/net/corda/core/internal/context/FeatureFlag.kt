package net.corda.core.internal.context

import net.corda.core.KeepForDJVM
import net.corda.core.StubOutForDJVM
import net.corda.core.internal.VisibleForTesting
import net.corda.core.utilities.loggerFor
import java.lang.UnsupportedOperationException
import kotlin.reflect.KProperty
import kotlin.reflect.KProperty0
import kotlin.reflect.jvm.isAccessible

/**
 * Encapsulates reading a feature flag from the node configuration.
 *
 * A feature flag can be temporarily set for testing using the withSet method.
 */
@KeepForDJVM
object FeatureFlag  {

    /**
     * This feature flag forces an exception if an unknown type is received during serialisation and the class carpenter is not enabled.
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
        // Obtain the underlying [FeatureFlagValue] delegates of the provided properties, so we can temporarily set their values.
        val delegates = properties.associate { (property, value) ->
            val delegate = property.apply { isAccessible = true }.getDelegate() as? FeatureFlagValue
                    ?: throw UnsupportedOperationException("$property is not a FeatureFlagValue")
            delegate to value
        }

        // Set the values of each of the delegates, and run the provided action.
        try {
            delegates.forEach { (delegate, value) -> delegate.testValue = value }
            return action()
        } finally {
            // Return each delegate to its original value, by unsetting the test value.
            delegates.forEach { (delegate, _) -> delegate.testValue = null }
        }
    }

    /**
     * A delegate which applies the same resolution path to every feature flag:
     *
     * 1) See if there is a thread-local test value set; otherwise
     * 2) See if there is a value set in the configuration map loaded during node start-up; otherwise
     * 3) return `false`.
     */
    private class FeatureFlagValue {
        // A value which can be set, thread-locally, to override the configured value. If it is null, then no test value is set.
        var testValue by threadLocalIfPossible()

        operator fun getValue(target: FeatureFlag, property: KProperty<*>): Boolean =
            testValue ?: configuration?.let { it[property] } ?: false
    }

    private val logger = loggerFor<FeatureFlag>()

    /**
     * A map of feature flag properties to [Boolean] values, which should be initialised precisely once during node startup.
     */
    private var configuration: Map<KProperty0<Boolean>, Boolean>? = null

    /**
     * Called during node startup, populating the feature flag configuration map from the node configuration.
     *
     * This should only be called once, and must never be called by tests, which must use `withSet` to set/override feature-flag values.
     *
     * Note that this means that it is not possible to control the feature-flag configuration of in-process nodes during testing, as each
     * node's initialisation will override the configuration globally for the whole JVM process.
     *
     * @param values The "featureFlag" configuration values to apply.
     */
    fun configure(vararg values: Pair<KProperty0<Boolean>, Boolean>) {
        val newConfiguration = mapOf(*values)
        if (newConfiguration != configuration) { logger.warn("Feature flags configured with $newConfiguration, overriding existing $configuration") }
        configuration = newConfiguration
    }

    /**
     * We cannot simply use a [ThreadLocal], because that class is not known to the determiniser. Instead we use an adaptor, [TestValueHolder],
     * which can wrap either a [ThreadLocal] or a plain value.
     */
    private interface TestValueHolder {
        operator fun getValue(featureFlagValue: FeatureFlagValue, property: KProperty<*>): Boolean?
        operator fun setValue(featureFlagValue: FeatureFlagValue, property: KProperty<*>, b: Boolean?)
    }

    /**
     * Attempt to create a [TestValueHolder] wrapping a [ThreadLocal], and if that is not successful create a wrapper for a plain value instead.
     */
    private fun threadLocalIfPossible(): TestValueHolder =
            try {
                getThreadLocalTestValueHolder()
            } catch (e: UnsupportedOperationException) {
                object : TestValueHolder {
                    private var value: Boolean? = null
                    override operator fun getValue(featureFlagValue: FeatureFlagValue, property: KProperty<*>): Boolean? = value
                    override operator fun setValue(featureFlagValue: FeatureFlagValue, property: KProperty<*>, b: Boolean?) {
                        value = b
                    }
                }
            }

    @StubOutForDJVM
    private fun getThreadLocalTestValueHolder(): TestValueHolder {
        return object : TestValueHolder {
            private var threadLocal = ThreadLocal<Boolean>()
            override operator fun getValue(featureFlagValue: FeatureFlagValue, property: KProperty<*>): Boolean? = threadLocal.get()
            override operator fun setValue(featureFlagValue: FeatureFlagValue, property: KProperty<*>, b: Boolean?) {
                threadLocal.set(b)
            }
        }
    }
}