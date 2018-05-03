package net.corda.nodeapi.internal.serialization

import net.corda.core.serialization.SerializationContext
import net.corda.core.serialization.SerializationFactory
import java.util.*

internal fun checkUseCase(allowedUseCases: EnumSet<SerializationContext.UseCase>) {
    val currentContext: SerializationContext = SerializationFactory.currentFactory?.currentContext
            ?: throw IllegalStateException("Current context is not set")
    if (!allowedUseCases.contains(currentContext.useCase)) {
        throw IllegalStateException("UseCase '${currentContext.useCase}' is not within '$allowedUseCases'")
    }
}