package net.corda.serialization.internal.model

import java.lang.reflect.Type

/**
 * Constructs [Type]s using [RemoteTypeInformation].
 */
interface RemoteTypeCarpenter {
    fun carpent(typeInformation: RemoteTypeInformation): Type
}