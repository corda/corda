package net.corda.serialization.internal.model

/**
 * Represents the reflection of some [RemoteTypeInformation] by some [LocalTypeInformation], which we use to make
 * decisions about evolution.
 */
data class ReflectedTypeInformation(
        val remoteTypeInformation: RemoteTypeInformation,
        val localTypeInformation: LocalTypeInformation)

/**
 * Given [RemoteTypeInformation], a [RemoteTypeReflector] will find (or, if necessary, create) a local type reflecting
 * the remote type.
 */
 interface RemoteTypeReflector {
     fun reflect(remoteInformation: Map<TypeDescriptor, RemoteTypeInformation>): Map<TypeDescriptor, ReflectedTypeInformation>
 }

/**
 * A [TypeLoadingRemoteTypeReflector] uses a [TypeLoader] to load local types reflecting remote types, and a
 * [LocalTypeModel] to obtain [LocalTypeInformation] for these local types.
 *
 * If the local and remote information doesn't match, we can compare the two sets of type information and decide
 * whether evolution is necessary.
 *
 * @param typeLoader The [TypeLoader] to use to load local types reflecting remote types.
 * @param localTypeModel The [LocalTypeModel] to use to obtain [LocalTypeInformation] for the local [Type]s so obtained.
 */
class TypeLoadingRemoteTypeReflector(
        private val typeLoader: TypeLoader,
        private val localTypeModel: LocalTypeModel): RemoteTypeReflector {

    override fun reflect(remoteInformation: Map<TypeDescriptor, RemoteTypeInformation>):
            Map<TypeDescriptor, ReflectedTypeInformation> {
        val localInformationByIdentifier = typeLoader.load(remoteInformation.values).mapValues { (_, type) ->
            localTypeModel.inspect(type)
        }

        return remoteInformation.mapValues { (_, remoteInformation) ->
            ReflectedTypeInformation(remoteInformation, localInformationByIdentifier[remoteInformation.typeIdentifier]!!)
        }
    }
}