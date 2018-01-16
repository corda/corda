package net.corda.lazyhub

import net.corda.core.serialization.CordaSerializable
import kotlin.reflect.KClass
import kotlin.reflect.KFunction

/** Supertype of all exceptions thrown directly by [LazyHub]. */
@CordaSerializable
abstract class LazyHubException(message: String) : RuntimeException(message)

/** The type can't be instantiated because it is abstract, i.e. it's an interface or abstract class. */
class AbstractTypeException(message: String) : LazyHubException(message)

/**
 * The class can't be instantiated because it has no public constructor.
 * This is so that you can easily hide a constructor from LazyHub by making it non-public.
 */
class NoPublicConstructorsException(message: String) : LazyHubException(message)

/**
 * Nullable factory return types are not supported, as LazyHub has no concept of a provider that MAY supply an object.
 * If you want an optional result, use logic to decide whether to add the factory to the lazyHub.
 */
class NullableReturnTypeException(message: String) : LazyHubException(message)

/** The parameter can't be satisfied and doesn't have a default and isn't nullable. */
abstract class UnsatisfiableParamException(message: String) : LazyHubException(message)

/** No provider has been registered for the wanted type. */
class NoSuchProviderException(message: String) : UnsatisfiableParamException(message)

/**
 * No provider has been registered for the component type of the wanted array.
 * Note that LazyHub does not create empty arrays, make the array param type nullable to accept no elements.
 * This allows you to express zero-or-more (nullable) or one-or-more via the parameter type.
 */
class UnsatisfiableArrayException(message: String) : UnsatisfiableParamException(message)

/** More than one provider has been registered for the type but at most one object is wanted. */
class TooManyProvidersException(message: String) : UnsatisfiableParamException(message)

/**
 * More than one public constructor is satisfiable and there is no clear winner.
 * The winner is the constructor with the most params for which LazyHub actually supplies an arg.
 */
class NoUniqueGreediestSatisfiableConstructorException(message: String) : LazyHubException(message)

/** The object being created depends on itself, i.e. it's already being instantiated/factoried. */
class CircularDependencyException(message: String) : LazyHubException(message)

/** Depend on this as a param (and add the [MutableLazyHub], which is a [LazyHubFactory], to itself) if you want to make child containers. */
interface LazyHubFactory {
    fun child(): MutableLazyHub
}

/**
 * Read-only interface to the lazyHub.
 * Where possible, always obtain your object via a constructor/method param instead of directly from the [LazyHub].
 * This results in the greatest automatic benefits to the codebase e.g. separation of concerns and ease of testing.
 * A notable exception to this rule is `getAll(Unit::class)` to (idempotently) run all side-effects.
 */
interface LazyHub : LazyHubFactory {
    operator fun <T : Any> get(clazz: KClass<T>) = get(clazz.java)
    operator fun <T> get(clazz: Class<T>) = getOrNull(clazz) ?: throw NoSuchProviderException(clazz.toString())
    fun <T : Any> getAll(clazz: KClass<T>) = getAll(clazz.java)
    fun <T> getAll(clazz: Class<T>): List<T>
    fun <T : Any> getOrNull(clazz: KClass<T>) = getOrNull(clazz.java)
    fun <T> getOrNull(clazz: Class<T>): T?
}

/** Fully-featured interface to the lazyHub. */
interface MutableLazyHub : LazyHub {
    /** Register the given object against its class and all supertypes. */
    fun obj(obj: Any)

    /** Like plain old [MutableLazyHub.obj] but removes all [service] providers first. */
    fun <T : Any> obj(service: KClass<T>, obj: T)

    /**
     * Register the given class as a provider for itself and all supertypes.
     * The class is instantiated at most once, using the greediest public constructor satisfiable at the time.
     */
    fun impl(impl: KClass<*>)

    /**
     * Same as [MutableLazyHub.impl] if you don't have a static reference to the class.
     * Note that Kotlin features such as nullable params and default args will not be available.
     */
    fun impl(impl: Class<*>)

    /** Like plain old [MutableLazyHub.impl] but removes all [service] providers first. */
    fun <S : Any, T : S> impl(service: KClass<S>, impl: KClass<T>)

    /** Like the [KClass] variant if you don't have a static reference fo the class. */
    fun <S : Any, T : S> impl(service: KClass<S>, impl: Class<T>)

    /**
     * Register the given function as a provider for its **declared** return type and all supertypes.
     * The function is invoked at most once. Unlike constructors, the function may have any visibility.
     * By convention the function should have side-effects iff its return type is [Unit].
     */
    fun factory(factory: KFunction<*>)

    /** Register a factory that provides the given type from the given hub. */
    fun factory(lh: LazyHub, type: KClass<*>)

    /** Like plain old [MutableLazyHub.factory] but removes all [service] providers first. */
    fun <S : Any, T : S> factory(service: KClass<S>, factory: KFunction<T>)
}
