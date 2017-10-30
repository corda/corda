package net.corda.observerdemo.flow

class RegistryException(val error: RegistryObserverFlow.Error) : Exception("Error response from trade finance registry - ${error}")
